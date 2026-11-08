package dev.orbit.client.module;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.orbit.client.OrbitClient;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Config profiles: saved copies of every mod's settings (config/orbit-configs/NAME.json), loadable any time,
 * and shareable as one line of text ("orbit:..." on the clipboard).
 */
public final class Configs {
    private Configs() {}

    private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("orbit-configs");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public record Entry(String name, long modified, int enabled) {}

    public static String clean(String name) { return name.trim().replaceAll("[^A-Za-z0-9 _\\-]", "").replaceAll("\\s+", " "); }

    public static List<Entry> list() {
        List<Entry> l = new ArrayList<>();
        try {
            if (!Files.isDirectory(DIR)) return l;
            try (var s = Files.list(DIR)) {
                for (Path p : s.filter(f -> f.toString().endsWith(".json")).toList()) {
                    int on = 0;
                    try {
                        JsonObject o = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
                        for (var e : o.entrySet()) if (e.getValue().isJsonObject() && e.getValue().getAsJsonObject().has("enabled") && e.getValue().getAsJsonObject().get("enabled").getAsBoolean()) on++;
                    } catch (Exception ignored) { }
                    String n = p.getFileName().toString();
                    l.add(new Entry(n.substring(0, n.length() - 5), Files.getLastModifiedTime(p).toMillis(), on));
                }
            }
        } catch (Exception e) { OrbitClient.LOG.warn("Could not list configs", e); }
        l.sort((a, b) -> Long.compare(b.modified(), a.modified()));
        return l;
    }

    public static boolean save(String name) {
        name = clean(name);
        if (name.isEmpty()) return false;
        try { Files.createDirectories(DIR); Files.writeString(DIR.resolve(name + ".json"), GSON.toJson(OrbitClient.modules().snapshot())); return true; }
        catch (Exception e) { OrbitClient.LOG.warn("Could not save config {}", name, e); return false; }
    }

    public static boolean load(String name) {
        try { OrbitClient.modules().apply(JsonParser.parseString(Files.readString(DIR.resolve(clean(name) + ".json"))).getAsJsonObject()); return true; }
        catch (Exception e) { OrbitClient.LOG.warn("Could not load config {}", name, e); return false; }
    }

    public static void delete(String name) {
        try { Files.deleteIfExists(DIR.resolve(clean(name) + ".json")); } catch (Exception ignored) { }
    }

    /** one line of text with the whole config (compressed) */
    public static String share(String name) {
        try {
            byte[] raw = Files.readString(DIR.resolve(clean(name) + ".json")).getBytes(StandardCharsets.UTF_8);
            Deflater d = new Deflater(Deflater.BEST_COMPRESSION);
            d.setInput(raw); d.finish();
            byte[] buf = new byte[raw.length + 64];
            int n = d.deflate(buf); d.end();
            return "orbit:" + clean(name).replace(' ', '_') + ":" + Base64.getUrlEncoder().withoutPadding().encodeToString(java.util.Arrays.copyOf(buf, n));
        } catch (Exception e) { return null; }
    }

    /** imports a shared line; returns the saved config's name or null */
    public static String importShared(String text) {
        try {
            text = text.trim();
            if (!text.startsWith("orbit:")) return null;
            String[] parts = text.split(":", 3);
            byte[] zipped = Base64.getUrlDecoder().decode(parts[2]);
            Inflater inf = new Inflater();
            inf.setInput(zipped);
            byte[] out = new byte[1 << 20];
            int n = inf.inflate(out); inf.end();
            String json = new String(out, 0, n, StandardCharsets.UTF_8);
            JsonParser.parseString(json).getAsJsonObject(); // must be valid
            String base = clean(parts[1].replace('_', ' '));
            if (base.isEmpty()) base = "Imported";
            String name = base;
            for (int i = 2; Files.exists(DIR.resolve(name + ".json")); i++) name = base + " " + i;
            Files.createDirectories(DIR);
            Files.writeString(DIR.resolve(name + ".json"), json);
            return name;
        } catch (Exception e) { OrbitClient.LOG.warn("Could not import config", e); return null; }
    }
}
