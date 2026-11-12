package dev.orbit.client.pvp;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.orbit.client.OrbitClient;
import dev.orbit.client.module.TierTagsModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/**
 * Looks up players' PvP tiers on MCTiers, PvPTiers and SubTiers and adds them after their name, e.g.
 * "TrustQ  HT4 Sword · LT3 Bed". Lookups are by name, run in the background (a few per second at most) and are
 * cached for 15 minutes; names nobody has looked up yet simply show no tag until the answer arrives.
 */
public final class TierTags {
    private TierTags() {}

    /** one ranking: gamemode, tier 1..5, high (HT) or low (LT), retired */
    public record Rank(String mode, int tier, boolean high, boolean retired) {}
    private record Entry(List<Rank> ranks, long fetched) {}

    private enum Source {
        MCTIERS("https://mctiers.io/api/search_profile/", 'M', 0x4DD88A),
        PVPTIERS("https://pvptiers.com/api/search_profile/", 'P', 0xB18CFF),
        SUBTIERS("https://subtiers.net/api/v2/profile/by-name/", 'S', 0xFFA24D);
        final String url; final char letter; final int color;
        Source(String url, char letter, int color) { this.url = url; this.letter = letter; this.color = color; }
    }

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NORMAL).build();
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "Orbit-TierTags"); t.setDaemon(true); return t; });
    private static final Map<String, Entry> CACHE = new ConcurrentHashMap<>();
    private static final Set<String> PENDING = ConcurrentHashMap.newKeySet();
    private static final long TTL = 15 * 60_000L, RETRY = 2 * 60_000L;

    private static TierTagsModule module() {
        return OrbitClient.modules() != null && OrbitClient.modules().get("tiertags") instanceof TierTagsModule m && m.isEnabled() ? m : null;
    }

    /** name tag above a player's head */
    public static Text nameTag(UUID id, String name, Text text) {
        var m = module();
        if (m == null || !m.nametags.on() || text == null) return text;
        return decorate(m, id, name, text);
    }
    /** a row in the Tab list */
    public static Text tabList(UUID id, String name, Text text) {
        var m = module();
        if (m == null || !m.tablist.on() || text == null) return text;
        return decorate(m, id, name, text);
    }

    private static Text decorate(TierTagsModule m, UUID id, String name, Text text) {
        if (name == null || name.isEmpty()) return text;
        var mc = MinecraftClient.getInstance();
        if (!m.self.on() && mc.player != null && mc.player.getUuid().equals(id)) return text;
        List<Source> sources = new ArrayList<>();
        if (m.mctiers.on()) sources.add(Source.MCTIERS);
        if (m.pvptiers.on()) sources.add(Source.PVPTIERS);
        if (m.subtiers.on()) sources.add(Source.SUBTIERS);
        if (sources.isEmpty()) return text;
        MutableText out = null;
        for (Source s : sources) {
            Rank r = pick(s, ranks(s, name), m);
            if (r == null) continue;
            MutableText part = Text.empty();
            if (sources.size() > 1) part.append(Text.literal(String.valueOf(s.letter)).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(s.color)).withBold(true))).append(" ");
            String tag = (r.retired() ? "R" : "") + (r.high() ? "HT" : "LT") + r.tier();
            part.append(Text.literal(tag).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(color(r))).withBold(r.tier() <= 2 && !r.retired())));
            if (m.showMode.on()) part.append(Text.literal(" " + label(r.mode())).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xAAB4C0))));
            if (out == null) out = Text.empty(); else out.append(Text.literal(" · ").setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x5A6470))));
            out.append(part);
        }
        if (out == null) return text;
        return Text.empty().append(text).append(Text.literal("  ")).append(out);
    }

    /** the ranking to show for one source: a chosen gamemode, or the best tier overall */
    private static Rank pick(Source s, List<Rank> ranks, TierTagsModule m) {
        if (ranks == null || ranks.isEmpty()) return null;
        Rank best = null;
        String want = m.mode.get();
        for (Rank r : ranks) {
            if (r.retired() && !m.retired.on()) continue;
            if (s != Source.SUBTIERS && !want.equals("Best") && !label(r.mode()).equalsIgnoreCase(want)) continue;
            if (best == null || score(r) > score(best)) best = r;
        }
        return best;
    }
    /** current tiers beat retired ones; then the higher tier, then HT over LT */
    private static int score(Rank r) { return (6 - r.tier()) * 10 + (r.high() ? 5 : 0) - (r.retired() ? 100 : 0); }

    private static int color(Rank r) {
        int c = switch (r.tier()) { case 1 -> 0xFFC93D; case 2 -> 0xD6DEE8; case 3 -> 0xE79A5A; case 4 -> 0x6FD8A0; default -> 0x8FA6BC; };
        if (!r.high()) c = blend(c, 0x9AA4AE, 0.3f);
        if (r.retired()) c = blend(c, 0x6A7480, 0.5f);
        return c;
    }
    private static int blend(int a, int b, float k) {
        int r = (int) (((a >> 16) & 255) * (1 - k) + ((b >> 16) & 255) * k), g = (int) (((a >> 8) & 255) * (1 - k) + ((b >> 8) & 255) * k), bl = (int) ((a & 255) * (1 - k) + (b & 255) * k);
        return (r << 16) | (g << 8) | bl;
    }
    /** pretty gamemode names (MCTiers' "vanilla" is Crystal) */
    public static String label(String mode) {
        return switch (mode.toLowerCase(Locale.ROOT)) {
            case "vanilla", "crystal" -> "Crystal"; case "sword" -> "Sword"; case "axe" -> "Axe"; case "uhc" -> "UHC"; case "pot" -> "Pot";
            case "neth_pot", "nethop", "neth_op" -> "NethOP"; case "smp" -> "SMP"; case "mace" -> "Mace";
            default -> { String s = mode.replace('_', ' '); yield s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }
        };
    }

    // ------------------------------------------------------------------ lookups
    private static List<Rank> ranks(Source s, String name) {
        String key = s.name() + ":" + name.toLowerCase(Locale.ROOT);
        Entry e = CACHE.get(key);
        long now = System.currentTimeMillis();
        if ((e == null || now - e.fetched() > (e.ranks() == null ? RETRY : TTL)) && PENDING.size() < 40 && PENDING.add(key)) {
            IO.execute(() -> {
                try { CACHE.put(key, new Entry(fetch(s, name), System.currentTimeMillis())); }
                catch (Exception ex) { CACHE.put(key, new Entry(null, System.currentTimeMillis())); }
                finally { PENDING.remove(key); try { Thread.sleep(120); } catch (InterruptedException ignored) { } }
            });
        }
        return e == null ? null : e.ranks();
    }

    private static List<Rank> fetch(Source s, String name) throws Exception {
        HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create(s.url + URLEncoder.encode(name, StandardCharsets.UTF_8)))
                .timeout(Duration.ofSeconds(10)).header("User-Agent", "Orbit-Client").GET().build(), HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() == 404) return List.of();       // not ranked there
        if (r.statusCode() != 200) throw new IllegalStateException("HTTP " + r.statusCode());
        JsonObject o = JsonParser.parseString(r.body()).getAsJsonObject();
        if (!o.has("rankings") || !o.get("rankings").isJsonObject()) return List.of();
        List<Rank> out = new ArrayList<>();
        for (var en : o.getAsJsonObject("rankings").entrySet()) {
            if (!en.getValue().isJsonObject()) continue;
            JsonObject v = en.getValue().getAsJsonObject();
            if (!v.has("tier") || v.get("tier").isJsonNull()) continue;
            int tier = v.get("tier").getAsInt();
            boolean high = v.has("pos") && !v.get("pos").isJsonNull() && v.get("pos").getAsInt() == 0;
            boolean retired = v.has("retired") && !v.get("retired").isJsonNull() && v.get("retired").getAsBoolean();
            if (tier >= 1 && tier <= 5) out.add(new Rank(en.getKey(), tier, high, retired));
        }
        return out;
    }

    /** for tests: what we know about a name right now */
    public static String debug(String name) {
        StringBuilder b = new StringBuilder();
        for (Source s : Source.values()) b.append(s.name()).append('=').append(CACHE.get(s.name() + ":" + name.toLowerCase(Locale.ROOT))).append(' ');
        return b.toString();
    }
}
