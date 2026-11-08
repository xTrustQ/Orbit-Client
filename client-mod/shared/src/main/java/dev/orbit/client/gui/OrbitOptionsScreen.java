package dev.orbit.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.*;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.function.Supplier;

import static dev.orbit.client.gui.Render2D.*;

/**
 * Orbit's options screen: the settings you change most as smooth sliders and toggles on the left,
 * and tiles that open every detailed Minecraft settings page on the right.
 */
public final class OrbitOptionsScreen extends OrbitBaseScreen {
    private final Screen parent;
    private final long openedAt = System.currentTimeMillis();
    private final Map<Object, Float> hover = new HashMap<>();
    private long lastFrame = System.nanoTime();
    private float dt = 1 / 60f;
    private float scroll, scrollTarget;
    private int dragging = -1;
    private double pendingValue;

    public OrbitOptionsScreen(Screen parent) { super(Text.literal("Options")); this.parent = parent; }
    @Override public void close() { client.options.write(); client.setScreen(parent); }

    // ------------------------------------------------------------------ sliders
    private record Slider(String label, double min, double max, double step, boolean applyOnRelease) {}
    private final Slider[] sliders = {
            new Slider("Field of view", 30, 110, 1, false),
            new Slider("Render distance", 2, 32, 1, true),
            new Slider("Simulation distance", 5, 32, 1, true),
            new Slider("Brightness", 0, 1, 0.01, false),
            new Slider("Mouse sensitivity", 0, 1, 0.005, false),
            new Slider("GUI scale", 0, 6, 1, true),
            new Slider("Max framerate", 10, 260, 10, false),
            new Slider("Master volume", 0, 1, 0.01, false),
            new Slider("Music volume", 0, 1, 0.01, false)};

    private double get(int i) {
        var o = client.options;
        return switch (i) {
            case 0 -> o.getFov().getValue();
            case 1 -> o.getViewDistance().getValue();
            case 2 -> o.getSimulationDistance().getValue();
            case 3 -> o.getGamma().getValue();
            case 4 -> o.getMouseSensitivity().getValue();
            case 5 -> o.getGuiScale().getValue();
            case 6 -> o.getMaxFps().getValue();
            case 7 -> o.getSoundVolumeOption(SoundCategory.MASTER).getValue();
            default -> o.getSoundVolumeOption(SoundCategory.MUSIC).getValue();
        };
    }
    private void set(int i, double v) {
        var o = client.options;
        try {
            switch (i) {
                case 0 -> o.getFov().setValue((int) Math.round(v));
                case 1 -> o.getViewDistance().setValue((int) Math.round(v));
                case 2 -> o.getSimulationDistance().setValue((int) Math.round(v));
                case 3 -> o.getGamma().setValue(v);
                case 4 -> o.getMouseSensitivity().setValue(v);
                case 5 -> { o.getGuiScale().setValue((int) Math.round(v)); client.onResolutionChanged(); }
                case 6 -> o.getMaxFps().setValue((int) Math.round(v));
                case 7 -> o.getSoundVolumeOption(SoundCategory.MASTER).setValue(v);
                default -> o.getSoundVolumeOption(SoundCategory.MUSIC).setValue(v);
            }
        } catch (Exception ignored) { } // out-of-range values are ignored by Minecraft
    }
    private double maxOf(int i) {
        if (i == 5) return Math.max(1, client.getWindow().calculateScaleFactor(0, client.forcesUnicodeFont()));
        return sliders[i].max();
    }
    private String valueText(int i, double v) {
        return switch (i) {
            case 0 -> Math.round(v) == 70 ? "Normal" : Math.round(v) == 110 ? "Quake Pro" : String.valueOf(Math.round(v));
            case 1, 2 -> Math.round(v) + " chunks";
            case 3 -> v <= 0.001 ? "Moody" : v >= 0.999 ? "Bright" : Math.round(v * 100) + "%";
            case 4 -> v <= 0.001 ? "*yawn*" : v >= 0.999 ? "HYPERSPEED" : Math.round(v * 200) + "%";
            case 5 -> Math.round(v) == 0 ? "Auto" : String.valueOf(Math.round(v));
            case 6 -> v >= 260 ? "Unlimited" : Math.round(v) + " fps";
            default -> v <= 0.001 ? "Off" : Math.round(v * 100) + "%";
        };
    }

    // ------------------------------------------------------------------ toggles
    private final String[] toggles = {"Fullscreen", "VSync", "View bobbing", "Smooth lighting", "Clouds"};
    private boolean toggleOn(int i) {
        var o = client.options;
        return switch (i) { case 0 -> o.getFullscreen().getValue(); case 1 -> o.getEnableVsync().getValue(); case 2 -> o.getBobView().getValue(); case 3 -> o.getAo().getValue(); default -> o.getCloudRenderMode().getValue() != CloudRenderMode.OFF; };
    }
    private String toggleLabel(int i) {
        if (i == 4) { var m = client.options.getCloudRenderMode().getValue(); return "Clouds: " + (m == CloudRenderMode.OFF ? "Off" : m == CloudRenderMode.FAST ? "Fast" : "Fancy"); }
        return toggles[i];
    }
    private void flip(int i) {
        var o = client.options;
        switch (i) {
            case 0 -> o.getFullscreen().setValue(!o.getFullscreen().getValue());
            case 1 -> o.getEnableVsync().setValue(!o.getEnableVsync().getValue());
            case 2 -> o.getBobView().setValue(!o.getBobView().getValue());
            case 3 -> o.getAo().setValue(!o.getAo().getValue());
            default -> { var m = o.getCloudRenderMode().getValue(); o.getCloudRenderMode().setValue(m == CloudRenderMode.OFF ? CloudRenderMode.FAST : m == CloudRenderMode.FAST ? CloudRenderMode.FANCY : CloudRenderMode.OFF); }
        }
    }

    // ------------------------------------------------------------------ tiles (Minecraft's detailed pages)
    private record Tile(String label, Item icon, Supplier<Screen> open) {}
    private List<Tile> tiles() {
        var o = client.options;
        return List.of(
                new Tile("Video", Items.PAINTING, () -> new VideoOptionsScreen(this, client, o)),
                new Tile("Controls", Items.LEVER, () -> new ControlsOptionsScreen(this, o)),
                new Tile("Music & Sounds", Items.NOTE_BLOCK, () -> new SoundOptionsScreen(this, o)),
                new Tile("Skin", Items.LEATHER_CHESTPLATE, () -> new SkinOptionsScreen(this, o)),
                new Tile("Language", Items.WRITABLE_BOOK, () -> new LanguageOptionsScreen(this, o, client.getLanguageManager())),
                new Tile("Resource Packs", Items.GRASS_BLOCK, () -> new net.minecraft.client.gui.screen.pack.PackScreen(client.getResourcePackManager(),
                        m -> { o.refreshResourcePacks(m); client.setScreen(this); }, client.getResourcePackDir(), Text.translatable("resourcePack.title"))),
                new Tile("Chat", Items.OAK_SIGN, () -> new ChatOptionsScreen(this, o)),
                new Tile("Accessibility", Items.SPYGLASS, () -> new AccessibilityOptionsScreen(this, o)),
                new Tile("Online", Items.COMPASS, () -> new OnlineOptionsScreen(this, o)),
                new Tile("Credits", Items.BOOK, () -> new net.minecraft.client.gui.screen.option.CreditsAndAttributionScreen(this)));
    }

    // ------------------------------------------------------------------ layout
    private int cw() { return Math.min(560, width - 30); }
    private int cx() { return (width - cw()) / 2; }
    private int leftW() { return Math.round(cw() * 0.56f); }
    private int top() { return 46; }
    private static final int SROW = 25;
    private static boolean in(double mx, double my, double x, double y, double w, double h) { return mx >= x && my >= y && mx < x + w && my < y + h; }
    private float hv(Object k, boolean on) { float v = approach(hover.getOrDefault(k, 0f), on ? 1 : 0, 16, dt); hover.put(k, v); return v; }
    private int contentH() { return sliders.length * SROW + 12 + ((toggles.length + 1) / 2) * 24; }

    @Override
    public void renderBackground(DrawContext c, int mx, int my, float delta) {
        if (client.world != null) { super.renderBackground(c, mx, my, delta); c.fillGradient(0, 0, width, height, 0x99050310, 0xD0050310); }
        else SpaceBackground.draw(c, width, height, mx, my, false);
    }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        long now = System.nanoTime();
        dt = Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        super.render(c, mx, my, delta);
        float intro = easeOut((System.currentTimeMillis() - openedAt) / 380f);
        int cx = cx(), cw = cw(), lw = leftW(), top = top();
        float k = Render2D.pixelScale(1.6f);
        dev.orbit.client.compat.Compat.push(c);
        dev.orbit.client.compat.Compat.translate(c, cx, Render2D.snap(14 + (1 - intro) * 6));
        dev.orbit.client.compat.Compat.scale(c, k);
        display(c, "Options", 0, 0, alpha(0xFFFFFFFF, intro), false);
        dev.orbit.client.compat.Compat.pop(c);
        text(c, "Quick settings on the left · everything else on the right", cx, 33, MUTED, false);

        scroll = approach(scroll, scrollTarget, 16, dt);
        int bottom = height - 30;
        scrollTarget = Math.max(0, Math.min(scrollTarget, Math.max(0, Math.max(contentH(), tilesH()) - (bottom - top))));
        c.enableScissor(0, top - 2, width, bottom);
        int sy = Math.round(top - scroll);
        // sliders
        for (int i = 0; i < sliders.length; i++) {
            float appear = easeOut((System.currentTimeMillis() - openedAt - i * 30) / 320f);
            int y = Math.round(sy + i * SROW + (1 - appear) * 6);
            double max = maxOf(i), v = dragging == i ? pendingValue : get(i);
            double frac = Math.max(0, Math.min(1, (v - sliders[i].min()) / (max - sliders[i].min())));
            float h = hv("s" + i, in(mx, my, cx, y, lw, SROW - 3) || dragging == i);
            round(c, cx, y, lw, SROW - 3, 8, withAlpha(0xFFFFFF, (int) ((16 + 14 * h) * appear)));
            text(c, sliders[i].label(), cx + 8, y + 4, alpha(TEXT, appear), false);
            String val = valueText(i, v);
            text(c, val, cx + lw - 8 - width(val), y + 4, alpha(lerpColor(MUTED, 0xFFFFFFFF, h), appear), false);
            int tx = cx + 8, tw = lw - 16, ty = y + 15;
            round(c, tx, ty, tw, 3, 1, withAlpha(0xFFFFFF, (int) (40 * appear)));
            round(c, tx, ty, (float) (tw * frac), 3, 1, alpha(accent(), appear));
            disc(c, (float) (tx + tw * frac), ty + 1.5f, 3.5f + h, alpha(0xFFFFFFFF, appear));
        }
        // toggles
        int ty0 = sy + sliders.length * SROW + 8, tgw = (lw - 6) / 2;
        for (int i = 0; i < toggles.length; i++) {
            int x = cx + (i % 2) * (tgw + 6), y = ty0 + (i / 2) * 24;
            boolean on = toggleOn(i);
            float h = hv("t" + i, in(mx, my, x, y, tgw, 20));
            round(c, x, y, tgw, 20, 10, on ? withAlpha(accent(), (int) (120 + 60 * h)) : withAlpha(0xFFFFFF, (int) (18 + 16 * h)));
            text(c, fit(toggleLabel(i), tgw - 30, false), x + 10, y + 6, on ? 0xFFFFFFFF : TEXT, false);
            // little switch
            float sx = x + tgw - 22;
            round(c, sx, y + 6, 14, 8, 4, on ? 0xFFFFFFFF : 0x40FFFFFF);
            disc(c, on ? sx + 10 : sx + 4, y + 10, 3, on ? accent() : 0xFFB0A8CC);
        }
        // tiles
        int rx = cx + lw + 12, rw = cw - lw - 12, tw2 = (rw - 6) / 2;
        List<Tile> ts = tiles();
        for (int i = 0; i < ts.size(); i++) {
            float appear = easeOut((System.currentTimeMillis() - openedAt - 80 - i * 30) / 320f);
            int x = rx + (i % 2) * (tw2 + 6), y = Math.round(sy + (i / 2) * 40 + (1 - appear) * 6);
            float h = hv("tile" + i, in(mx, my, x, y, tw2, 36));
            round(c, x, y, tw2, 36, 10, withAlpha(0xFFFFFF, (int) ((18 + 22 * h) * appear)));
            if (h > 0.01f) round(c, x, y + 35, tw2 * h, 1.2f, 1, withAlpha(accent(), (int) (255 * h)));
            item(c, ts.get(i).icon(), x + 8, y + 10 - h, 16);
            text(c, fit(ts.get(i).label(), tw2 - 34, false), x + 30, y + 14, alpha(TEXT, appear), false);
        }
        c.disableScissor();
        SpaceBackground.button(c, width / 2 - 60, height - 25, 120, 18, "Done", mx, my, hv("done", in(mx, my, width / 2 - 60, height - 25, 120, 18)), true);
    }
    private int tilesH() { return ((tiles().size() + 1) / 2) * 40; }

    private double valueAt(int i, double mx) {
        int tx = cx() + 8, tw = leftW() - 16;
        double frac = Math.max(0, Math.min(1, (mx - tx) / tw));
        double min = sliders[i].min(), max = maxOf(i), step = sliders[i].step();
        double v = min + frac * (max - min);
        return Math.max(min, Math.min(max, Math.round(v / step) * step));
    }

    @Override
    protected boolean onClick(double mx, double my, int button) {
        if (in(mx, my, width / 2 - 60, height - 25, 120, 18)) { Sounds.click(); close(); return true; }
        int cx = cx(), lw = leftW(), top = top(), sy = Math.round(top - scroll);
        if (my < top - 2 || my > height - 30) return false;
        for (int i = 0; i < sliders.length; i++) {
            int y = sy + i * SROW;
            if (in(mx, my, cx, y, lw, SROW - 3)) {
                dragging = i; pendingValue = valueAt(i, mx);
                if (!sliders[i].applyOnRelease()) set(i, pendingValue);
                return true;
            }
        }
        int ty0 = sy + sliders.length * SROW + 8, tgw = (lw - 6) / 2;
        for (int i = 0; i < toggles.length; i++) {
            int x = cx + (i % 2) * (tgw + 6), y = ty0 + (i / 2) * 24;
            if (in(mx, my, x, y, tgw, 20)) { flip(i); Sounds.toggle(toggleOn(i)); return true; }
        }
        int rx = cx + lw + 12, rw = cw() - lw - 12, tw2 = (rw - 6) / 2;
        List<Tile> ts = tiles();
        for (int i = 0; i < ts.size(); i++) {
            int x = rx + (i % 2) * (tw2 + 6), y = sy + (i / 2) * 40;
            if (in(mx, my, x, y, tw2, 36)) { Sounds.click(); client.options.write(); client.setScreen(ts.get(i).open().get()); return true; }
        }
        return false;
    }
    @Override protected boolean onDrag(double mx, double my, int button) {
        if (dragging < 0) return false;
        pendingValue = valueAt(dragging, mx);
        if (!sliders[dragging].applyOnRelease()) set(dragging, pendingValue);
        return true;
    }
    @Override protected boolean onRelease(double mx, double my, int button) {
        if (dragging < 0) return false;
        if (sliders[dragging].applyOnRelease() && Math.abs(pendingValue - get(dragging)) > 1e-6) set(dragging, pendingValue);
        Sounds.click();
        dragging = -1;
        return true;
    }
    @Override protected boolean onScroll(double mx, double my, double v) { scrollTarget -= (float) (v * 30); return true; }
    @Override protected boolean onKey(int key, int scan, int mods) { if (key == GLFW.GLFW_KEY_ESCAPE) { close(); return true; } return false; }
}
