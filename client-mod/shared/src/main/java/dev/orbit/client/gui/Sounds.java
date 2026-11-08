package dev.orbit.client.gui;

import dev.orbit.client.OrbitClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

/** Soft UI sound effects for Orbit's menus (built from vanilla sounds; turn off under Settings → Interface). */
public final class Sounds {
    private Sounds() {}
    private static long lastHover;

    private static void play(SoundEvent ev, float pitch, float volume) {
        try {
            if (!OrbitClient.modules().ui().sounds.on()) return;
            MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.ui(ev, pitch, volume));
        } catch (Throwable ignored) {}
    }

    /** Menu opens: a bright crystal chime. */
    public static void open() { play(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.5f, 0.9f); }
    /** Menu closes: the same chime, lower. */
    public static void close() { play(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, 0.6f); }
    /** Switching tabs / pages. */
    public static void tab() { play(SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), 1.7f, 0.35f); }
    /** Generic button. */
    public static void click() { play(SoundEvents.UI_BUTTON_CLICK.value(), 1.25f, 0.3f); }
    /** A mod switched on / off. */
    public static void toggle(boolean on) { play(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), on ? 1.8f : 1.1f, 0.35f); }
    /** Something good happened (bought, saved, switched account). */
    public static void success() { play(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.3f, 0.4f); }
    /** Very quiet tick when the mouse moves onto something (rate-limited). */
    public static void hover() {
        long now = System.currentTimeMillis();
        if (now - lastHover < 60) return;
        lastHover = now;
        play(SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), 2.0f, 0.08f);
    }
}
