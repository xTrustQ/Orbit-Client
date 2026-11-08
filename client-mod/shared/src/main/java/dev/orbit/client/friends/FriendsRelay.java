package dev.orbit.client.friends;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.orbit.client.OrbitClient;
import dev.orbit.client.emote.EmoteSync;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Friends without hosting anything: online status and messages go through the free public relay ntfy.sh.
 * Used only when no Orbit sync server is set (that one wins). Each player has a "dm" topic (messages to
 * them) and an "on" topic (a heartbeat every minute). Messages are encrypted with a key made from both
 * players' UUIDs — enough to keep them out of plain sight on the relay, not a replacement for a private server.
 */
public final class FriendsRelay {
    private FriendsRelay() {}

    private static final String BASE = "https://ntfy.sh/";
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private static final Path STATE = FabricLoader.getInstance().getConfigDir().resolve("orbit-relay.json");
    private static final SecureRandom RNG = new SecureRandom();

    private static WebSocket dmSocket, onSocket;
    private static boolean dmConnecting, onConnecting;
    private static long nextDm, nextOn, nextBeat;
    private static String onTopics = "";
    private static String dmSince; // id of the last message we got (ntfy resumes after it)
    /** friend uuid -> last heartbeat (ms) ; negative = said goodbye */
    private static final Map<String, Long> SEEN = new ConcurrentHashMap<>();
    private static final Set<String> HANDLED = Collections.newSetFromMap(new LinkedHashMap<>() {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Boolean> e) { return size() > 500; }
    });

    static boolean enabled() { return !EmoteSync.configured() && !"off".equals(System.getProperty("orbit.relay")) && me() != null; }
    public static boolean connected() { return dmSocket != null; }
    public static boolean online(String uuid) { Long t = SEEN.get(uuid); return t != null && t > 0 && System.currentTimeMillis() - t < 150_000; }

    private static String me() { var id = MinecraftClient.getInstance().getSession().getUuidOrNull(); return id == null ? null : id.toString(); }
    private static String myName() { return MinecraftClient.getInstance().getSession().getUsername(); }

    static String topic(String kind, String uuid) { return "orbit-" + kind + "-" + hex(sha("orbit-friends-v1:" + kind + ":" + uuid.toLowerCase())).substring(0, 24); }

    // ------------------------------------------------------------------ every client tick
    public static void tick() {
        if (!enabled()) { closeAll(); return; }
        long now = System.currentTimeMillis();
        if (dmSocket == null && !dmConnecting && now > nextDm) connectDm();
        String want = presenceTopics();
        if (!want.equals(onTopics) && onSocket != null) { onSocket.abort(); onSocket = null; }
        if (onSocket == null && !onConnecting && now > nextOn && !want.isEmpty()) connectOn(want);
        if (now > nextBeat) { nextBeat = now + 60_000; post(topic("on", me()), "on"); }
    }

    /** say goodbye so friends see you go offline right away */
    public static void shutdown() {
        if (!enabled()) return;
        try { HTTP.send(req(topic("on", me()), "off"), HttpResponse.BodyHandlers.discarding()); } catch (Exception ignored) { }
        closeAll();
    }

    private static void closeAll() {
        if (dmSocket != null) { dmSocket.abort(); dmSocket = null; }
        if (onSocket != null) { onSocket.abort(); onSocket = null; }
        onTopics = "";
    }

    private static String presenceTopics() {
        List<String> t = new ArrayList<>();
        for (Friends.Friend f : Friends.list()) t.add(topic("on", f.uuid));
        Collections.sort(t);
        return String.join(",", t);
    }

    // ------------------------------------------------------------------ messages to me
    private static void connectDm() {
        dmConnecting = true;
        if (dmSince == null) dmSince = loadSince();
        // first start: what was sent in the last 12 h; later: everything after the last message we saw
        String since = !dmSince.isEmpty() ? dmSince : "12h";
        HTTP.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(8))
                .buildAsync(URI.create("wss://ntfy.sh/" + topic("dm", me()) + "/ws?since=" + since), new Listener(true))
                .whenComplete((ws, err) -> {
                    dmConnecting = false;
                    if (err != null) { nextDm = System.currentTimeMillis() + 20_000; OrbitClient.LOG.debug("Friends relay unavailable: {}", err.getMessage()); return; }
                    dmSocket = ws;
                    nextBeat = 0; // announce right away
                });
    }

    // ------------------------------------------------------------------ friends' heartbeats
    private static void connectOn(String topics) {
        onConnecting = true;
        HTTP.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(8))
                .buildAsync(URI.create("wss://ntfy.sh/" + topics + "/ws?since=3m"), new Listener(false))
                .whenComplete((ws, err) -> {
                    onConnecting = false;
                    if (err != null) { nextOn = System.currentTimeMillis() + 20_000; return; }
                    onSocket = ws;
                    onTopics = topics;
                });
    }

    public static boolean send(String to, String text) {
        if (!enabled() || dmSocket == null) return false;
        try {
            byte[] iv = new byte[12];
            RNG.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key(me(), to), new GCMParameterSpec(128, iv));
            JsonObject o = new JsonObject();
            o.addProperty("f", me());
            o.addProperty("n", myName());
            o.addProperty("iv", Base64.getEncoder().encodeToString(iv));
            o.addProperty("c", Base64.getEncoder().encodeToString(c.doFinal(text.getBytes(StandardCharsets.UTF_8))));
            post(topic("dm", to), o.toString());
            return true;
        } catch (Exception e) { OrbitClient.LOG.warn("Could not send message", e); return false; }
    }

    private static void post(String topic, String body) {
        HTTP.sendAsync(req(topic, body), HttpResponse.BodyHandlers.discarding())
                .exceptionally(t -> { OrbitClient.LOG.debug("Friends relay post failed: {}", t.getMessage()); return null; });
    }
    private static HttpRequest req(String topic, String body) {
        return HttpRequest.newBuilder(URI.create(BASE + topic)).timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
    }

    private static final class Listener implements WebSocket.Listener {
        private final boolean dm;
        private final StringBuilder buf = new StringBuilder();
        Listener(boolean dm) { this.dm = dm; }

        @Override public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            buf.append(data);
            if (last) { String s = buf.toString(); buf.setLength(0); try { handle(s); } catch (Exception ignored) { } }
            ws.request(1);
            return null;
        }
        @Override public CompletionStage<?> onClose(WebSocket ws, int code, String reason) { lost(); return null; }
        @Override public void onError(WebSocket ws, Throwable error) { lost(); }
        private void lost() {
            if (dm) { dmSocket = null; nextDm = System.currentTimeMillis() + 10_000; }
            else { onSocket = null; nextOn = System.currentTimeMillis() + 10_000; }
        }

        private void handle(String raw) throws Exception {
            JsonObject ev = JsonParser.parseString(raw).getAsJsonObject();
            if (!"message".equals(ev.get("event").getAsString())) return;
            String id = ev.get("id").getAsString(), msg = ev.get("message").getAsString(), topic = ev.get("topic").getAsString();
            long time = ev.get("time").getAsLong();
            if (!dm) { // heartbeat from a friend
                for (Friends.Friend f : List.copyOf(Friends.list()))
                    if (topic("on", f.uuid).equals(topic)) {
                        long at = Math.min(System.currentTimeMillis(), time * 1000);
                        Long prev = SEEN.get(f.uuid);
                        if (prev == null || Math.abs(prev) <= at) SEEN.put(f.uuid, "off".equals(msg) ? -at : at);
                    }
                return;
            }
            synchronized (HANDLED) { if (!HANDLED.add(id)) return; }
            JsonObject o = JsonParser.parseString(msg).getAsJsonObject();
            String from = o.get("f").getAsString();
            if (from.equalsIgnoreCase(me())) return;
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key(from, me()), new GCMParameterSpec(128, Base64.getDecoder().decode(o.get("iv").getAsString())));
            String text = new String(c.doFinal(Base64.getDecoder().decode(o.get("c").getAsString())), StandardCharsets.UTF_8);
            String name = o.has("n") ? o.get("n").getAsString() : "Player";
            if (name.length() > 16 || text.length() > 256) return;
            dmSince = id; saveSince(id);
            SEEN.put(from, System.currentTimeMillis()); // they just wrote, so they're on
            MinecraftClient.getInstance().execute(() -> Friends.onDm(from, name, text, time * 1000));
        }
    }

    // ------------------------------------------------------------------ helpers
    private static SecretKeySpec key(String a, String b) {
        a = a.toLowerCase(); b = b.toLowerCase();
        String pair = a.compareTo(b) < 0 ? a + ":" + b : b + ":" + a;
        return new SecretKeySpec(sha("orbit-dm-v1:" + pair), "AES");
    }
    private static byte[] sha(String s) {
        try { return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    private static String hex(byte[] b) { StringBuilder sb = new StringBuilder(); for (byte x : b) sb.append(String.format("%02x", x)); return sb.toString(); }

    private static String loadSince() {
        try { return Files.exists(STATE) ? new Gson().fromJson(Files.readString(STATE), JsonObject.class).get("since").getAsString().replaceAll("[^A-Za-z0-9]", "") : ""; }
        catch (Exception e) { return ""; }
    }
    private static void saveSince(String t) {
        try { JsonObject o = new JsonObject(); o.addProperty("since", t); Files.writeString(STATE, o.toString()); } catch (Exception ignored) { }
    }
}
