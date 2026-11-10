package dev.orbit.client.friends;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.orbit.client.OrbitClient;
import dev.orbit.client.emote.EmoteSync;
import dev.orbit.client.mixin.MinecraftClientAccessor;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orbit friends: your friend list, chat history and incoming requests. Messages travel through the
 * Orbit sync server when one is set, otherwise through the free public relay (FriendsRelay). Both players need Orbit.
 */
public final class Friends {
    private Friends() {}

    public static final class Friend { public String uuid, name; public Friend(String uuid, String name) { this.uuid = uuid; this.name = name; } }
    public static final class Msg {
        public boolean mine; public String text; public long at;
        /** set on invites: the server address to join */
        public String invite;
        public Msg(boolean mine, String text, long at) { this.mine = mine; this.text = text; this.at = at; }
    }
    private static final class Data { List<Friend> friends = new ArrayList<>(); Map<String, List<Msg>> chats = new HashMap<>(); }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("orbit-friends.json");
    private static Data data;
    private static final Set<String> ONLINE = ConcurrentHashMap.newKeySet();
    /** people who messaged you but aren't your friends yet: uuid -> name */
    public static final Map<String, String> REQUESTS = new ConcurrentHashMap<>();
    /** unread message counts per friend */
    public static final Map<String, Integer> UNREAD = new ConcurrentHashMap<>();
    private static long lastPoll;

    private static Data d() {
        if (data == null) {
            try { data = Files.exists(FILE) ? GSON.fromJson(Files.readString(FILE), Data.class) : null; } catch (Exception e) { OrbitClient.LOG.warn("Could not read friends", e); }
            if (data == null) data = new Data();
            if (data.friends == null) data.friends = new ArrayList<>();
            if (data.chats == null) data.chats = new HashMap<>();
        }
        return data;
    }
    private static void save() {
        try { Files.createDirectories(FILE.getParent()); Files.writeString(FILE, GSON.toJson(d())); } catch (Exception e) { OrbitClient.LOG.warn("Could not save friends", e); }
    }

    public static List<Friend> list() { return d().friends; }
    public static boolean isFriend(String uuid) { return d().friends.stream().anyMatch(f -> f.uuid.equals(uuid)); }
    public static boolean online(String uuid) { return ONLINE.contains(uuid) || FriendsRelay.online(uuid); }
    public static List<Msg> chat(String uuid) { return d().chats.computeIfAbsent(uuid, k -> new ArrayList<>()); }
    public static boolean connected() { return EmoteSync.connected() || FriendsRelay.connected(); }
    public static int unreadTotal() { int n = REQUESTS.size(); for (int v : UNREAD.values()) n += v; return n; }

    /** looks the name up with Mojang and adds the player */
    public static CompletableFuture<Friend> add(String name) {
        return CompletableFuture.supplyAsync(() -> {
            var api = ((MinecraftClientAccessor) MinecraftClient.getInstance()).orbit$apiServices();
            var found = api.profileRepository().findProfileByName(name);
            if (found.isEmpty()) throw new IllegalStateException("No Minecraft player called " + name);
            return new Friend(found.get().id().toString(), found.get().name());
        }, Util.getIoWorkerExecutor()).thenApply(f -> {
            MinecraftClient.getInstance().execute(() -> addDirect(f.uuid, f.name));
            return f;
        });
    }
    public static void addDirect(String uuid, String name) {
        if (!isFriend(uuid)) { d().friends.add(new Friend(uuid, name)); save(); }
        REQUESTS.remove(uuid);
        poll(true);
    }
    public static void remove(String uuid) { d().friends.removeIf(f -> f.uuid.equals(uuid)); save(); }

    public static boolean send(String uuid, String text) {
        text = text.trim();
        if (text.isEmpty()) return false;
        boolean sent = EmoteSync.connected() ? EmoteSync.sendDm(uuid, text) : FriendsRelay.send(uuid, text);
        if (!sent) return false;
        chat(uuid).add(new Msg(true, text, System.currentTimeMillis()));
        trim(uuid);
        save();
        return true;
    }

    // ------------------------------------------------------------------ from the sync server
    public static void onDm(String uuid, String name, String text, long at) {
        chat(uuid).add(new Msg(false, text, at > 0 ? at : System.currentTimeMillis()));
        trim(uuid);
        save();
        if (!isFriend(uuid)) REQUESTS.put(uuid, name);
        UNREAD.merge(uuid, 1, Integer::sum);
        var mc = MinecraftClient.getInstance();
        if (mc.player != null) mc.player.sendMessage(Text.literal("§d[Orbit] §b" + name + "§7: §f" + text), false);
        dev.orbit.client.gui.Sounds.success();
    }
    /** friend uuid -> server they're playing on (only for friends who share it and added you too) */
    private static final Map<String, String> WHERE = new ConcurrentHashMap<>();
    private static boolean primed; // first answer after connecting: no "came online" pop-ups

    public static void onOnline(Collection<String> uuids, Map<String, String> where) {
        if (primed && OrbitClient.modules().ui().friendToasts.on())
            for (String u : uuids) if (!ONLINE.contains(u) && isFriend(u)) {
                String n = name(u), w = where.get(u);
                FriendToasts.show(n, n + " is online", w != null && !w.isEmpty() ? "Playing on " + w : "Say hi in Friends", false);
            }
        primed = true;
        ONLINE.clear(); ONLINE.addAll(uuids);
        WHERE.clear(); WHERE.putAll(where);
    }
    public static void resetPresence() { primed = false; }
    /** "eu.mcpvp.club", "Singleplayer" or null */
    public static String where(String uuid) { String w = WHERE.get(uuid); return w == null || w.isEmpty() ? null : w; }
    public static boolean joinable(String where) { return where != null && !where.equals("Singleplayer"); }
    private static String name(String uuid) {
        for (Friend f : d().friends) if (f.uuid.equals(uuid)) return f.name;
        return REQUESTS.getOrDefault(uuid, "A friend");
    }

    // ------------------------------------------------------------------ invites & joining
    /** invite a friend to the server you're on now */
    public static boolean invite(String uuid) {
        String here = EmoteSync.currentWhere();
        if (!joinable(here) || !EmoteSync.sendInvite(uuid, here)) return false;
        Msg m = new Msg(true, "Invited " + name(uuid) + " to " + here, System.currentTimeMillis());
        chat(uuid).add(m); trim(uuid); save();
        return true;
    }
    public static void onInvite(String uuid, String name, String server, long at) {
        Msg m = new Msg(false, name + " invited you to play on " + server, at > 0 ? at : System.currentTimeMillis());
        m.invite = server;
        chat(uuid).add(m); trim(uuid); save();
        if (!isFriend(uuid)) REQUESTS.put(uuid, name);
        UNREAD.merge(uuid, 1, Integer::sum);
        var mc = MinecraftClient.getInstance();
        if (mc.player != null) mc.player.sendMessage(Text.literal("§d[Orbit] §b" + name + "§7 invited you to §f" + server + "§7 — open §dFriends§7 in the pause menu to join"), false);
        if (OrbitClient.modules().ui().friendToasts.on()) FriendToasts.show(name, name + " invited you", "Play on " + server + " · join in Friends", true);
        dev.orbit.client.gui.Sounds.success();
    }
    public static void onInviteFailed() {
        FriendToasts.show(null, "Invite not delivered", "They need to be online and have you as a friend", false);
    }

    /** leaves the current world (if any) and connects to a server */
    public static void join(String address) {
        var mc = MinecraftClient.getInstance();
        if (!joinable(address)) return;
        if (mc.world != null) {
            String here = EmoteSync.currentWhere();
            if (address.equalsIgnoreCase(here)) return; // already there
            mc.disconnect(net.minecraft.client.world.ClientWorld.QUITTING_MULTIPLAYER_TEXT); // same as the pause menu's Disconnect
        }
        var info = new net.minecraft.client.network.ServerInfo(address, address, net.minecraft.client.network.ServerInfo.ServerType.OTHER);
        net.minecraft.client.gui.screen.multiplayer.ConnectScreen.connect(new dev.orbit.client.gui.OrbitTitleScreen(), mc,
                net.minecraft.client.network.ServerAddress.parse(address), info, false, null);
    }

    /** asks the server who of your friends is online (every 10 s, or now) */
    public static void poll(boolean now) {
        long t = System.currentTimeMillis();
        if (!now && t - lastPoll < 10_000) return;
        lastPoll = t;
        List<String> ids = new ArrayList<>();
        for (Friend f : d().friends) ids.add(f.uuid);
        ids.addAll(REQUESTS.keySet());
        if (!ids.isEmpty()) EmoteSync.askOnline(ids);
    }

    private static void trim(String uuid) { List<Msg> l = chat(uuid); while (l.size() > 200) l.remove(0); }
}
