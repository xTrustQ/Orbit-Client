package dev.orbit.client.cosmetics;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.orbit.client.OrbitClient;
import dev.orbit.client.compat.Compat;
import dev.orbit.client.mixin.MinecraftClientAccessor;
import dev.orbit.client.module.NewModules;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Connection to the Orbit cosmetics API (orbit-server/api.js): publishes what you wear and fetches what other
 * Orbit players around you wear, so everyone using Orbit sees each other's hats, wings, auras and capes.
 * The server URL comes from the launcher (-Dorbit.api, or the sync server's address).
 */
public final class CosmeticsApi {
    private CosmeticsApi() {}

    public record Remote(String hat, String wings, String aura, String cape, long fetched) {}

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "Orbit-Cosmetics"); t.setDaemon(true); return t; });
    private static final Path TOKENS = FabricLoader.getInstance().getConfigDir().resolve("orbit-api.json");
    private static final Map<UUID, Remote> REMOTE = new ConcurrentHashMap<>();
    private static final Set<UUID> ASKED = ConcurrentHashMap.newKeySet();
    private static final Map<String, Identifier> CAPES = new ConcurrentHashMap<>();
    private static final Set<String> CAPE_LOADING = ConcurrentHashMap.newKeySet();
    private static String lastSig = "";
    private static long nextCheck, nextFetch;
    private static volatile boolean busy, offline;
    public static volatile String status = "";

    /** the API address, or null when no Orbit server is set */
    public static String base() {
        String a = System.getProperty("orbit.api");
        if (a == null || a.isBlank()) {
            String s = System.getProperty("orbit.sync");
            if (s == null || s.isBlank()) return null;
            a = s.replaceFirst("^wss://", "https://").replaceFirst("^ws://", "http://");
        }
        return a.replaceAll("/+$", "");
    }
    public static boolean enabled() { return base() != null; }

    public static Remote of(UUID id) {
        Remote r = REMOTE.get(id);
        return r == null || r.hat() == null ? null : r;
    }

    // ------------------------------------------------------------------ every client tick
    public static void tick() {
        if (!enabled()) return;
        var mc = MinecraftClient.getInstance();
        long now = System.currentTimeMillis();
        if (now > nextCheck && !busy && !offline) { // publish when what you wear changes
            nextCheck = now + 4000;
            String sig = signature();
            if (!sig.equals(lastSig)) { lastSig = sig; busy = true; IO.execute(CosmeticsApi::publish); }
        }
        if (now > nextFetch && mc.world != null) { // fetch players around you we don't know yet (or not for a while)
            nextFetch = now + 2000;
            List<String> ids = new ArrayList<>();
            for (PlayerEntity p : mc.world.getPlayers()) {
                if (p == mc.player) continue;
                Remote r = REMOTE.get(p.getUuid());
                if ((r == null && ASKED.add(p.getUuid())) || (r != null && now - r.fetched() > 300_000)) { ids.add(p.getUuid().toString()); REMOTE.putIfAbsent(p.getUuid(), new Remote(null, null, null, null, now)); }
                if (ids.size() >= 100) break;
            }
            if (!ids.isEmpty()) IO.execute(() -> fetch(ids));
        }
    }

    private static String signature() {
        var mods = OrbitClient.modules();
        String hat = "None", wings = "None", aura = "None";
        if (mods.get("cosmetics") instanceof NewModules.Cosmetics c && c.isEnabled()) { hat = c.hat.get(); wings = c.wings.get(); aura = c.aura.get(); }
        Path cape = CapeManager.currentFile();
        long m = 0;
        try { if (cape != null) m = Files.getLastModifiedTime(cape).toMillis(); } catch (Exception ignored) { }
        return hat + "|" + wings + "|" + aura + "|" + cape + "|" + m;
    }

    // ------------------------------------------------------------------ publishing
    private static void publish() {
        try {
            var mc = MinecraftClient.getInstance();
            UUID me = mc.getSession().getUuidOrNull();
            if (me == null) return;
            String token = token(me);
            if (token == null) token = signIn(me);
            if (token == null) return;
            String[] parts = lastSig.split("\\|", -1);
            JsonObject body = new JsonObject();
            body.addProperty("hat", parts[0]); body.addProperty("wings", parts[1]); body.addProperty("aura", parts[2]);
            Path cape = CapeManager.currentFile();
            if (cape != null && Files.exists(cape) && Files.size(cape) < 400_000) body.addProperty("cape", Base64.getEncoder().encodeToString(Files.readAllBytes(cape)));
            HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create(base() + "/api/cosmetics")).timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(body.toString())).build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() == 401) { saveToken(me, null); lastSig = ""; return; } // sign in again next time
            status = r.statusCode() == 200 ? "Your cosmetics are visible to other Orbit players" : "Cosmetics server: " + r.statusCode();
        } catch (Exception e) {
            status = "Cosmetics server unreachable";
            lastSig = ""; // try again later
            OrbitClient.LOG.debug("Cosmetics publish failed: {}", e.toString());
        } finally { busy = false; }
    }

    /** proves to the server that this is really your account, the same way joining a Minecraft server does */
    private static String signIn(UUID me) throws Exception {
        var mc = MinecraftClient.getInstance();
        String access = mc.getSession().getAccessToken();
        if (access == null || access.length() < 20) { offline = true; status = "Offline account — others can't see your cosmetics"; return null; }
        JsonObject start = new JsonObject();
        start.addProperty("uuid", me.toString());
        start.addProperty("name", mc.getSession().getUsername());
        HttpResponse<String> r = HTTP.send(post("/api/auth/start", start), HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() != 200) return null;
        String serverId = JsonParser.parseString(r.body()).getAsJsonObject().get("serverId").getAsString();
        ((MinecraftClientAccessor) mc).orbit$apiServices().sessionService().joinServer(me, access, serverId);
        JsonObject fin = new JsonObject();
        fin.addProperty("name", mc.getSession().getUsername());
        fin.addProperty("serverId", serverId);
        r = HTTP.send(post("/api/auth/finish", fin), HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() != 200) { status = "Cosmetics sign-in failed"; return null; }
        String token = JsonParser.parseString(r.body()).getAsJsonObject().get("token").getAsString();
        saveToken(me, token);
        return token;
    }
    private static HttpRequest post(String path, JsonObject body) {
        return HttpRequest.newBuilder(URI.create(base() + path)).timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
    }
    private static String token(UUID me) {
        try { if (!Files.exists(TOKENS)) return null; var o = JsonParser.parseString(Files.readString(TOKENS)).getAsJsonObject(); String k = base() + "|" + me; return o.has(k) ? o.get(k).getAsString() : null; }
        catch (Exception e) { return null; }
    }
    private static void saveToken(UUID me, String token) {
        try {
            JsonObject o = Files.exists(TOKENS) ? JsonParser.parseString(Files.readString(TOKENS)).getAsJsonObject() : new JsonObject();
            String k = base() + "|" + me;
            if (token == null) o.remove(k); else o.addProperty(k, token);
            Files.writeString(TOKENS, new Gson().toJson(o));
        } catch (Exception ignored) { }
    }

    // ------------------------------------------------------------------ fetching others
    private static void fetch(List<String> ids) {
        try {
            HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create(base() + "/api/cosmetics?uuids=" + String.join(",", ids))).timeout(Duration.ofSeconds(15)).GET().build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 200) return;
            JsonObject o = JsonParser.parseString(r.body()).getAsJsonObject();
            long now = System.currentTimeMillis();
            for (String id : ids) {
                UUID u = UUID.fromString(id);
                if (!o.has(id)) { REMOTE.put(u, new Remote(null, null, null, null, now)); continue; }
                JsonObject c = o.getAsJsonObject(id);
                String cape = c.has("cape") && !c.get("cape").isJsonNull() ? c.get("cape").getAsString() : null;
                REMOTE.put(u, new Remote(str(c, "hat"), str(c, "wings"), str(c, "aura"), cape, now));
                OrbitUsers.add(u); // they use Orbit: badge on their name tag
            }
        } catch (Exception e) { OrbitClient.LOG.debug("Cosmetics fetch failed: {}", e.toString()); }
    }
    private static String str(JsonObject o, String k) { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "None"; }

    /** the texture of someone's Orbit cape (downloaded once, then cached) */
    public static Identifier cape(String hash) {
        if (hash == null) return null;
        Identifier id = CAPES.get(hash);
        if (id != null || !CAPE_LOADING.add(hash) || !enabled()) return id;
        IO.execute(() -> {
            try {
                HttpResponse<byte[]> r = HTTP.send(HttpRequest.newBuilder(URI.create(base() + "/api/capes/" + hash + ".png")).timeout(Duration.ofSeconds(20)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
                if (r.statusCode() != 200) return;
                NativeImage img = NativeImage.read(new ByteArrayInputStream(r.body()));
                MinecraftClient.getInstance().execute(() -> {
                    Identifier tex = Compat.id("cape/remote/" + hash);
                    MinecraftClient.getInstance().getTextureManager().registerTexture(tex, Compat.texture("orbit-cape-" + hash, img, false));
                    CAPES.put(hash, tex);
                });
            } catch (Exception e) { OrbitClient.LOG.debug("Cape download failed: {}", e.toString()); }
        });
        return null;
    }
}
