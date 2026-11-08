package dev.orbit.client.waypoints;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.orbit.client.OrbitClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Waypoints, saved per server / singleplayer world in config/orbit-waypoints.json. */
public final class Waypoints {
    private Waypoints() {}

    public static final class Waypoint {
        public String name;
        public int x, y, z;
        public String dimension;
        public int color;
        public boolean visible = true;
        public Waypoint(String name, int x, int y, int z, String dimension, int color) { this.name = name; this.x = x; this.y = y; this.z = z; this.dimension = dimension; this.color = color; }
    }

    public static final int[] COLORS = {0xFFB18CFF, 0xFF4DD8FF, 0xFF4DFF88, 0xFFFFD84D, 0xFFFF7B5C, 0xFFFF7BD0, 0xFFFFFFFF};
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("orbit-waypoints.json");
    private static Map<String, List<Waypoint>> all;

    private static Map<String, List<Waypoint>> data() {
        if (all == null) {
            all = new HashMap<>();
            try {
                if (Files.exists(FILE)) {
                    Map<String, List<Waypoint>> m = GSON.fromJson(Files.readString(FILE), new TypeToken<Map<String, List<Waypoint>>>() {}.getType());
                    if (m != null) all.putAll(m);
                }
            } catch (Exception e) { OrbitClient.LOG.warn("Could not read waypoints", e); }
        }
        return all;
    }

    public static void save() {
        try { Files.createDirectories(FILE.getParent()); Files.writeString(FILE, GSON.toJson(data())); }
        catch (Exception e) { OrbitClient.LOG.warn("Could not save waypoints", e); }
    }

    /** "server:play.example.net" or "world:My World"; null when not in a world. */
    public static String worldKey() {
        var mc = MinecraftClient.getInstance();
        if (mc.world == null) return null;
        if (mc.getServer() != null) return "world:" + mc.getServer().getSaveProperties().getLevelName();
        var s = mc.getCurrentServerEntry();
        return "server:" + (s != null ? s.address.toLowerCase() : "unknown");
    }
    public static String dimension() {
        var mc = MinecraftClient.getInstance();
        return mc.world == null ? "" : mc.world.getRegistryKey().getValue().toString();
    }

    /** waypoints of the current world (all dimensions) — editable list */
    public static List<Waypoint> current() {
        String k = worldKey();
        if (k == null) return new ArrayList<>();
        return data().computeIfAbsent(k, x -> new ArrayList<>());
    }

    public static Waypoint addHere() {
        var p = MinecraftClient.getInstance().player;
        if (p == null) return null;
        List<Waypoint> list = current();
        Waypoint w = new Waypoint("Waypoint " + (list.size() + 1), p.getBlockX(), p.getBlockY(), p.getBlockZ(), dimension(), COLORS[list.size() % COLORS.length]);
        list.add(w);
        save();
        return w;
    }
}
