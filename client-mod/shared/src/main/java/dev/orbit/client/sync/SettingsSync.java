package dev.orbit.client.sync;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.orbit.client.OrbitClient;
import dev.orbit.client.cosmetics.CosmeticsApi;
import dev.orbit.client.friends.FriendToasts;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Settings sync: your Orbit setup (every mod's settings, the HUD layout, cosmetics) is saved to your Minecraft
 * account on the Orbit server, and comes back by itself on a new PC. Changes upload ~20 s after you make them.
 * Rule: a PC that never synced (new install) takes the account's settings; otherwise the newest change wins.
 */
public final class SettingsSync {
    private SettingsSync() {}

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "Orbit-SettingsSync"); t.setDaemon(true); return t; });
    private static final Path STATE = FabricLoader.getInstance().getConfigDir().resolve("orbit-sync.json");

    private static String lastHash;   // settings as last uploaded/downloaded
    private static long lastUpdated;  // when that version was made
    private static boolean started, busy;
    private static String changedHash;
    private static long changedAt, nextCheck, retryAt;
    public static volatile String status = "";

    private static boolean enabled() { return CosmeticsApi.enabled() && OrbitClient.modules().ui().settingsSync.on(); }

    /** every client tick */
    public static void tick() {
        if (!enabled() || busy) return;
        long now = System.currentTimeMillis();
        if (now < retryAt) return;
        if (!started) { started = true; busy = true; loadState(); IO.execute(SettingsSync::startup); return; }
        if (now < nextCheck) return;
        nextCheck = now + 5000;
        String h = hash(snapshot());
        if (h.equals(lastHash)) { changedHash = null; return; }
        if (!h.equals(changedHash)) { changedHash = h; changedAt = now; return; }
        if (now - changedAt > 20_000) { busy = true; IO.execute(() -> upload(false)); } // settled for 20 s: save it
    }

    // ------------------------------------------------------------------ start of the game
    private static void startup() {
        try {
            String token = CosmeticsApi.bearer();
            if (token == null) { status = "Settings sync needs a Microsoft account"; return; }
            HttpResponse<String> r = HTTP.send(get(token), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() == 401) { CosmeticsApi.dropLogin(); started = false; retryAt = System.currentTimeMillis() + 30_000; return; }
            String local = hash(snapshot());
            if (r.statusCode() == 404) { upload(true); return; } // nothing on the account yet
            if (r.statusCode() != 200) { started = false; retryAt = System.currentTimeMillis() + 60_000; return; }
            JsonObject o = JsonParser.parseString(r.body()).getAsJsonObject();
            long cloudTime = o.get("updated").getAsLong();
            JsonObject data = o.getAsJsonObject("data");
            boolean newPc = lastHash == null && OrbitClient.modules().fresh;
            boolean cloudNewer = cloudTime > lastUpdated && (lastHash == null || local.equals(lastHash));
            if (newPc || cloudNewer) {
                MinecraftClient.getInstance().execute(() -> {
                    OrbitClient.modules().apply(data);
                    lastHash = hash(snapshot()); lastUpdated = cloudTime; saveState();
                    status = "Restored your settings from your account";
                    if (newPc) FriendToasts.show(null, "Welcome back!", "Your Orbit settings were restored from your account", true);
                });
            } else if (!local.equals(lastHash)) upload(true);
            else status = "Up to date";
        } catch (Exception e) {
            started = false; retryAt = System.currentTimeMillis() + 60_000; // server asleep / offline: try again later
            OrbitClient.LOG.debug("Settings sync startup failed: {}", e.toString());
        } finally { busy = false; }
    }

    // ------------------------------------------------------------------ upload / restore
    private static void upload(boolean inner) {
        try {
            String token = CosmeticsApi.bearer();
            if (token == null) return;
            JsonObject snap = snapshot();
            long now = System.currentTimeMillis();
            JsonObject body = new JsonObject();
            body.add("data", snap);
            body.addProperty("updated", now);
            HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create(CosmeticsApi.base() + "/api/settings")).timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8)).build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() == 401) { CosmeticsApi.dropLogin(); retryAt = System.currentTimeMillis() + 30_000; return; }
            if (r.statusCode() != 200) { retryAt = System.currentTimeMillis() + 60_000; return; }
            lastHash = hash(snap); lastUpdated = now; saveState();
            status = "Saved to your account";
        } catch (Exception e) {
            retryAt = System.currentTimeMillis() + 60_000;
            OrbitClient.LOG.debug("Settings upload failed: {}", e.toString());
        } finally { if (!inner) busy = false; }
    }

    /** "Upload now" button */
    public static void uploadNow() {
        if (!CosmeticsApi.enabled()) { FriendToasts.show(null, "Settings sync unavailable", "No Orbit server set", false); return; }
        if (busy) return;
        busy = true;
        IO.execute(() -> {
            upload(false);
            MinecraftClient.getInstance().execute(() -> FriendToasts.show(null, "Settings sync", status.isEmpty() ? "Couldn't reach the Orbit server" : status, true));
        });
    }

    /** "Restore from my account" button: replaces this PC's settings with the saved ones */
    public static void restoreNow() {
        if (!CosmeticsApi.enabled() || busy) return;
        busy = true;
        IO.execute(() -> {
            try {
                String token = CosmeticsApi.bearer();
                if (token == null) { toast("Needs a Microsoft account"); return; }
                HttpResponse<String> r = HTTP.send(get(token), HttpResponse.BodyHandlers.ofString());
                if (r.statusCode() == 404) { toast("Nothing saved on your account yet"); return; }
                if (r.statusCode() != 200) { toast("Couldn't reach the Orbit server"); return; }
                JsonObject o = JsonParser.parseString(r.body()).getAsJsonObject();
                long t = o.get("updated").getAsLong();
                JsonObject data = o.getAsJsonObject("data");
                MinecraftClient.getInstance().execute(() -> {
                    OrbitClient.modules().apply(data);
                    lastHash = hash(snapshot()); lastUpdated = t; saveState();
                    FriendToasts.show(null, "Settings restored", "Your Orbit settings came back from your account", true);
                });
            } catch (Exception e) { toast("Couldn't reach the Orbit server"); }
            finally { busy = false; }
        });
    }
    private static void toast(String s) { MinecraftClient.getInstance().execute(() -> FriendToasts.show(null, "Settings sync", s, false)); }

    // ------------------------------------------------------------------ helpers
    private static HttpRequest get(String token) {
        return HttpRequest.newBuilder(URI.create(CosmeticsApi.base() + "/api/settings")).timeout(Duration.ofSeconds(20)).header("Authorization", "Bearer " + token).GET().build();
    }
    private static JsonObject snapshot() {
        JsonObject s = OrbitClient.modules().snapshot();
        // per-PC things stay on this PC
        if (s.has("interface") && s.get("interface").isJsonObject()) s.getAsJsonObject("interface").remove("settingsSync");
        return s;
    }
    private static String hash(JsonObject o) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(o.toString().getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { return String.valueOf(o.toString().hashCode()); }
    }
    private static void loadState() {
        try {
            if (!Files.exists(STATE)) return;
            JsonObject o = JsonParser.parseString(Files.readString(STATE)).getAsJsonObject();
            lastHash = o.has("hash") ? o.get("hash").getAsString() : null;
            lastUpdated = o.has("updated") ? o.get("updated").getAsLong() : 0;
        } catch (Exception ignored) { }
    }
    private static void saveState() {
        try {
            JsonObject o = new JsonObject();
            if (lastHash != null) o.addProperty("hash", lastHash);
            o.addProperty("updated", lastUpdated);
            Files.writeString(STATE, o.toString());
        } catch (Exception ignored) { }
    }
}
