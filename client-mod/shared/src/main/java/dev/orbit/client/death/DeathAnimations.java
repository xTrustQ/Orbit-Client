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

    public enum Style { SOUL, BLACK_HOLE, THUNDER, FIREWORK, SHATTER, TUNG, PIANO, BOWLING, UFO }
    /** what you can pick for Death Animations (the simple particle styles are only used by Kill Effects now) */
    public static final String[] NAMES = {"Piano Drop", "Bowling Strike", "UFO Abduction", "Tung Tung Bonk", "Random"};
    private static final Style[] PICKS = {Style.PIANO, Style.BOWLING, Style.UFO, Style.TUNG};

    /** phi = direction (radians, world) from the victim to where the bat buddy stands: always on your left; stage = id of the prop's stand-in entity (0 = none) */
    private record Anim(Style style, double x, double y, double z, float height, float phi, int[] tick, int stage) {}
    private static final Map<Integer, Anim> ACTIVE = new HashMap<>();
    /** stand-in entity id -> its animation: big props (piano, ball, saucer) are drawn when that entity is drawn, so they outlive the body */
    private static final Map<Integer, Anim> STAGES = new HashMap<>();
    private static int stageId = -6_000_000;
    private static boolean staged(Style s) { return s == Style.PIANO || s == Style.BOWLING || s == Style.UFO; }
    private static int endTick(Style s) { return staged(s) ? 52 : 40; }

    /** an invisible, untouchable client-only entity at the victim's feet that keeps rendering after the body is gone */
    private static int spawnStage(double x, double y, double z) {
        var mc = MinecraftClient.getInstance();
        try {
            var stand = new net.minecraft.entity.decoration.ArmorStandEntity(EntityType.ARMOR_STAND, mc.world) {
                @Override public boolean canHit() { return false; }
                @Override public boolean isPushable() { return false; }
                @Override public boolean shouldRender(double d) { return d < 96 * 96; }
                @Override public boolean isInvisible() { return true; }
            };
            stand.setNoGravity(true);
            stand.setId(stageId--);
            stand.refreshPositionAfterTeleport(x, y, z);
            mc.world.addEntity(stand);
            return stand.getId();
        } catch (Throwable t) { return 0; }
    }
    /** for the renderer: the stage key of this entity (0 = not a stage) */
    public static int stageKey(Entity e) { return STAGES.containsKey(e.getId()) ? e.getId() : 0; }
    /** draws the props of a stage */
    public static void renderStage(int key, MatrixStack matrices, net.minecraft.client.render.command.OrderedRenderCommandQueue queue,
                                   net.minecraft.client.render.state.CameraRenderState camera, double x, double y, double z, int light, double sqDist) {
        Anim a = STAGES.get(key);
        if (a == null) return;
        float t = a.tick()[0] + MinecraftClient.getInstance().getRenderTickCounter().getTickProgress(false);
        int kind = a.style() == Style.PIANO ? dev.orbit.client.cosmetics.DeathProps.PIANO : a.style() == Style.BOWLING ? dev.orbit.client.cosmetics.DeathProps.BOWLING : dev.orbit.client.cosmetics.DeathProps.UFO;
        dev.orbit.client.cosmetics.DeathProps.render(kind, matrices, queue, camera, x, y, z, light, t, a.phi(), a.height(), sqDist);
    }
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
        add(effectId--, s, x, y, z, height, phi, 0);
    }
    private static void add(int key, Style s, double x, double y, double z, float height, float phi, int startTick) {
        int stage = staged(s) ? spawnStage(x, y, z) : 0;
        Anim a = new Anim(s, x, y, z, height, phi, new int[]{startTick}, stage);
        ACTIVE.put(key, a);
        if (stage != 0) STAGES.put(stage, a);
        start(s, x, y, z, height);
    }

    /** every client tick: spot new deaths and drive the effects */
    public static void tick() {
        var mc = MinecraftClient.getInstance();
        var m = module();
        if (mc.world == null) { ACTIVE.clear(); SEEN.clear(); STAGES.clear(); return; }
        Set<Integer> alive = new HashSet<>();
        if (m != null) for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof LivingEntity l)) continue;
            if (l.deathTime <= 0) continue;
            alive.add(l.getId());
            if (SEEN.add(l.getId()) && l.deathTime <= 3 && eligible(l, m)) {
                int pick = Arrays.asList(NAMES).indexOf(m.style.get());
                Style s = pick < 0 || pick >= PICKS.length ? PICKS[RNG.nextInt(PICKS.length)] : PICKS[pick];
                // the buddy stands to the camera's left of the victim, so you watch it from the side
                float phi = (float) Math.toRadians(mc.gameRenderer.getCamera().getYaw());
                add(l.getId(), s, l.getX(), l.getY(), l.getZ(), l.getHeight(), phi, Math.max(0, l.deathTime - 1)); // in step with the body
            }
        }
        SEEN.retainAll(alive);
        // effects keep running a little after the body is gone
        for (Iterator<Map.Entry<Integer, Anim>> it = ACTIVE.entrySet().iterator(); it.hasNext(); ) {
            Anim a = it.next().getValue();
            int t = ++a.tick()[0];
            effects(a, t);
            if (t > endTick(a.style())) {
                it.remove();
                if (a.stage() != 0) { STAGES.remove(a.stage()); mc.world.removeEntity(a.stage(), Entity.RemovalReason.DISCARDED); }
            }
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
            case PIANO -> { }
            case BOWLING -> sound(SoundEvents.ENTITY_MINECART_RIDING, x, y, z, 1.0f, 0.45f);   // a heavy ball rumbling in
            case UFO -> { sound(SoundEvents.BLOCK_BEACON_ACTIVATE, x, y + 4, z, 1f, 1.7f); sound(SoundEvents.BLOCK_CONDUIT_AMBIENT, x, y + 4, z, 1f, 1.4f); }
        }
    }

    /** the new cartoon deaths: sounds and particles in step with the props (DeathProps) */
    private static void cartoon(Anim a, int t) {
        double x = a.x(), y = a.y(), z = a.z();
        float h = a.height();
        switch (a.style()) {
            case PIANO -> {
                int hit = (int) dev.orbit.client.cosmetics.DeathProps.PIANO_HIT;
                if (t < hit && t % 2 == 0) sound(SoundEvents.BLOCK_NOTE_BLOCK_FLUTE.value(), x, y + 3, z, 0.9f, 2.0f - t * 0.16f); // falling whistle
                if (t == hit) {
                    sound(SoundEvents.BLOCK_ANVIL_LAND, x, y, z, 1.0f, 0.7f);
                    sound(SoundEvents.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, x, y, z, 0.9f, 0.8f);
                    for (float p : new float[]{0.5f, 0.63f, 0.71f, 0.94f, 1.19f}) sound(SoundEvents.BLOCK_NOTE_BLOCK_HARP.value(), x, y + 1, z, 0.8f, p); // the crash chord
                    var wood = new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.DARK_OAK_PLANKS.getDefaultState());
                    var black = new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.BLACK_CONCRETE.getDefaultState());
                    for (int i = 0; i < 40; i++) {
                        double ang = RNG.nextDouble() * Math.PI * 2, r = 0.4 + RNG.nextDouble() * 1.4;
                        p(i % 2 == 0 ? wood : black, x + Math.cos(ang) * r, y + 0.4, z + Math.sin(ang) * r, Math.cos(ang) * 0.3, 0.3 + RNG.nextDouble() * 0.3, Math.sin(ang) * 0.3);
                    }
                    for (int i = 0; i < 24; i++) { double ang = i * Math.PI / 12; p(ParticleTypes.CLOUD, x + Math.cos(ang) * 1.3, y + 0.15, z + Math.sin(ang) * 1.3, Math.cos(ang) * 0.12, 0.01, Math.sin(ang) * 0.12); }
                    for (int i = 0; i < 10; i++) p(ParticleTypes.NOTE, x + (RNG.nextDouble() - 0.5) * 2, y + 1.4 + RNG.nextDouble(), z + (RNG.nextDouble() - 0.5) * 2, RNG.nextDouble(), 0, 0);
                }
                if (t == hit + 5) sound(SoundEvents.BLOCK_NOTE_BLOCK_HARP.value(), x, y + 1, z, 0.7f, 1.8f);   // plink
                if (t == hit + 11) sound(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), x, y + 1, z, 0.9f, 0.5f);  // ...plonk
                if (t > hit && t < hit + 20 && t % 4 == 0) p(ParticleTypes.NOTE, x + (RNG.nextDouble() - 0.5) * 1.5, y + 1.3, z + (RNG.nextDouble() - 0.5) * 1.5, RNG.nextDouble(), 0, 0);
            }
            case BOWLING -> {
                int hit = (int) dev.orbit.client.cosmetics.DeathProps.BALL_HIT;
                if (t == hit) {
                    sound(SoundEvents.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, x, y, z, 1.0f, 1.25f);
                    sound(SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, x, y, z, 1.0f, 0.7f);
                    for (int i = 0; i < 20; i++) p(ParticleTypes.CRIT, x, y + h * 0.4, z, (RNG.nextDouble() - 0.5) * 0.9, RNG.nextDouble() * 0.5, (RNG.nextDouble() - 0.5) * 0.9);
                    p(ParticleTypes.EXPLOSION, x, y + h * 0.4, z, 0, 0, 0);
                }
                if (t == hit + 1 || t == hit + 2 || t == hit + 4) sound(SoundEvents.BLOCK_BAMBOO_WOOD_BREAK, x, y, z, 1.0f, 1.3f + RNG.nextFloat() * 0.3f); // pins clattering
                if (t == hit + 6) { sound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, x, y + 1, z, 0.6f, 1.2f); for (int i = 0; i < 16; i++) p(ParticleTypes.FIREWORK, x, y + h + 1, z, (RNG.nextDouble() - 0.5) * 0.3, RNG.nextDouble() * 0.2, (RNG.nextDouble() - 0.5) * 0.3); }
                if (t > hit && t < hit + 14) { double[] o = flingOffset(t - hit, a.phi(), 1.2f); p(ParticleTypes.CLOUD, x + o[0], y + o[1] + h * 0.5, z + o[2], 0, 0, 0); }
            }
            case UFO -> {
                int take = (int) dev.orbit.client.cosmetics.DeathProps.UFO_TAKE, gone = (int) dev.orbit.client.cosmetics.DeathProps.UFO_GONE;
                if (t == take) sound(SoundEvents.BLOCK_BEACON_AMBIENT, x, y + 2, z, 1.2f, 1.5f);
                if (t > take && t < gone && t % 3 == 0) sound(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, x, y + 2, z, 0.9f, 0.8f + (t - take) * 0.08f);
                if (t > take && t < 21) for (int i = 0; i < 2; i++) { double ang = RNG.nextDouble() * Math.PI * 2, r = RNG.nextDouble() * 0.9; p(ParticleTypes.END_ROD, x + Math.cos(ang) * r, y + RNG.nextDouble() * 0.5, z + Math.sin(ang) * r, 0, 0.12, 0); }
                if (t == gone) { sound(SoundEvents.ENTITY_ENDERMAN_TELEPORT, x, y + h + 2, z, 1f, 1.6f); sound(SoundEvents.ENTITY_ITEM_PICKUP, x, y + h + 2, z, 1f, 0.6f); }
                if (t == 24) sound(SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, x, y + h + 2, z, 1f, 0.5f);
            }
            default -> { }
        }
    }

    private static void effects(Anim a, int t) {
        double x = a.x(), y = a.y(), z = a.z();
        float h = a.height();
        int accent = OrbitClient.modules().ui().accent() & 0xFFFFFF, accent2 = OrbitClient.modules().ui().accent2() & 0xFFFFFF;
        if (staged(a.style())) { cartoon(a, t); return; }
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
            case PIANO -> { // looks up... then flattened like a pancake under the piano
                float hit = dev.orbit.client.cosmetics.DeathProps.PIANO_HIT;
                if (deathTime >= hit) { float k = Math.min(1, (deathTime - hit) / 0.6f); m.scale(1 + 0.6f * k, 1 - 0.9f * k, 1 + 0.6f * k); }
                else if (deathTime > hit - 3) { float k = (deathTime - (hit - 3)) / 3; m.scale(1 - 0.05f * k, 1 + 0.08f * k, 1 - 0.05f * k); } // flinch upward
            }
            case BOWLING -> { // knocked flying like a pin: the ball hits from your left
                float hit = dev.orbit.client.cosmetics.DeathProps.BALL_HIT;
                if (deathTime >= hit) fling(m, deathTime - hit, height, bodyYaw, phi, 1.2f);
            }
            case UFO -> { // lifted up the beam, spinning and shrinking into the saucer
                float take = dev.orbit.client.cosmetics.DeathProps.UFO_TAKE, gone = dev.orbit.client.cosmetics.DeathProps.UFO_GONE;
                if (deathTime < take) break;
                float k = Math.min(1, (deathTime - take) / (gone - take)), e = k * k * (3 - 2 * k);
                m.translate(0, e * (height + 1.25f), 0);
                m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(e * e * 900));
                float s = deathTime > gone ? 0.001f : 1 - 0.65f * e;
                scaleAround(m, height, s, s);
            }
            default -> { }
        }
    }

    /** a body knocked away from your left (the same maths gives the particle trail): u = ticks since the hit */
    private static void fling(MatrixStack m, float u, float height, float bodyYaw, float phi, float power) {
        float lx = -(float) Math.cos(phi), lz = -(float) Math.sin(phi);
        float dist = (u * 0.5f + u * u * 0.05f) * power, up = (u * 0.55f - u * u * 0.03f) * power;
        float yawRot = 180 - bodyYaw;
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yawRot));
        m.translate(lx * dist, Math.max(-0.2f, up), lz * dist);
        m.translate(0, height / 2, 0);
        m.multiply(new org.joml.Quaternionf().rotateAxis(u * 0.9f, -lz, 0, lx));
        m.translate(0, -height / 2, 0);
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yawRot));
    }
    private static double[] flingOffset(float u, float phi, float power) {
        double dist = (u * 0.5 + u * u * 0.05) * power, up = Math.max(-0.2, (u * 0.55 - u * u * 0.03) * power);
        return new double[]{-Math.cos(phi) * dist, up, -Math.sin(phi) * dist};
    }
    private static void scaleAround(MatrixStack m, float height, float sxz, float sy) {
        m.translate(0, height / 2, 0);
        m.scale(sxz, sy, sxz);
        m.translate(0, -height / 2, 0);
    }
}
