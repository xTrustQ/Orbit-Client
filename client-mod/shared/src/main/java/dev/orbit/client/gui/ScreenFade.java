package dev.orbit.client.gui;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Clean transitions: every Orbit screen — and every menu screen while you're not in a world —
 * fades in from the space background instead of popping in.
 */
public final class ScreenFade {
    private ScreenFade() {}
    private static final Map<Screen, Long> OPENED = new WeakHashMap<>();
    private static final long DURATION = 260;

    public static void install() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            boolean orbit = screen instanceof OrbitBaseScreen;
            if (screen instanceof net.minecraft.client.gui.screen.ChatScreen || screen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen<?>) return; // never delay chat / inventories
            OPENED.putIfAbsent(screen, System.currentTimeMillis()); // resizing re-inits; don't fade again
            boolean inWorld = client.world != null;
            if (orbit) // Orbit screens: their content fades in on top of the background
                ScreenEvents.beforeRender(screen).register((s, ctx, mx, my, delta) -> {
                    Long at = OPENED.get(s);
                    if (at == null) return;
                    float t = (System.currentTimeMillis() - at) / (float) DURATION;
                    Render2D.fade = t >= 1 ? 1 : 0.15f + 0.85f * Render2D.easeOut(t);
                });
            ScreenEvents.afterRender(screen).register((s, ctx, mx, my, delta) -> {
                Render2D.fade = 1;
                Long at = OPENED.get(s);
                if (at == null) return;
                float t = (System.currentTimeMillis() - at) / (float) DURATION;
                if (t >= 1) return;
                float a = 1 - Render2D.easeOut(t);
                int col = inWorld ? 0x05030D : 0x0A0818;
                int max = inWorld ? (orbit ? 120 : 150) : 235; // in a world: a softer fade so the game shows through
                ctx.fill(0, 0, s.width, s.height, Render2D.withAlpha(col, Math.round(max * a)));
            });
        });
    }
}
