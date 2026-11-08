package dev.orbit.client.spotify;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.orbit.client.OrbitClient;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Background service: polls Spotify for the current track (token from the Orbit launcher,
 * file path in -Dorbit.spotify) and fetches time-synced lyrics from LRCLIB.
 * All network work happens on a daemon thread; the render thread only reads the volatile snapshot.
 */
public final class SpotifyLyrics {
    public record Line(long timeMs, String text) {}
    /** Immutable view for the HUD. */
    public record Snapshot(String status, String title, String artist, long durationMs, long progressMs, long sampledAt, boolean playing, List<Line> lines) {
        public long position() { return playing ? progressMs + (System.currentTimeMillis() - sampledAt) : progressMs; }
        public int index() {
            long p = position() + leadMs; // show a line slightly early so it never trails the singer
            int idx = -1;
            for (int i = 0; i < lines.size(); i++) { if (lines.get(i).timeMs() <= p) idx = i; else break; }
            return idx;
        }
    }

    /** How many ms early lyric lines switch (Spotify Lyrics → Timing). */
    public static volatile long leadMs = 250;

    private static final Snapshot EMPTY = new Snapshot("idle", "", "", 0, 0, 0, false, List.of());
    private static volatile Snapshot snapshot = EMPTY;
    private static volatile boolean wanted;
    private static Thread worker;
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).followRedirects(HttpClient.Redirect.NORMAL).build();
    private static final Pattern LRC = Pattern.compile("\\[(\\d+):(\\d+)(?:[.:](\\d+))?]\\s*(.*)");

    private static volatile String album = "";
    private static volatile net.minecraft.util.Identifier art;
    private static volatile int artColor = 0xFF1B1636;
    public static String album() { return album; }
    /** Album cover texture (null until loaded). */
    public static net.minecraft.util.Identifier art() { return art; }
    /** Average colour of the album cover, for tinting the widget. */
    public static int artColor() { return artColor; }

    private static volatile String accessToken;
    private static volatile long accessExpires;
    private static String currentTrackId;

    private SpotifyLyrics() {}

    public static Snapshot get() { return snapshot; }

    /** Called every tick by the lyrics module; starts/stops the worker lazily. */
    public static synchronized void setWanted(boolean on) {
        wanted = on;
        if (on && (worker == null || !worker.isAlive())) {
            worker = new Thread(SpotifyLyrics::loop, "Orbit-Spotify");
            worker.setDaemon(true);
            worker.start();
        }
    }

    private static final Object WAKE = new Object();

    private static volatile boolean fake;
    /** Dev/test only: pretend a song is playing (stops real polling from replacing it). */
    public static void fakeForTest(String title, String artist, String albumName, long durationMs, long progressMs, List<Line> lines) {
        fake = true;
        album = albumName;
        snapshot = new Snapshot("ok", title, artist, durationMs, progressMs, System.currentTimeMillis(), true, lines);
    }

    private static void loop() {
        while (wanted) {
            long wait = 1500;
            try { wait = fake ? 5000 : poll(); }
            catch (Exception e) { snapshot = new Snapshot("error", "", "", 0, 0, 0, false, List.of()); OrbitClient.LOG.debug("Spotify poll failed", e); wait = 8000; }
            try { synchronized (WAKE) { WAKE.wait(wait); } } catch (InterruptedException ignored) { return; }
        }
    }
    private static void pollSoon() { synchronized (WAKE) { WAKE.notifyAll(); } }

    // ----------------------------------------------------------------------------------- playback controls
    private static volatile String controlMsg;
    private static volatile long controlMsgAt;
    /** A short message after a failed control (shown on the card for a few seconds), else null. */
    public static String controlMessage() { return controlMsg != null && System.currentTimeMillis() - controlMsgAt < 5000 ? controlMsg : null; }
    private static void message(String m) { controlMsg = m; controlMsgAt = System.currentTimeMillis(); }

    /** "play", "pause", "next" or "previous" — runs off the render thread. Needs Spotify Premium. */
    public static void control(String action) {
        Snapshot s = snapshot;
        if (action.equals("pause") && s.playing()) snapshot = new Snapshot(s.status(), s.title(), s.artist(), s.durationMs(), s.position(), System.currentTimeMillis(), false, s.lines());
        if (action.equals("play") && !s.playing() && !s.title().isEmpty()) snapshot = new Snapshot(s.status(), s.title(), s.artist(), s.durationMs(), s.progressMs(), System.currentTimeMillis(), true, s.lines());
        Thread t = new Thread(() -> {
            try {
                Path file = tokenFile();
                if (file == null || !Files.exists(file)) { message("Connect Spotify in the Orbit launcher"); return; }
                synchronized (SpotifyLyrics.class) {
                    if (accessToken == null || System.currentTimeMillis() > accessExpires) refresh(file, JsonParser.parseString(Files.readString(file)).getAsJsonObject());
                }
                boolean put = action.equals("play") || action.equals("pause");
                HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create("https://api.spotify.com/v1/me/player/" + action))
                        .header("Authorization", "Bearer " + accessToken).timeout(Duration.ofSeconds(8));
                rb = put ? rb.PUT(HttpRequest.BodyPublishers.noBody()) : rb.POST(HttpRequest.BodyPublishers.noBody());
                HttpResponse<String> res = HTTP.send(rb.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                int code = res.statusCode();
                if (code >= 200 && code < 300) { controlMsg = null; }
                else if (code == 403 && res.body().contains("PREMIUM")) message("Spotify Premium is needed for controls");
                else if (code == 403 || code == 401) message("Reconnect Spotify in the launcher to allow controls");
                else if (code == 404) message("Open Spotify on a device first");
                else message("Spotify said no (" + code + ")");
            } catch (Exception e) {
                message("Couldn't reach Spotify");
                OrbitClient.LOG.debug("Spotify control failed", e);
            }
            try { Thread.sleep(250); } catch (InterruptedException ignored) {}
            pollSoon();
        }, "Orbit-Spotify-Control");
        t.setDaemon(true);
        t.start();
    }

    // ----------------------------------------------------------------------------------- spotify
    private static Path tokenFile() {
        String p = System.getProperty("orbit.spotify");
        return p == null ? null : Path.of(p);
    }

    private static long poll() throws Exception {
        Path file = tokenFile();
        if (file == null || !Files.exists(file)) { snapshot = new Snapshot("disconnected", "", "", 0, 0, 0, false, List.of()); return 10000; }
        JsonObject cfg = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        if (!cfg.has("refreshToken")) { snapshot = new Snapshot("disconnected", "", "", 0, 0, 0, false, List.of()); return 10000; }
        synchronized (SpotifyLyrics.class) {
            if (accessToken == null || System.currentTimeMillis() > accessExpires) refresh(file, cfg);
        }

        long t0 = System.currentTimeMillis();
        HttpResponse<String> res = HTTP.send(HttpRequest.newBuilder(URI.create("https://api.spotify.com/v1/me/player/currently-playing"))
                .header("Authorization", "Bearer " + accessToken).timeout(Duration.ofSeconds(8)).GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() == 401) { accessToken = null; return 500; }
        if (res.statusCode() == 429) return 15000;
        if (res.statusCode() == 204 || res.body().isBlank()) { snapshot = new Snapshot("paused", "", "", 0, 0, 0, false, List.of()); return 3000; }
        JsonObject o = JsonParser.parseString(res.body()).getAsJsonObject();
        if (!o.has("item") || o.get("item").isJsonNull()) { snapshot = new Snapshot("paused", "", "", 0, 0, 0, false, List.of()); return 3000; }
        JsonObject item = o.getAsJsonObject("item");
        String id = str(item, "id"), title = str(item, "name");
        List<String> artists = new ArrayList<>();
        if (item.has("artists")) for (JsonElement a : item.getAsJsonArray("artists")) artists.add(str(a.getAsJsonObject(), "name"));
        String artist = String.join(", ", artists);
        String album = item.has("album") ? str(item.getAsJsonObject("album"), "name") : "";
        long duration = item.has("duration_ms") ? item.get("duration_ms").getAsLong() : 0;
        long progress = o.has("progress_ms") && !o.get("progress_ms").isJsonNull() ? o.get("progress_ms").getAsLong() : 0;
        boolean playing = o.has("is_playing") && o.get("is_playing").getAsBoolean();
        long sampled = (t0 + System.currentTimeMillis()) / 2; // the position was measured around the middle of the request

        List<Line> lines = snapshot.lines();
        if (id != null && !id.equals(currentTrackId)) {
            currentTrackId = id;
            SpotifyLyrics.album = album;
            loadArt(item);
            lines = List.of();
            snapshot = new Snapshot("loading", title, artist, duration, progress, sampled, playing, lines);
            lines = fetchLyrics(title, artists.isEmpty() ? "" : artists.get(0), album, duration);
        }
        // keep the time the position was measured (not "now"): the lyrics download can take seconds
        snapshot = new Snapshot(lines.isEmpty() ? "nolyrics" : "ok", title, artist, duration, progress, sampled, playing, lines);
        return playing ? 1000 : 3000;
    }

    private static void refresh(Path file, JsonObject cfg) throws Exception {
        String body = "grant_type=refresh_token&refresh_token=" + enc(cfg.get("refreshToken").getAsString()) + "&client_id=" + enc(cfg.get("clientId").getAsString());
        HttpResponse<String> res = HTTP.send(HttpRequest.newBuilder(URI.create("https://accounts.spotify.com/api/token"))
                .header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() != 200) throw new IllegalStateException("Spotify token refresh failed: " + res.statusCode());
        JsonObject t = JsonParser.parseString(res.body()).getAsJsonObject();
        accessToken = t.get("access_token").getAsString();
        accessExpires = System.currentTimeMillis() + (t.get("expires_in").getAsLong() - 60) * 1000;
        if (t.has("refresh_token")) { // Spotify rotates refresh tokens for PKCE apps — keep the launcher file current
            cfg.addProperty("refreshToken", t.get("refresh_token").getAsString());
            Files.writeString(file, cfg.toString());
        }
    }

    // ----------------------------------------------------------------------------------- album art
    private static final net.minecraft.util.Identifier ART_ID = dev.orbit.client.compat.Compat.id("spotify/art");
    /** Downloads the cover (JPEG), converts it to a 64x64 PNG Minecraft can load, and registers it. */
    private static void loadArt(JsonObject item) {
        try {
            if (!item.has("album")) return;
            JsonArray images = item.getAsJsonObject("album").getAsJsonArray("images");
            if (images == null || images.isEmpty()) return;
            String url = images.get(images.size() > 1 ? images.size() - 2 : 0).getAsJsonObject().get("url").getAsString();
            HttpResponse<byte[]> res = HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            java.awt.image.BufferedImage src = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(res.body()));
            if (src == null) return;
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(64, 64, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = img.createGraphics();
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(src, 0, 0, 64, 64, null);
            g.dispose();
            long r = 0, gr = 0, b = 0;
            for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) { int p = img.getRGB(x, y); r += p >> 16 & 255; gr += p >> 8 & 255; b += p & 255; }
            int n = 64 * 64;
            artColor = 0xFF000000 | (int) (r / n) << 16 | (int) (gr / n) << 8 | (int) (b / n);
            java.io.ByteArrayOutputStream png = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(img, "png", png);
            byte[] bytes = png.toByteArray();
            net.minecraft.client.MinecraftClient.getInstance().execute(() -> {
                try {
                    net.minecraft.client.texture.NativeImage ni = net.minecraft.client.texture.NativeImage.read(bytes);
                    net.minecraft.client.MinecraftClient.getInstance().getTextureManager().registerTexture(ART_ID, dev.orbit.client.compat.Compat.texture("orbit-spotify-art", ni, false));
                    art = ART_ID;
                } catch (Exception e) { OrbitClient.LOG.debug("Album art upload failed", e); }
            });
        } catch (Exception e) {
            OrbitClient.LOG.debug("Album art download failed", e);
        }
    }

    // ----------------------------------------------------------------------------------- lrclib
    private static List<Line> fetchLyrics(String title, String artist, String album, long durationMs) {
        try {
            String q = "track_name=" + enc(title) + "&artist_name=" + enc(artist) + "&album_name=" + enc(album) + "&duration=" + Math.round(durationMs / 1000.0);
            HttpResponse<String> res = lrclib("/api/get?" + q);
            if (res.statusCode() == 200) {
                List<Line> l = parse(JsonParser.parseString(res.body()).getAsJsonObject());
                if (!l.isEmpty()) return l;
            }
            res = lrclib("/api/search?track_name=" + enc(title) + "&artist_name=" + enc(artist));
            if (res.statusCode() == 200) {
                JsonArray arr = JsonParser.parseString(res.body()).getAsJsonArray();
                for (JsonElement e : arr) { List<Line> l = parse(e.getAsJsonObject()); if (!l.isEmpty()) return l; }
            }
        } catch (Exception e) {
            OrbitClient.LOG.debug("Lyrics lookup failed", e);
        }
        return List.of();
    }

    private static HttpResponse<String> lrclib(String path) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create("https://lrclib.net" + path))
                .header("User-Agent", "Orbit Client 1.0 (Minecraft mod)").timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static List<Line> parse(JsonObject o) {
        String synced = o.has("syncedLyrics") && !o.get("syncedLyrics").isJsonNull() ? o.get("syncedLyrics").getAsString() : null;
        List<Line> out = new ArrayList<>();
        if (synced == null) return out;
        for (String raw : synced.split("\n")) {
            Matcher m = LRC.matcher(raw.trim());
            if (!m.matches()) continue;
            long ms = Long.parseLong(m.group(1)) * 60000 + Long.parseLong(m.group(2)) * 1000;
            if (m.group(3) != null) { String f = (m.group(3) + "00").substring(0, 3); ms += Long.parseLong(f); }
            out.add(new Line(ms, m.group(4).trim()));
        }
        return out;
    }

    private static String str(JsonObject o, String k) { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : ""; }
    private static String enc(String s) { return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8); }
}
