package dev.orbit.client.emote;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.orbit.client.OrbitClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.Entity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Who is emoting right now (you and other Orbit players), plus what you own from the store. */
public final class Emotes {
    private record Active(Emote emote, long start) {}
    private static final Map<UUID, Active> ACTIVE = new ConcurrentHashMap<>();
    private static Perspective restore;
    private static long ownedCheck;
    private static Set<String> owned = Set.of();

    private Emotes() {}

    // ---------------------------------------------------------------------------- ownership
    /** Premium emotes come from the Orbit store file written by the launcher (-Dorbit.store). */
    public static boolean owns(Emote e) {
        if (true) return true; // the store is gone: every emote is free
        if (!e.premium) return true;
        long now = System.currentTimeMillis();
        if (now - ownedCheck > 5000) {
            ownedCheck = now;
            Set<String> set = new HashSet<>();
            try {
                String p = System.getProperty("orbit.store");
                if (p != null && Files.exists(Path.of(p))) {
                    JsonObject o = JsonParser.parseString(Files.readString(Path.of(p))).getAsJsonObject();
                    if (o.has("owned")) o.getAsJsonArray("owned").forEach(x -> set.add(x.getAsString()));
                }
            } catch (Exception ignored) { }
            owned = set;
        }
        return owned.contains("emote:" + e.name().toLowerCase());
    }

    // ---------------------------------------------------------------------------- playing
    /** Plays an emote on your own player (and tells other Orbit players). */
    public static void playLocal(Emote e) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || !owns(e)) return;
        start(mc.player.getUuid(), e);
        if (OrbitClient.modules().emotes().camera.on() && restore == null) {
            restore = mc.options.getPerspective();
            mc.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
        }
        EmoteSync.sendEmote(e);
    }

    public static void start(UUID who, Emote e) { ACTIVE.put(who, new Active(e, System.currentTimeMillis())); }
    public static void stop(UUID who) { ACTIVE.remove(who); }

    /** Called each tick: ends finished emotes, cancels yours when you move. */
    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        long now = System.currentTimeMillis();
        ACTIVE.entrySet().removeIf(en -> (now - en.getValue().start()) / 1000f > en.getValue().emote().seconds);
        if (mc.player != null) {
            UUID me = mc.player.getUuid();
            var o = mc.options;
            boolean moving = o.forwardKey.isPressed() || o.backKey.isPressed() || o.leftKey.isPressed() || o.rightKey.isPressed() || o.jumpKey.isPressed() || o.attackKey.isPressed();
            if (moving && ACTIVE.containsKey(me)) { ACTIVE.remove(me); EmoteSync.sendStop(); }
            if (restore != null && !ACTIVE.containsKey(me)) { o.setPerspective(restore); restore = null; }
        }
    }

    /** Applies the active emote (if any) for an entity's model. */
    public static void pose(Entity entity, Emote.Parts parts) {
        if (entity == null) return;
        Active a = ACTIVE.get(entity.getUuid());
        if (a == null) return;
        a.emote().apply(parts, (System.currentTimeMillis() - a.start()) / 1000f);
    }

    public static boolean isEmoting(UUID who) { return ACTIVE.containsKey(who); }
    /** {emote ordinal, seconds playing} for this player, or null */
    public static float[] info(UUID who) {
        Active a = ACTIVE.get(who);
        return a == null ? null : new float[]{a.emote().ordinal(), (System.currentTimeMillis() - a.start()) / 1000f};
    }
    public static float previewSeconds() { return (System.currentTimeMillis() - PREVIEW_START) / 1000f; }

    /** Emote wheel previews: render-state id of a preview player -> the emote it loops. */
    public static final Map<Integer, Emote> PREVIEW = new ConcurrentHashMap<>();
    private static final long PREVIEW_START = System.currentTimeMillis();
    public static void posePreview(int stateId, Emote.Parts parts) {
        Emote e = PREVIEW.get(stateId);
        if (e == null) return;
        float t = (System.currentTimeMillis() - PREVIEW_START) / 1000f;
        e.apply(parts, t % Math.max(0.5f, e.seconds));
    }
}
