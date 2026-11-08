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
    public static final class Msg { public boolean mine; public String text; public long at; public Msg(boolean mine, String text, long at) { this.mine = mine; this.text = text; this.at = at; } }
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
    public static void onOnline(Collection<String> uuids) { ONLINE.clear(); ONLINE.addAll(uuids); }

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
