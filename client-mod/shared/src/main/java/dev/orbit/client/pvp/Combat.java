package dev.orbit.client.pvp;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Everything Orbit's PvP features need to know about fights, worked out on your client:
 * hits, misses, combos, W-taps, damage dealt/taken, kills, deaths, damage numbers and a short fight timeline.
 */
public final class Combat {
    private Combat() {}

    // ------------------------------------------------------------------ session stats
    public static long sessionStart = System.currentTimeMillis();
    public static String sessionPlace = "";
    public static int hits, misses, kills, deaths, wtaps, bestCombo, combo;
    public static float damageDealt, damageTaken, distance;
    public static long lastHitAt, lastHitInterval, lastHurtAt, comboAt;
    /** the entity you last hit (and when) */
    public static int targetId = -1;
    public static long targetAt;

    public record Event(long at, int type, float amount) {} // type 0 dealt, 1 taken, 2 kill
    public static final List<Event> TIMELINE = Collections.synchronizedList(new ArrayList<>());

    public record DamageNumber(double x, double y, double z, float amount, boolean crit, boolean heal, long at, float drift) {}
    public static final List<DamageNumber> NUMBERS = Collections.synchronizedList(new ArrayList<>());

    /** finished sessions waiting to be shown as a recap */
    public static Snapshot pendingRecap;
    public record Snapshot(String place, long millis, int hits, int misses, int kills, int deaths, int bestCombo, int wtaps, float dealt, float taken, float distance) {}

    private static final Map<Integer, Float> HEALTH = new ConcurrentHashMap<>();
    private static final Set<Integer> KILLED = new HashSet<>();
    private static float myLastHealth = -1;
    private static boolean wasDead, fwdDown, fwdReleasedAfterHit;
    private static long fwdReleaseAt;
    private static double lastX = Double.NaN, lastZ;
    private static final Random RNG = new Random();

    public static void reset(String place) {
        sessionStart = System.currentTimeMillis(); sessionPlace = place;
        hits = misses = kills = deaths = wtaps = bestCombo = combo = 0;
        damageDealt = damageTaken = distance = 0;
        TIMELINE.clear(); NUMBERS.clear(); HEALTH.clear(); KILLED.clear();
        myLastHealth = -1; lastX = Double.NaN; targetId = -1;
    }

    public static Snapshot snapshot() {
        return new Snapshot(sessionPlace, System.currentTimeMillis() - sessionStart, hits, misses, kills, deaths, bestCombo, wtaps, damageDealt, damageTaken, distance);
    }

    public static boolean inFight() { return System.currentTimeMillis() - Math.max(lastHitAt, lastHurtAt) < 8000; }

    // ------------------------------------------------------------------ hooks
    /** you hit something (AttackEntityCallback) */
    public static void onAttack(PlayerEntity me, Entity target) {
        long now = System.currentTimeMillis();
        if (!(target instanceof LivingEntity)) return;
        hits++;
        if (lastHitAt > 0) lastHitInterval = now - lastHitAt;
        lastHitAt = now;
        // combo: hits on the same target without getting hit back
        if (target.getId() != targetId || now - comboAt > 3000) combo = 0;
        combo++; comboAt = now;
        bestCombo = Math.max(bestCombo, combo);
        targetId = target.getId(); targetAt = now;
        fwdReleasedAfterHit = false;
        dev.orbit.client.module.NewModules.ReachDisplay.onAttack(me, target);
    }

    /** you swung at nothing */
    public static void onMiss() { if (inFight()) misses++; }

    // ------------------------------------------------------------------ every client tick
    public static void tick() {
        var mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;
        long now = System.currentTimeMillis();
        var me = mc.player;

        // distance walked
        if (!Double.isNaN(lastX)) { double dx = me.getX() - lastX, dz = me.getZ() - lastZ; double d = Math.sqrt(dx * dx + dz * dz); if (d < 10) distance += (float) d; }
        lastX = me.getX(); lastZ = me.getZ();

        // getting hurt
        float hp = me.getHealth();
        if (myLastHealth >= 0 && hp < myLastHealth - 0.01f) {
            float dmg = myLastHealth - hp;
            damageTaken += dmg; lastHurtAt = now; combo = 0;
            TIMELINE.add(new Event(now, 1, dmg));
        }
        myLastHealth = hp;
        boolean dead = me.isDead() || hp <= 0;
        if (dead && !wasDead) deaths++;
        wasDead = dead;

        // W-taps: after a hit, let go of W (or sprint) and press it again quickly
        boolean fwd = mc.options.forwardKey.isPressed();
        if (now - lastHitAt < 600) {
            if (fwdDown && !fwd) { fwdReleasedAfterHit = true; fwdReleaseAt = now; }
            if (!fwdDown && fwd && fwdReleasedAfterHit && now - fwdReleaseAt < 500) { wtaps++; fwdReleasedAfterHit = false; }
        }
        fwdDown = fwd;

        // health of everything nearby: damage numbers, damage dealt, kills
        Set<Integer> seen = new HashSet<>();
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof LivingEntity l) || e == me || e.squaredDistanceTo(me) > 48 * 48) continue;
            seen.add(e.getId());
            float h = l.getHealth();
            Float before = HEALTH.put(e.getId(), h);
            if (before != null && Math.abs(h - before) > 0.05f && before > 0) {
                float diff = h - before;
                boolean mine = e.getId() == targetId && now - targetAt < 1500;
                if (diff < 0 && mine) { damageDealt += -diff; TIMELINE.add(new Event(now, 0, -diff)); }
                boolean crit = mine && me.fallDistance > 0 && !me.isOnGround();
                NUMBERS.add(new DamageNumber(e.getX(), e.getY() + e.getHeight() + 0.2, e.getZ(), Math.abs(diff), crit, diff > 0, now, (RNG.nextFloat() - 0.5f) * 0.6f));
            }
            if ((l.isDead() || h <= 0) && e.getId() == targetId && now - targetAt < 3000 && KILLED.add(e.getId())) {
                kills++;
                TIMELINE.add(new Event(now, 2, 0));
                dev.orbit.client.pvp.KillEffects.onKill(l);
            }
        }
        HEALTH.keySet().retainAll(seen);
        KILLED.retainAll(seen);
        synchronized (NUMBERS) { NUMBERS.removeIf(n -> now - n.at() > 1400); if (NUMBERS.size() > 80) NUMBERS.subList(0, NUMBERS.size() - 80).clear(); }
        synchronized (TIMELINE) { TIMELINE.removeIf(ev -> now - ev.at() > 30_000); }
    }

    /** the living entity your PvP HUDs talk about: the one you last hit, else what you look at */
    public static LivingEntity target() {
        var mc = MinecraftClient.getInstance();
        if (mc.world == null) return null;
        if (targetId >= 0 && System.currentTimeMillis() - targetAt < 6000 && mc.world.getEntityById(targetId) instanceof LivingEntity l && l.isAlive()) return l;
        if (mc.targetedEntity instanceof LivingEntity l && l.isAlive()) return l;
        return null;
    }
}
