package dev.orbit.client.gui;

import dev.orbit.client.OrbitClient;
import dev.orbit.client.module.NewModules;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import static dev.orbit.client.gui.Render2D.*;

/** Pixel painter for the custom crosshair: left click/drag paints, right click/drag erases. */
public final class CrosshairEditorScreen extends OrbitBaseScreen {
    private final Screen parent;
    private final NewModules.Crosshair mod;
    private Boolean painting; // null = not dragging, true = paint, false = erase

    public CrosshairEditorScreen(Screen parent, NewModules.Crosshair mod) {
        super(Text.literal("Crosshair painter"));
        this.parent = parent;
        this.mod = mod;
    }

    private int cell() { return Math.max(4, Math.min(12, (height - 90) / NewModules.Crosshair.GRID)); }
    private int gx() { return width / 2 - NewModules.Crosshair.GRID * cell() / 2 - 60; }
    private int gy() { return (height - NewModules.Crosshair.GRID * cell()) / 2 + 6; }
    private static final String[] BTN = {"Clear", "Fill cross", "Reset", "Done"};

    @Override
    public void renderBackground(DrawContext c, int mx, int my, float delta) {
        c.fillGradient(0, 0, width, height, 0xE005030D, 0xF00B0718);
    }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        super.render(c, mx, my, delta);
        int n = NewModules.Crosshair.GRID, s = cell(), x0 = gx(), y0 = gy(), acc = accent();
        centered(c, "Paint your crosshair", width / 2f, 14, 0xFFFFFFFF, true);
        centered(c, "Left click paints  ·  right click erases  ·  drag to draw", width / 2f, 26, MUTED, false);

        round(c, x0 - 6, y0 - 6, n * s + 12, n * s + 12, 8, 0x40FFFFFF);
        round(c, x0 - 5, y0 - 5, n * s + 10, n * s + 10, 7, 0xFF0E0B1A);
        int hx = (mx - x0) / s, hy = (my - y0) / s;
        boolean inGrid = mx >= x0 && my >= y0 && hx < n && hy < n;
        for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) {
            int px = x0 + x * s, py = y0 + y * s;
            boolean on = mod.pixels.get(x, y), centre = x == n / 2 && y == n / 2;
            int col = on ? mod.color.argb() : (x + y) % 2 == 0 ? 0xFF1B1636 : 0xFF17122D;
            if (!on && centre) col = 0xFF2B2450;
            c.fill(px, py, px + s - 1, py + s - 1, col);
            if (inGrid && x == hx && y == hy) outline(c, px - 1, py - 1, s + 1, s + 1, acc);
        }

        // preview panel
        int px = x0 + n * s + 30, py = y0;
        round(c, px, py, 110, 110, 8, 0x40FFFFFF);
        round(c, px + 1, py + 1, 108, 108, 7, 0xFF5E7FA8); // sky-ish backdrop so the crosshair reads
        c.fill(px + 1, py + 70, px + 109, py + 109, 0xFF4E7A36);
        for (int y = 0; y < n; y++) for (int x = 0; x < n; x++)
            if (mod.pixels.get(x, y)) c.fill(px + 55 - n / 2 + x, py + 30 - n / 2 + y, px + 56 - n / 2 + x, py + 31 - n / 2 + y, mod.color.argb());
        for (int y = 0; y < n; y++) for (int x = 0; x < n; x++)
            if (mod.pixels.get(x, y)) c.fill(px + 55 - n * 3 / 2 + x * 3, py + 80 - n * 3 / 2 + y * 3 + 8, px + 58 - n * 3 / 2 + x * 3, py + 83 - n * 3 / 2 + y * 3 + 8, mod.color.argb());
        text(c, "1x", px + 4, py + 4, 0xFFFFFFFF, true);
        text(c, "3x", px + 4, py + 60, 0xFFFFFFFF, true);

        for (int i = 0; i < BTN.length; i++) {
            int bx = px, by = py + 120 + i * 22;
            boolean hov = mx >= bx && my >= by && mx < bx + 110 && my < by + 18;
            boolean primary = i == BTN.length - 1;
            round(c, bx, by, 110, 18, 9, primary ? (hov ? lerpColor(acc, 0xFFFFFFFF, 0.25f) : acc) : hov ? 0x77FFFFFF : 0x44FFFFFF);
            if (!primary) round(c, bx + 1, by + 1, 108, 16, 8, 0xFF120E24);
            centered(c, BTN[i], bx + 55, by + 5, primary ? 0xFF140A28 : TEXT, false);
        }
    }

    private void paintAt(double mx, double my) {
        int s = cell(), x = (int) ((mx - gx()) / s), y = (int) ((my - gy()) / s), n = NewModules.Crosshair.GRID;
        if (mx < gx() || my < gy() || x >= n || y >= n || painting == null) return;
        mod.pixels.set(x, y, painting);
    }

    @Override
    protected boolean onClick(double mx, double my, int button) {
        int n = NewModules.Crosshair.GRID, px = gx() + n * cell() + 30, py = gy();
        for (int i = 0; i < BTN.length; i++) {
            int by = py + 120 + i * 22;
            if (mx >= px && my >= by && mx < px + 110 && my < by + 18) {
                switch (i) {
                    case 0 -> mod.pixels.clear();
                    case 1 -> { mod.pixels.clear(); for (int k = 0; k < n; k++) { mod.pixels.set(n / 2, k, true); mod.pixels.set(k, n / 2, true); } }
                    case 2 -> mod.pixels.reset();
                    default -> close();
                }
                return true;
            }
        }
        painting = button == 0 ? Boolean.TRUE : button == 1 ? Boolean.FALSE : null;
        paintAt(mx, my);
        return true;
    }
    @Override protected boolean onDrag(double mx, double my, int button) { paintAt(mx, my); return true; }
    @Override protected boolean onRelease(double mx, double my, int button) { painting = null; return true; }
    @Override protected boolean onKey(int key, int scan, int mods) { if (key == GLFW.GLFW_KEY_ESCAPE) { close(); return true; } return false; }

    @Override public void close() {
        mod.style.set("Custom");
        OrbitClient.modules().save();
        client.setScreen(parent);
    }
}
