package dev.orbit.client.pvp;

import dev.orbit.client.OrbitClient;
import dev.orbit.client.death.DeathAnimations;
import dev.orbit.client.module.PvpModules;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

import java.util.Random;

/** Kill Effect Editor: what happens when YOU kill someone — an effect, a sound and a "+1 kill" popup. */
public final class KillEffects {
    private KillEffects() {}

    public static final String[] EFFECTS = {"Lightning", "Firework", "Soul", "Black Hole", "Shatter", "Blood", "Hearts", "Totem", "None"};
    public static final String[] SOUNDS = {"Level up", "Orb", "Anvil", "Bell", "Thunder", "Chime", "Totem", "Blast", "None"};
    private static final Random RNG = new Random();
    public static String popupName = "";
    public static long popupAt;

    private static PvpModules.KillEffectsModule module() {
        return OrbitClient.modules() != null && OrbitClient.modules().get("killeffects") instanceof PvpModules.KillEffectsModule m && m.isEnabled() ? m : null;
    }

    public static void onKill(LivingEntity victim) {
        var m = module();
        if (m == null) return;
        play(m, victim.getX(), victim.getY(), victim.getZ(), victim.getHeight());
        popupName = victim.getName().getString();
        popupAt = System.currentTimeMillis();
    }

    /** preview: in front of you */
    public static void preview() {
        var mc = MinecraftClient.getInstance();
        var m = module();
        if (mc.player == null) return;
        if (m == null && OrbitClient.modules().get("killeffects") instanceof PvpModules.KillEffectsModule mm) m = mm;
        if (m == null) return;
        var look = mc.player.getRotationVec(1).multiply(3);
        play(m, mc.player.getX() + look.x, mc.player.getY(), mc.player.getZ() + look.z, 1.8f);
        popupName = "Preview"; popupAt = System.currentTimeMillis();
    }

    private static void play(PvpModules.KillEffectsModule m, double x, double y, double z, float h) {
        var mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        int amount = (int) Math.round(m.particles.get());
        switch (m.effect.get()) {
            case "Lightning" -> DeathAnimations.playAt(DeathAnimations.Style.THUNDER, x, y, z, h);
            case "Firework" -> DeathAnimations.playAt(DeathAnimations.Style.FIREWORK, x, y, z, h);
            case "Soul" -> DeathAnimations.playAt(DeathAnimations.Style.SOUL, x, y, z, h);
            case "Black Hole" -> DeathAnimations.playAt(DeathAnimations.Style.BLACK_HOLE, x, y, z, h);
            case "Shatter" -> DeathAnimations.playAt(DeathAnimations.Style.SHATTER, x, y, z, h);
            case "Blood" -> {
                var blood = new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.getDefaultState());
                for (int i = 0; i < amount * 2; i++) mc.world.addParticleClient(blood, x + (RNG.nextDouble() - 0.5) * 0.6, y + RNG.nextDouble() * h, z + (RNG.nextDouble() - 0.5) * 0.6, (RNG.nextDouble() - 0.5) * 0.5, RNG.nextDouble() * 0.4, (RNG.nextDouble() - 0.5) * 0.5);
            }
            case "Hearts" -> { for (int i = 0; i < Math.max(4, amount / 3); i++) mc.world.addParticleClient(ParticleTypes.HEART, x + (RNG.nextDouble() - 0.5), y + h * 0.5 + RNG.nextDouble(), z + (RNG.nextDouble() - 0.5), 0, 0.1, 0); }
            case "Totem" -> { for (int i = 0; i < amount * 2; i++) mc.world.addParticleClient(ParticleTypes.TOTEM_OF_UNDYING, x, y + h * 0.6, z, (RNG.nextDouble() - 0.5) * 0.9, RNG.nextDouble() * 0.8, (RNG.nextDouble() - 0.5) * 0.9); }
            default -> { }
        }
        SoundEvent s = switch (m.sound.get()) {
            case "Level up" -> SoundEvents.ENTITY_PLAYER_LEVELUP;
            case "Orb" -> SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP;
            case "Anvil" -> SoundEvents.BLOCK_ANVIL_LAND;
            case "Bell" -> SoundEvents.BLOCK_BELL_USE;
            case "Thunder" -> SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER;
            case "Chime" -> SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value();
            case "Totem" -> SoundEvents.ITEM_TOTEM_USE;
            case "Blast" -> SoundEvents.ENTITY_FIREWORK_ROCKET_LARGE_BLAST;
            default -> null;
        };
        if (s != null && mc.player != null) mc.world.playSoundClient(mc.player.getX(), mc.player.getY(), mc.player.getZ(), s, SoundCategory.PLAYERS, m.volume.f(), m.pitch.f(), false);
    }
}
