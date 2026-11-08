package dev.orbit.client.gui;

import dev.orbit.client.OrbitClient;
import dev.orbit.client.compat.Compat;
import dev.orbit.client.cosmetics.CosmeticsFeature;
import dev.orbit.client.emote.Emote;
import dev.orbit.client.emote.EmoteWheel;
import dev.orbit.client.emote.Emotes;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import static dev.orbit.client.gui.Render2D.*;

/**
 * Round emote wheel: six round slots on a ring, each showing you doing that emote, name underneath.
 * Three pages (scroll / arrow keys). Point and click (or press the emote key again). Right-click a slot
 * to take it off; empty slots open Locker → Emotes where you pick what goes on the wheel.
 */
public final class EmoteWheelScreen extends OrbitBaseScreen {
    private static final int SLOTS = EmoteWheel.SLOTS;
    private static final int ID_BASE = CosmeticsFeature.PREVIEW_ID - 500;
    private static int page;
    private final long openedAt = System.currentTimeMillis();
    private long pageAt;
    private int pageDir;
    private final Map<Integer, PlayerPreview> previews = new HashMap<>();
    private final Map<Integer, Float> hover = new HashMap<>();
    private int hovered = -1;
    private long lastFrame = System.nanoTime();

    public EmoteWheelScreen() { super(Text.literal("Emotes")); }

    @Override public boolean shouldPause() { return false; }
    private Emote slot(int i) { return EmoteWheel.get(page, i); }

    @Override
    public void renderBackground(net.minecraft.client.gui.DrawContext c, int mx, int my, float delta) {
        float t = easeOut((System.currentTimeMillis() - openedAt) / 200f);
        c.fillGradient(0, 0, width, height, withAlpha(0x05030D, (int) (50 * t)), withAlpha(0x0B0718, (int) (110 * t)));
    }

    private Supplier<SkinTextures> skin() { return () -> client.player != null ? client.player.getSkin() : null; }

    private float bigR() { return Math.min(height * 0.43f, width * 0.32f); }
    private float slotR() { return bigR() * 0.27f; }
    private float[] pos(int i, float spin) {
        double a = i * Math.PI * 2 / SLOTS - Math.PI / 2 + spin;
        float rr = bigR() * 0.62f;
        return new float[]{width / 2f + (float) Math.cos(a) * rr, height / 2f + (float) Math.sin(a) * rr};
    }

    @Override
    public void render(net.minecraft.client.gui.DrawContext c, int mx, int my, float delta) {
        super.render(c, mx, my, delta);
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        float t = easeOut((System.currentTimeMillis() - openedAt) / 260f);
        float pt = easeOut((System.currentTimeMillis() - pageAt) / 300f);
        float spin = pageDir * (1 - pt) * 0.5f; // the ring turns a little when you change page
        int cx = width / 2, cy = height / 2, acc = accent();
        float R = bigR() * (0.9f + 0.1f * t), r = slotR();

        // hovered slot = the sector the mouse points at (outside the hub)
        double dx = mx - cx, dy = my - cy;
        hovered = -1;
        if (Math.hypot(dx, dy) > R * 0.24f) {
            double ang = Math.atan2(dy, dx) + Math.PI / 2 + Math.PI / SLOTS;
            hovered = (int) Math.floor((((ang % (Math.PI * 2)) + Math.PI * 2) % (Math.PI * 2)) / (Math.PI * 2 / SLOTS));
        }

        // the wheel: soft ring + hub
        disc(c, cx, cy, R + 2, withAlpha(0xFFFFFF, (int) (26 * t)));
        disc(c, cx, cy, R, withAlpha(0x0B0818, (int) (150 * t)));
        if (hovered >= 0) { // glow behind the pointed-at slot
            float[] p = pos(hovered, spin);
            disc(c, p[0], p[1], r * 1.5f, withAlpha(acc, (int) (40 * t)));
        }
        disc(c, cx, cy, R * 0.25f + 1.5f, withAlpha(acc, (int) (110 * t)));
        disc(c, cx, cy, R * 0.25f, withAlpha(0x0B0818, (int) (235 * t)));

        float fadeIn = 0.25f + 0.75f * pt;
        for (int i = 0; i < SLOTS; i++) {
            Emote e = slot(i);
            float[] p = pos(i, spin);
            float hv = approach(hover.getOrDefault(i, 0f), i == hovered ? 1 : 0, 14, dt);
            hover.put(i, hv);
            float rr = r * (1 + 0.08f * hv) * (0.8f + 0.2f * t);
            disc(c, p[0], p[1], rr + 1.5f, lerpColor(withAlpha(0xFFFFFF, (int) (50 * t)), withAlpha(acc, (int) (255 * t)), hv));
            disc(c, p[0], p[1], rr, withAlpha(lerpColor(0x15112A, 0x241B45, hv), (int) (235 * t)));
            if (e == null) {
                centered(c, "+", p[0], p[1] - 8, withAlpha(0xFFFFFF, (int) (120 * t * fadeIn)), false);
                centered(c, "Empty", p[0], p[1] + 3, withAlpha(0xFFFFFF, (int) (70 * t * fadeIn)), false);
                continue;
            }
            int id = ID_BASE - (page * SLOTS + i);
            Emotes.PREVIEW.put(id, e);
            PlayerPreview pv = previews.computeIfAbsent(id, k -> { PlayerPreview pp = new PlayerPreview(0, 0, 1, 1, skin()); pp.id = k; pp.setSpin(-15); pp.expand = false; return pp; });
            pv.w = Math.round(rr * 1.35f); pv.h = Math.round(rr * 1.42f);
            pv.x = Math.round(p[0] - pv.w / 2f); pv.y = Math.round(p[1] - rr * 0.92f);
            if (t > 0.35f && pt > 0.3f) pv.render(c);
            String name = e.title.toUpperCase(java.util.Locale.ROOT);
            float ny = p[1] + rr * 0.56f;
            round(c, p[0] - width(name) / 2f - 4, ny - 2, width(name) + 8, 12, 6, withAlpha(0x0B0818, (int) (200 * t)));
            centered(c, name, p[0], ny, i == hovered ? 0xFFFFFFFF : withAlpha(0xFFFFFF, (int) (215 * t * fadeIn)), false);
        }

        // hub: hovered name, page and page dots
        Emote hv = hovered >= 0 ? slot(hovered) : null;
        if (hv != null) centered(c, fit(hv.title, Math.round(R * 0.46f), true), cx, cy - 9, withAlpha(acc, (int) (255 * t)), true);
        else centered(c, "Emotes", cx, cy - 9, withAlpha(0xFFFFFF, (int) (200 * t)), true);
        centered(c, (page + 1) + " / " + EmoteWheel.PAGES, cx, cy + 2, withAlpha(0xFFFFFF, (int) (150 * t)), false);
        for (int p = 0; p < EmoteWheel.PAGES; p++)
            disc(c, cx + (p - 1) * 7, cy + 15, p == page ? 2.4f : 1.6f, withAlpha(p == page ? acc : 0xFFFFFF, (int) ((p == page ? 255 : 90) * t)));
        centered(c, "Click to play  ·  scroll for pages  ·  right-click removes  ·  edit in Locker → Emotes", cx, height - 14, withAlpha(0xFFFFFF, (int) (130 * t)), true);
    }

    private void turn(int d) {
        page = Math.floorMod(page + d, EmoteWheel.PAGES);
        pageDir = d; pageAt = System.currentTimeMillis();
        Sounds.tab();
    }

    private void pick() {
        Emote e = hovered >= 0 ? slot(hovered) : null;
        if (hovered >= 0 && e == null) { Sounds.click(); client.setScreen(OrbitTitleScreen.pauseOn("emotes")); return; }
        close();
        if (e != null) { Sounds.click(); Emotes.playLocal(e); }
    }

    @Override public void removed() { Emotes.PREVIEW.clear(); }
    @Override protected boolean onClick(double mx, double my, int button) {
        if (button == 1) { if (hovered >= 0 && slot(hovered) != null) { EmoteWheel.clear(page, hovered); Sounds.toggle(false); } return true; }
        pick();
        return true;
    }
    @Override protected boolean onScroll(double mx, double my, double v) { turn(v < 0 ? 1 : -1); return true; }
    @Override protected boolean onKey(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { close(); return true; }
        if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_D) { turn(1); return true; }
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_A) { turn(-1); return true; }
        if (key >= GLFW.GLFW_KEY_1 && key < GLFW.GLFW_KEY_1 + EmoteWheel.PAGES) { int d = key - GLFW.GLFW_KEY_1 - page; if (d != 0) turn(d); return true; }
        if (Compat.matches(OrbitClient.emoteKey, key, scan)) { pick(); return true; }
        return false;
    }
}
