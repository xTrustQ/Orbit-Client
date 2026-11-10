package dev.orbit.client.emote;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.orbit.client.OrbitClient;
import dev.orbit.client.friends.Friends;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Connection to the Orbit sync server (URL passed by the launcher as -Dorbit.sync).
 * - Emotes and Orbit badges are shared with Orbit players on the same Minecraft server ("room").
 * - Friends: online status and private messages work everywhere, also on the main menu.
 * Fully optional: without a server everything else still works.
 */
public final class EmoteSync {
    private static WebSocket socket;
    private static String room = null;
    private static long nextAttempt;
    private static boolean connecting;

    private EmoteSync() {}

    private static String url() {
        String u = System.getProperty("orbit.sync");
        return u == null || u.isBlank() ? null : u;
    }
    public static boolean configured() { return url() != null; }
    public static boolean connected() { return socket != null; }

    private static String currentRoom() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return "";
        if (!OrbitClient.modules().emotes().isEnabled() || !OrbitClient.modules().emotes().sync.on()) return "";
        ServerInfo s = mc.getCurrentServerEntry();
        return s != null ? s.address.toLowerCase() : "singleplayer:" + mc.player.getUuid();
    }

    /** Every tick (also on the main menu): connect/reconnect and keep the room in sync. */
    public static void tick() {
        if (url() == null) { close(); return; }
        if (socket == null) {
            if (!connecting && System.currentTimeMillis() > nextAttempt) connect();
            return;
        }
        String r = currentRoom();
        // re-announce when the emote room, the server you're on, your friend list or sharing changes
        String key = r + "|" + currentWhere() + "|" + Friends.list().size() + "|" + OrbitClient.modules().ui().shareServer.on();
        if (!r.equals(room) || !key.equals(helloKey)) { room = r; helloKey = key; sendHello(); Friends.poll(true); }
        Friends.poll(false);
    }
    private static String helloKey = "";

    /** where you are, for friends: a server address, "Singleplayer", or "" on the menu */
    public static String currentWhere() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return "";
        if (mc.isInSingleplayer()) return "Singleplayer";
        ServerInfo s = mc.getCurrentServerEntry();
        return s != null ? s.address : "";
    }

    private static void connect() {
        connecting = true;
        HttpClient.newHttpClient().newWebSocketBuilder().connectTimeout(Duration.ofSeconds(8))
                .buildAsync(URI.create(url()), new Listener())
                .whenComplete((ws, err) -> {
                    connecting = false;
                    if (err != null) { nextAttempt = System.currentTimeMillis() + 20_000; OrbitClient.LOG.debug("Orbit sync unavailable: {}", err.getMessage()); return; }
                    socket = ws;
                    room = null; // forces a hello on the next tick
                    Friends.resetPresence(); // no "came online" pop-ups for everyone who already was
                });
    }

    private static void close() {
        if (socket != null) { socket.abort(); socket = null; }
    }

    private static boolean send(JsonObject o) {
        WebSocket ws = socket;
        if (ws == null) return false;
        ws.sendText(o.toString(), true).exceptionally(t -> { socket = null; return null; });
        return true;
    }

    private static void sendHello() {
        MinecraftClient mc = MinecraftClient.getInstance();
        UUID id = mc.player != null ? mc.player.getUuid() : mc.getSession().getUuidOrNull();
        if (id == null) return;
        JsonObject o = new JsonObject();
        o.addProperty("type", "hello");
        o.addProperty("uuid", id.toString());
        o.addProperty("name", mc.getSession().getUsername());
        o.addProperty("room", room == null ? "" : room);
        // Friends 2.0: the server you're on (the Orbit server only shows it to people who added you) + your friends
        boolean share = OrbitClient.modules().ui().shareServer.on();
        o.addProperty("where", share ? currentWhere() : "");
        o.addProperty("share", share);
        JsonArray fr = new JsonArray();
        for (Friends.Friend f : Friends.list()) fr.add(f.uuid.toLowerCase());
        o.add("friends", fr);
        send(o);
    }

    public static boolean sendInvite(String to, String server) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "invite");
        o.addProperty("to", to);
        o.addProperty("server", server);
        return send(o);
    }

    public static void sendEmote(Emote e) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "emote");
        o.addProperty("emote", e.name().toLowerCase());
        send(o);
    }

    public static void sendStop() {
        JsonObject o = new JsonObject();
        o.addProperty("type", "stop");
        send(o);
    }

    public static boolean sendDm(String to, String text) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "dm");
        o.addProperty("to", to);
        o.addProperty("text", text);
        return send(o);
    }

    public static void askOnline(List<String> uuids) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "online?");
        JsonArray a = new JsonArray();
        uuids.forEach(a::add);
        o.add("uuids", a);
        send(o);
    }

    private static final class Listener implements WebSocket.Listener {
        private final StringBuilder buf = new StringBuilder();

        @Override public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            buf.append(data);
            if (last) { handle(buf.toString()); buf.setLength(0); }
            ws.request(1);
            return null;
        }

        private void handle(String text) {
            try {
                JsonObject o = JsonParser.parseString(text).getAsJsonObject();
                String type = o.get("type").getAsString();
                MinecraftClient mc = MinecraftClient.getInstance();
                if ("online".equals(type)) {
                    List<String> ids = new ArrayList<>();
                    o.getAsJsonArray("uuids").forEach(e -> ids.add(e.getAsString()));
                    java.util.Map<String, String> where = new java.util.HashMap<>();
                    if (o.has("status") && o.get("status").isJsonObject())
                        for (var e : o.getAsJsonObject("status").entrySet()) where.put(e.getKey(), e.getValue().getAsString());
                    mc.execute(() -> Friends.onOnline(ids, where));
                    return;
                }
                if ("invite-failed".equals(type)) { mc.execute(Friends::onInviteFailed); return; }
                if ("invite-sent".equals(type)) return;
                if (!o.has("uuid")) return;
                UUID who = UUID.fromString(o.get("uuid").getAsString());
                mc.execute(() -> {
                    switch (type) {
                        case "invite" -> Friends.onInvite(who.toString(), o.has("name") ? o.get("name").getAsString() : "Player", o.get("server").getAsString(), o.has("at") ? o.get("at").getAsLong() : 0);
                        case "dm" -> Friends.onDm(who.toString(), o.has("name") ? o.get("name").getAsString() : "Player", o.get("text").getAsString(), o.has("at") ? o.get("at").getAsLong() : 0);
                        case "here" -> dev.orbit.client.cosmetics.OrbitUsers.add(who);
                        case "gone" -> dev.orbit.client.cosmetics.OrbitUsers.remove(who);
                        default -> {
                            if (mc.player != null && who.equals(mc.player.getUuid())) return;
                            if ("emote".equals(type)) { Emote e = Emote.byName(o.get("emote").getAsString()); if (e != null) Emotes.start(who, e); }
                            else if ("stop".equals(type)) Emotes.stop(who);
                        }
                    }
                });
            } catch (Exception ignored) { }
        }

        @Override public CompletionStage<?> onClose(WebSocket ws, int code, String reason) { socket = null; nextAttempt = System.currentTimeMillis() + 10_000; return null; }
        @Override public void onError(WebSocket ws, Throwable error) { socket = null; nextAttempt = System.currentTimeMillis() + 20_000; }
    }
}
