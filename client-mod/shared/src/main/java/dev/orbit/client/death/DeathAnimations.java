package dev.orbit.client.death;

import dev.orbit.client.OrbitClient;
import dev.orbit.client.module.NewModules;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.type.FireworkExplosionComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.RotationAxis;

import java.util.*;

/**
 * Death Animations: when a player (or mob) dies, instead of just falling over they
 *  - Soul Ascend: rise up spinning while a spiral of souls and light carries them away
 *  - Black Hole: get sucked into a swirling purple singularity that collapses with a flash of light
 *  - Thunder Strike: get hit by a lightning bolt, sparks and smoke
 *  - Firework: swell up and pop into a firework in your theme colours
 *  - Shatter: freeze solid and burst into shards of ice
 * Everything is client-side (only you see it).
 */
public final class DeathAnimations {
    private DeathAnimations() {}

    public enum Style { SOUL, BLACK_HOLE, THUNDER, FIREWORK, SHATTER, TUNG }
    public static final String[] NAMES = {"Soul Ascend", "Black Hole", "Thunder Strike", "Firework", "Shatter", "Tung Tung Bonk", "Random"};

    /** phi = direction (radians, world) from the victim to where the bat buddy stands: always on your left */
    private record Anim(Style style, double x, double y, double z, float height, float phi, int[] tick) {}
    private static final Map<Integer, Anim> ACTIVE = new HashMap<>();
    private static final Set<Integer> SEEN = new HashSet<>();
    private static final Random RNG = new Random();
    private static int boltId = -2_000_000;

    private static NewModules.DeathAnimationsModule module() {
        return OrbitClient.modules() != null && OrbitClient.modules().get("deathanim") instanceof NewModules.DeathAnimationsModule m && m.isEnabled() ? m : null;
    }

    /** the style playing on this dying entity, or -1 (used by the renderer for the body motion) */
    public static int styleFor(Entity e) {
        Anim a = ACTIVE.get(e.getId());
        return a == null ? -1 : a.style().ordinal();
    }
    public static float phiFor(Entity e) { Anim a = ACTIVE.get(e.getId()); return a == null ? 0 : a.phi(); }

    private static boolean eligible(LivingEntity l, NewModules.DeathAnimationsModule m) {
        var mc = MinecraftClient.getInstance();
        if (l == mc.player) return m.self.on();
        if (l instanceof PlayerEntity) return m.players.on();
        return m.mobs.on();
    }

    private static int effectId = -5_000_000;
    /** plays one of the effects (no body motion) at a spot — used by Kill Effects */
    public static void playAt(Style s, double x, double y, double z, float height) {
        var mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        float phi = (float) Math.toRadians(mc.gameRenderer.getCamera().getYaw());
        ACTIVE.put(effectId--, new Anim(s, x, y, z, height, phi, new int[]{0}));
        start(s, x, y, z, height);
    }

    /** every client tick: spot new deaths and drive the effects */
    public static void tick() {
        var mc = MinecraftClient.getInstance();
        var m = module();
        if (mc.world == null) { ACTIVE.clear(); SEEN.clear(); return; }
        Set<Integer> alive = new HashSet<>();
        if (m != null) for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof LivingEntity l)) continue;
            if (l.deathTime <= 0) continue;
            alive.add(l.getId());
            if (SEEN.add(l.getId()) && l.deathTime <= 3 && eligible(l, m)) {
                int pick = Arrays.asList(NAMES).indexOf(m.style.get());
                Style s = pick < 0 || pick >= Style.values().length ? Style.values()[RNG.nextInt(Style.values().length)] : Style.values()[pick];
                // the buddy stands to the camera's left of the victim, so you watch it from the side
                float phi = (float) Math.toRadians(mc.gameRenderer.getCamera().getYaw());
                ACTIVE.put(l.getId(), new Anim(s, l.getX(), l.getY(), l.getZ(), l.getHeight(), phi, new int[]{0}));
                start(s, l.getX(), l.getY(), l.getZ(), l.getHeight());
            }
        }
        SEEN.retainAll(alive);
        // effects keep running a little after the body is gone
        for (Iterator<Map.Entry<Integer, Anim>> it = ACTIVE.entrySet().iterator(); it.hasNext(); ) {
            Anim a = it.next().getValue();
            int t = ++a.tick()[0];
            effects(a, t);
            if (t > 40) it.remove();
        }
    }

    // ------------------------------------------------------------------ sounds & one-shot effects
    private static void sound(SoundEvent s, double x, double y, double z, float vol, float pitch) {
        MinecraftClient.getInstance().world.playSoundClient(x, y, z, s, SoundCategory.PLAYERS, vol, pitch, false);
    }
    private static void p(ParticleEffect e, double x, double y, double z, double vx, double vy, double vz) {
        MinecraftClient.getInstance().world.addParticleClient(e, x, y, z, vx, vy, vz);
    }

    private static void start(Style s, double x, double y, double z, float h) {
        var mc = MinecraftClient.getInstance();
        switch (s) {
            case SOUL -> { sound(SoundEvents.PARTICLE_SOUL_ESCAPE.value(), x, y, z, 1.2f, 0.8f); sound(SoundEvents.BLOCK_BEACON_DEACTIVATE, x, y, z, 0.8f, 1.4f); }
            case BLACK_HOLE -> sound(SoundEvents.ENTITY_ENDERMAN_TELEPORT, x, y, z, 1f, 0.5f);
            case THUNDER -> {
                try {
                    LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(mc.world, SpawnReason.LOAD);
                    if (bolt != null) {
                        bolt.setCosmetic(true);
                        bolt.setId(boltId--);
                        bolt.refreshPositionAfterTeleport(x, y, z);
                        mc.world.addEntity(bolt);
                    }
                } catch (Throwable ignored) { }
                for (int i = 0; i < 30; i++) p(ParticleTypes.ELECTRIC_SPARK, x, y + RNG.nextDouble() * h, z, (RNG.nextDouble() - 0.5) * 0.8, RNG.nextDouble() * 0.5, (RNG.nextDouble() - 0.5) * 0.8);
            }
            case FIREWORK -> sound(SoundEvents.ENTITY_FIREWORK_ROCKET_TWINKLE, x, y, z, 0.6f, 1.6f);
            case SHATTER -> sound(SoundEvents.BLOCK_AMETHYST_BLOCK_BREAK, x, y, z, 1f, 0.6f);
            case TUNG -> { }
        }
    }

    private static void effects(Anim a, int t) {
        double x = a.x(), y = a.y(), z = a.z();
        float h = a.height();
        int accent = OrbitClient.modules().ui().accent() & 0xFFFFFF, accent2 = OrbitClient.modules().ui().accent2() & 0xFFFFFF;
        switch (a.style()) {
            case SOUL -> {
                if (t > 26) return;
                double rise = Math.min(1, t / 20.0) * 1.8;
                for (int i = 0; i < 3; i++) {
                    double ang = t * 0.55 + i * Math.PI * 2 / 3, r = 0.55 + 0.2 * Math.sin(t * 0.3);
                    p(ParticleTypes.SOUL, x + Math.cos(ang) * r, y + rise + h * 0.3, z + Math.sin(ang) * r, 0, 0.05, 0);
                }
                p(ParticleTypes.END_ROD, x + (RNG.nextDouble() - 0.5) * 0.8, y + rise + RNG.nextDouble() * h, z + (RNG.nextDouble() - 0.5) * 0.8, 0, 0.06, 0);
                if (t % 2 == 0) p(new DustParticleEffect(0xBFE9FF, 1.2f), x, y + rise + h * 0.5, z, 0, 0.02, 0);
                if (t == 20) for (int i = 0; i < 24; i++) { double ang = i * Math.PI / 12; p(ParticleTypes.END_ROD, x, y + rise + h * 0.6, z, Math.cos(ang) * 0.18, 0.05, Math.sin(ang) * 0.18); }
            }
            case BLACK_HOLE -> {
                double cy = y + h * 0.5;
                if (t <= 20) {
                    for (int i = 0; i < 6; i++) { // dust pulled in on a spiral
                        double ang = RNG.nextDouble() * Math.PI * 2, r = 2.2 + RNG.nextDouble(), up = (RNG.nextDouble() - 0.5) * 1.6;
                        double px = x + Math.cos(ang) * r, pz = z + Math.sin(ang) * r, py = cy + up;
                        double k = 0.16, tx = (x - px) * k - Math.sin(ang) * 0.12, tz = (z - pz) * k + Math.cos(ang) * 0.12;
                        p(new DustParticleEffect(i % 2 == 0 ? 0x5B2BB5 : accent, 1.1f), px, py, pz, tx, (cy - py) * k, tz);
                    }
                    p(ParticleTypes.PORTAL, x, cy, z, (RNG.nextDouble() - 0.5) * 2, (RNG.nextDouble() - 0.5) * 2, (RNG.nextDouble() - 0.5) * 2);
                    p(new DustParticleEffect(0x0A0412, 2.2f), x, cy, z, 0, 0, 0);
                }
                if (t == 20) { // collapse
                    sound(SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE.value(), x, cy, z, 1f, 1.3f);
                    for (int i = 0; i < 40; i++) {
                        double th = RNG.nextDouble() * Math.PI * 2, ph = RNG.nextDouble() * Math.PI, sp = 0.35;
                        p(ParticleTypes.END_ROD, x, cy, z, Math.sin(ph) * Math.cos(th) * sp, Math.cos(ph) * sp, Math.sin(ph) * Math.sin(th) * sp);
                    }
                    p(ParticleTypes.REVERSE_PORTAL, x, cy, z, 0, 0, 0);
                }
            }
            case THUNDER -> {
                if (t < 14 && t % 2 == 0) for (int i = 0; i < 3; i++) p(ParticleTypes.LARGE_SMOKE, x + (RNG.nextDouble() - 0.5) * 0.7, y + RNG.nextDouble() * h * 0.6, z + (RNG.nextDouble() - 0.5) * 0.7, 0, 0.04, 0);
                if (t < 10) p(ParticleTypes.FLAME, x + (RNG.nextDouble() - 0.5) * 0.6, y + 0.1, z + (RNG.nextDouble() - 0.5) * 0.6, 0, 0.02, 0);
                if (t % 3 == 0 && t < 18) p(ParticleTypes.ELECTRIC_SPARK, x, y + h * 0.5, z, (RNG.nextDouble() - 0.5) * 0.5, RNG.nextDouble() * 0.3, (RNG.nextDouble() - 0.5) * 0.5);
            }
            case FIREWORK -> {
                if (t < 6) p(ParticleTypes.FIREWORK, x, y + h * 0.5, z, (RNG.nextDouble() - 0.5) * 0.1, 0.1, (RNG.nextDouble() - 0.5) * 0.1);
                if (t == 6) {
                    var mc = MinecraftClient.getInstance();
                    var shape = RNG.nextBoolean() ? FireworkExplosionComponent.Type.LARGE_BALL : FireworkExplosionComponent.Type.STAR;
                    int[] bright = {0xFFD84D, 0xFF5CA8, 0x4DD8FF, 0x7CFF6B, 0xB18CFF};
                    int c1 = bright[RNG.nextInt(bright.length)], c2 = bright[RNG.nextInt(bright.length)];
                    var boom = new FireworkExplosionComponent(shape, IntList.of(c1, c2, 0xFFFFFF), IntList.of(0xFFFFFF), true, true);
                    var ring = new FireworkExplosionComponent(FireworkExplosionComponent.Type.BURST, IntList.of(c2), IntList.of(c1), false, true);
                    mc.world.addFireworkParticle(x, y + h * 0.6, z, 0, 0, 0, List.of(boom, ring));
                    sound(SoundEvents.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, x, y, z, 1.2f, 1f);
                }
            }
            case TUNG -> {
                double bx = x + Math.cos(a.phi()) * 1.45, bz = z + Math.sin(a.phi()) * 1.45;
                if (t == 1) for (int i = 0; i < 14; i++) p(ParticleTypes.POOF, bx + (RNG.nextDouble() - 0.5), y + 0.1, bz + (RNG.nextDouble() - 0.5), 0, 0.05, 0);
                if (t == 4 || t == 7 || t == 10) { // tung tung tung
                    sound(SoundEvents.BLOCK_NOTE_BLOCK_BASEDRUM.value(), x, y + h, z, 1f, 0.75f + (t - 4) * 0.04f);
                    sound(SoundEvents.BLOCK_BAMBOO_WOOD_HIT, x, y + h, z, 1.2f, 0.55f);
                    for (int i = 0; i < 5; i++) p(ParticleTypes.CRIT, x, y + h + 0.1, z, (RNG.nextDouble() - 0.5) * 0.3, 0.15, (RNG.nextDouble() - 0.5) * 0.3);
                }
                if (t == 12) { // BONK
                    sound(SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, x, y + 1, z, 1.2f, 1.1f);
                    sound(SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, x, y + 1, z, 1f, 0.8f);
                    for (int i = 0; i < 26; i++) p(ParticleTypes.CRIT, x, y + h * 0.6, z, (RNG.nextDouble() - 0.5) * 0.9, RNG.nextDouble() * 0.6, (RNG.nextDouble() - 0.5) * 0.9);
                    p(ParticleTypes.EXPLOSION, x, y + h * 0.6, z, 0, 0, 0);
                }
                if (t > 12 && t < 22) { // trail behind the flying victim
                    double[] o = dev.orbit.client.cosmetics.Props.bonkOffset(t, a.phi());
                    p(ParticleTypes.CLOUD, x + o[0], y + o[1] + h * 0.5, z + o[2], 0, 0, 0);
                    p(ParticleTypes.ENCHANTED_HIT, x + o[0], y + o[1] + h * 0.5, z + o[2], 0, 0, 0);
                }
            }
            case SHATTER -> {
                if (t < 5) for (int i = 0; i < 3; i++) p(ParticleTypes.SNOWFLAKE, x + (RNG.nextDouble() - 0.5) * 0.8, y + RNG.nextDouble() * h, z + (RNG.nextDouble() - 0.5) * 0.8, 0, 0, 0);
                if (t == 5) {
                    sound(SoundEvents.BLOCK_GLASS_BREAK, x, y, z, 1.2f, 0.8f);
                    var ice = new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.PACKED_ICE.getDefaultState());
                    var glass = new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.LIGHT_BLUE_STAINED_GLASS.getDefaultState());
                    for (int i = 0; i < 70; i++) {
                        double px = x + (RNG.nextDouble() - 0.5) * 0.7, py = y + RNG.nextDouble() * h, pz = z + (RNG.nextDouble() - 0.5) * 0.7;
                        p(i % 3 == 0 ? glass : ice, px, py, pz, (px - x) * 1.6, 0.25 + RNG.nextDouble() * 0.3, (pz - z) * 1.6);
                    }
                    for (int i = 0; i < 16; i++) p(ParticleTypes.SNOWFLAKE, x, y + h * 0.5, z, (RNG.nextDouble() - 0.5) * 0.4, RNG.nextDouble() * 0.2, (RNG.nextDouble() - 0.5) * 0.4);
                }
            }
        }
    }

    // ------------------------------------------------------------------ body motion (render thread)
    /** false = keep Minecraft's falling-over rotation for this style */
    public static boolean replacesFall(int style) { return style >= 0 && Style.values()[style] != Style.THUNDER; }
    public static boolean isBonk(int style) { return style == Style.TUNG.ordinal(); }

    /** applied after Minecraft's body rotation; deathTime counts up from 1 (20 = gone) */
    public static void transform(int style, MatrixStack m, float deathTime, float height, float bodyYaw, float phi) {
        if (isBonk(style)) { dev.orbit.client.cosmetics.Props.bonkLaunch(m, deathTime, height, bodyYaw, phi); return; }
        float t = Math.max(0, Math.min(1, (deathTime - 1) / 19f));
        float ease = t * t * (3 - 2 * t);
        switch (Style.values()[style]) {
            case SOUL -> {
                m.translate(0, ease * 1.8f, 0);
                m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(t * t * 540));
                scaleAround(m, height, 1 - 0.35f * t, 1 - 0.35f * t);
            }
            case BLACK_HOLE -> {
                float s = (float) Math.max(0.001, Math.pow(1 - t, 2.2));
                m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(t * t * 1440));
                m.translate(0, height * 0.5f * (1 - s) * 0.3f, 0);
                scaleAround(m, height, s, s * (1 + t * 0.6f));
            }
            case FIREWORK -> {
                float s = t < 0.28f ? 1 + 0.3f * (t / 0.28f) + 0.04f * (float) Math.sin(deathTime * 3) : Math.max(0.001f, 1.3f * (1 - (t - 0.28f) / 0.08f));
                scaleAround(m, height, s, s);
            }
            case SHATTER -> {
                if (t < 0.22f) m.translate((float) Math.sin(deathTime * 11) * 0.03f, 0, (float) Math.cos(deathTime * 13) * 0.03f); // shiver
                else scaleAround(m, height, 0.001f, 0.001f); // burst into shards
            }
            default -> { }
        }
    }
    private static void scaleAround(MatrixStack m, float height, float sxz, float sy) {
        m.translate(0, height / 2, 0);
        m.scale(sxz, sy, sxz);
        m.translate(0, -height / 2, 0);
    }
}
