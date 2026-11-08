package dev.orbit.client.gui;

import dev.orbit.client.OrbitClient;
import dev.orbit.client.module.HudModule;
import dev.orbit.client.module.setting.Setting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

import static dev.orbit.client.gui.Render2D.*;

/**
 * HUD Designer: drag elements, snap them to the screen and to each other (guide lines show how), nudge with the
 * arrow keys, resize with the scroll wheel, switch elements on/off in the list, and style the selected one.
 */
public final class HudEditorScreen extends OrbitBaseScreen {
    private static final int SNAP = 5;
    private final Screen parent;
    private final long openedAt = System.currentTimeMillis();
    private HudModule dragging, selected;
    private double offX, offY;
    private final List<float[]> guides = new ArrayList<>(); // {x1, y1, x2, y2}
    private static boolean grid = true, listOpen = true;
    private float listScroll;

    public HudEditorScreen(Screen parent) { super(Text.literal("Orbit HUD designer")); this.parent = parent; }
    @Override public boolean shouldPause() { return false; }

    @Override
    public void renderBackground(DrawContext c, int mouseX, int mouseY, float delta) {
        float t = easeOut((System.currentTimeMillis() - openedAt) / 300f);
        c.fill(0, 0, width, height, withAlpha(0x000000, (int) (70 * t)));
        if (grid) {
            int g = withAlpha(0xFFFFFF, (int) (10 * t));
            for (int x = 0; x < width; x += 16) c.fill(x, 0, x + 1, height, g);
            for (int y = 0; y < height; y += 16) c.fill(0, y, width, y + 1, g);
        }
    }

    private List<HudModule> all() { return OrbitClient.modules().hud(); }
    private List<HudModule> elements() { return all().stream().filter(HudModule::isEnabled).toList(); }
    private int ew(HudModule h) { return Math.round(h.width(true) * h.scaleF()); }
    private int eh(HudModule h) { return Math.round(h.height(true) * h.scaleF()); }
    private HudModule at(double mx, double my) {
        List<HudModule> l = elements();
        for (int i = l.size() - 1; i >= 0; i--) {
            HudModule h = l.get(i);
            int x = h.screenX(width, true), y = h.screenY(height, true);
            if (mx >= x && my >= y && mx < x + ew(h) && my < y + eh(h)) return h;
        }
        return null;
    }

    // ------------------------------------------------------------------ side list
    private int listW() { return 118; }
    private static final int LROW = 15;
    private boolean overList(double mx, double my) { return listOpen && mx < listW() + 8 && my > 24 && my < height - 8; }

    // ------------------------------------------------------------------ style bar for the selected element
    private record Toggle(String label, Setting.Bool s) {}
    private List<Toggle> toggles(HudModule h) {
        return List.of(new Toggle("Background", h.background), new Toggle("Rounded", h.rounded), new Toggle("Border", h.border), new Toggle("Shadow", h.shadow), new Toggle("Rainbow", h.chroma));
    }
    private int barY() { return height - 28; }
    private int barW(HudModule h) { int w = 12; for (Toggle t : toggles(h)) w += width(t.label()) + 16 + 4; return w + 140; }
    private int barX(HudModule h) { return (width - barW(h)) / 2; }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        super.render(c, mx, my, delta);
        int acc = accent();
        for (float[] g : guides) c.fill(Math.round(g[0]), Math.round(g[1]), Math.round(Math.max(g[0] + 1, g[2])), Math.round(Math.max(g[1] + 1, g[3])), withAlpha(acc, 180));
        HudModule hover = dragging != null ? dragging : at(mx, my);
        for (HudModule h : elements()) {
            h.render(c, width, height, true);
            int x = h.screenX(width, true), y = h.screenY(height, true), w = ew(h), hh = eh(h);
            boolean sel = h == selected;
            outline(c, x - 1, y - 1, w + 2, hh + 2, sel ? acc : h == hover ? lerpColor(acc, 0xFFFFFFFF, 0.4f) : withAlpha(0xFFFFFF, 40));
            if (h == hover || sel) {
                String label = h.name + "  " + Math.round(h.scaleF() * 100) + "%";
                int lw = width(label) + 8, ly = y - 13 < 2 ? y + hh + 3 : y - 13;
                round(c, x, ly, lw, 11, 3, withAlpha(acc, 230));
                text(c, label, x + 4, ly + 2, 0xFF140A28, false);
            }
        }
        // element list
        if (listOpen) {
            int lw = listW(), top = 26;
            round(c, 6, top - 4, lw, height - top - 4, 10, 0xE00E0B1E);
            bold(c, "Elements", 14, top + 2, 0xFFFFFFFF, false);
            c.enableScissor(6, top + 14, 6 + lw, height - 10);
            int y = Math.round(top + 16 - listScroll);
            for (HudModule h : all()) {
                boolean on = h.isEnabled(), hv = mx >= 6 && mx < 6 + lw && my >= y && my < y + LROW;
                if (hv || h == selected) round(c, 9, y, lw - 6, LROW - 1, 5, h == selected ? withAlpha(acc, 90) : 0x18FFFFFF);
                round(c, 13, y + 4, 7, 7, 2, on ? acc : 0x40FFFFFF);
                text(c, fit(h.name, lw - 26, false), 24, y + 4, on ? TEXT : FAINT, false);
                y += LROW;
            }
            c.disableScissor();
            listScroll = Math.max(0, Math.min(listScroll, Math.max(0, all().size() * LROW - (height - top - 30))));
        }
        // style bar or help
        if (selected != null && selected.isEnabled()) {
            int bx = barX(selected), by = barY(), bw = barW(selected);
            round(c, bx, by, bw, 22, 11, 0xEE0E0B1E);
            int x = bx + 8;
            for (Toggle t : toggles(selected)) {
                int w = width(t.label()) + 16;
                boolean on = t.s().on(), hv = mx >= x && mx < x + w && my >= by + 3 && my < by + 19;
                round(c, x, by + 3, w, 16, 8, on ? withAlpha(acc, hv ? 230 : 170) : hv ? 0x30FFFFFF : 0x16FFFFFF);
                text(c, t.label(), x + 8, by + 7, 0xFFFFFFFF, false);
                x += w + 4;
            }
            String op = "Opacity " + Math.round(selected.bgOpacity.get()) + "%";
            text(c, "−", x + 4, by + 7, TEXT, false); text(c, op, x + 14, by + 7, MUTED, false); text(c, "+", x + 18 + width(op), by + 7, TEXT, false);
            int sx = bx + bw - 60;
            round(c, sx, by + 3, 52, 16, 8, mx >= sx && mx < sx + 52 && my >= by + 3 && my < by + 19 ? withAlpha(acc, 230) : 0x30FFFFFF);
            centered(c, "Settings", sx + 26, by + 7, 0xFFFFFFFF, false);
        } else {
            String hint = "Drag · scroll to resize · click to select (arrows nudge, H hides, R resets) · G grid · Tab list · Esc done";
            int tw = Math.min(width - 20, width(hint) + 20);
            round(c, (width - tw) / 2f, barY() + 2, tw, 18, 9, 0xE00E0B1E);
            centered(c, fit(hint, tw - 16, false), width / 2f, barY() + 7, MUTED, false);
        }
        int dw = 54, dx = width - dw - 8;
        boolean dh = mx >= dx && mx < dx + dw && my >= 6 && my < 24;
        round(c, dx, 6, dw, 18, 9, dh ? lerpColor(acc, 0xFFFFFFFF, 0.2f) : acc);
        centered(c, "Done", dx + dw / 2f, 11, 0xFF140A28, false);
    }

    @Override
    protected boolean onClick(double mx, double my, int button) {
        int dw = 54, dx = width - dw - 8;
        if (mx >= dx && mx < dx + dw && my >= 6 && my < 24) { close(); return true; }
        // style bar
        if (selected != null && selected.isEnabled() && my >= barY() && my < barY() + 22) {
            int bx = barX(selected), by = barY(), x = bx + 8;
            for (Toggle t : toggles(selected)) {
                int w = width(t.label()) + 16;
                if (mx >= x && mx < x + w) { t.s().toggle(); Sounds.toggle(t.s().on()); return true; }
                x += w + 4;
            }
            String op = "Opacity " + Math.round(selected.bgOpacity.get()) + "%";
            if (mx >= x && mx < x + 12) { selected.bgOpacity.set(selected.bgOpacity.get() - 10); return true; }
            if (mx >= x + 14 + width(op) && mx < x + 28 + width(op)) { selected.bgOpacity.set(selected.bgOpacity.get() + 10); return true; }
            int sx = bx + barW(selected) - 60;
            if (mx >= sx && mx < sx + 52) { OrbitScreen s = parent instanceof OrbitScreen ns ? ns : new OrbitScreen(null); s.open(selected); client.setScreen(s); return true; }
            return true;
        }
        // element list
        if (overList(mx, my)) {
            int y = Math.round(26 + 16 - listScroll);
            for (HudModule h : all()) {
                if (my >= y && my < y + LROW) {
                    if (mx < 22) { h.setEnabled(!h.isEnabled()); Sounds.toggle(h.isEnabled()); }
                    else { if (!h.isEnabled()) h.setEnabled(true); selected = h; Sounds.click(); }
                    return true;
                }
                y += LROW;
            }
            return true;
        }
        HudModule h = at(mx, my);
        if (h == null) { selected = null; return false; }
        if (button == 1) { OrbitScreen s = parent instanceof OrbitScreen ns ? ns : new OrbitScreen(null); s.open(h); client.setScreen(s); return true; }
        dragging = h; selected = h;
        offX = mx - h.screenX(width, true);
        offY = my - h.screenY(height, true);
        return true;
    }

    @Override
    protected boolean onDrag(double mx, double my, int button) {
        if (dragging == null) return false;
        double w = ew(dragging), h = eh(dragging);
        double nx = mx - offX, ny = my - offY;
        guides.clear();
        // snap X: screen edges/centre, then other elements' left/right/centre
        double[] xs = {2, width / 2.0 - w / 2, width - w - 2};
        double bestX = Double.NaN, bestDX = SNAP; float gx = -1;
        for (int i = 0; i < xs.length; i++) if (Math.abs(nx - xs[i]) < bestDX) { bestDX = Math.abs(nx - xs[i]); bestX = xs[i]; gx = i == 1 ? width / 2f : -1; }
        double bestY = Double.NaN, bestDY = SNAP; float gy = -1;
        double[] ys = {2, height / 2.0 - h / 2, height - h - 2};
        for (int i = 0; i < ys.length; i++) if (Math.abs(ny - ys[i]) < bestDY) { bestDY = Math.abs(ny - ys[i]); bestY = ys[i]; gy = i == 1 ? height / 2f : -1; }
        for (HudModule o : elements()) {
            if (o == dragging) continue;
            int ox = o.screenX(width, true), oy = o.screenY(height, true), ow = ew(o), oh = eh(o);
            double[][] cx = {{ox, ox}, {ox + ow - w, ox + ow}, {ox + ow + 2, ox + ow}, {ox - w - 2, ox}, {ox + ow / 2.0 - w / 2, ox + ow / 2.0}};
            for (double[] k : cx) if (Math.abs(nx - k[0]) < bestDX) { bestDX = Math.abs(nx - k[0]); bestX = k[0]; gx = (float) k[1]; }
            double[][] cy = {{oy, oy}, {oy + oh - h, oy + oh}, {oy + oh + 2, oy + oh}, {oy - h - 2, oy}, {oy + oh / 2.0 - h / 2, oy + oh / 2.0}};
            for (double[] k : cy) if (Math.abs(ny - k[0]) < bestDY) { bestDY = Math.abs(ny - k[0]); bestY = k[0]; gy = (float) k[1]; }
        }
        if (!Double.isNaN(bestX)) { nx = bestX; if (gx >= 0) guides.add(new float[]{gx, 0, gx + 1, height}); }
        if (!Double.isNaN(bestY)) { ny = bestY; if (gy >= 0) guides.add(new float[]{0, gy, width, gy + 1}); }
        nx = Math.max(0, Math.min(width - w, nx)); ny = Math.max(0, Math.min(height - h, ny));
        dragging.setScreenPos(nx, ny, width, height, true);
        return true;
    }

    @Override protected boolean onRelease(double mx, double my, int button) { dragging = null; guides.clear(); return false; }

    @Override
    protected boolean onScroll(double mx, double my, double v) {
        if (overList(mx, my)) { listScroll -= (float) (v * 20); return true; }
        HudModule e = at(mx, my);
        if (e == null) e = selected;
        if (e == null) return false;
        int x = e.screenX(width, true), y = e.screenY(height, true);
        e.scale.set(e.scale.get() + (v > 0 ? 0.05 : -0.05));
        e.setScreenPos(x, y, width, height, true);
        return true;
    }

    @Override
    protected boolean onKey(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { if (selected != null) { selected = null; return true; } close(); return true; }
        if (key == GLFW.GLFW_KEY_G) { grid = !grid; return true; }
        if (key == GLFW.GLFW_KEY_TAB) { listOpen = !listOpen; return true; }
        if (selected == null) return false;
        int step = (mods & GLFW.GLFW_MOD_SHIFT) != 0 ? 10 : 1, dx = 0, dy = 0;
        switch (key) {
            case GLFW.GLFW_KEY_LEFT -> dx = -step;
            case GLFW.GLFW_KEY_RIGHT -> dx = step;
            case GLFW.GLFW_KEY_UP -> dy = -step;
            case GLFW.GLFW_KEY_DOWN -> dy = step;
            case GLFW.GLFW_KEY_H, GLFW.GLFW_KEY_DELETE -> { selected.setEnabled(false); selected = null; Sounds.toggle(false); return true; }
            case GLFW.GLFW_KEY_R -> { int x = selected.screenX(width, true), y = selected.screenY(height, true); selected.scale.set(1.0); selected.setScreenPos(x, y, width, height, true); return true; }
            default -> { return false; }
        }
        int x = selected.screenX(width, true) + dx, y = selected.screenY(height, true) + dy;
        selected.setScreenPos(Math.max(0, Math.min(width - ew(selected), x)), Math.max(0, Math.min(height - eh(selected), y)), width, height, true);
        return true;
    }

    @Override public void close() { OrbitClient.modules().save(); client.setScreen(parent); }
}
