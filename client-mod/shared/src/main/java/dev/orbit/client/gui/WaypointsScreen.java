package dev.orbit.client.gui;

import dev.orbit.client.waypoints.Waypoints;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.orbit.client.gui.Render2D.*;

/** Manage this world's waypoints: add at your position, rename, recolour, show/hide, copy coordinates, delete. */
public final class WaypointsScreen extends OrbitBaseScreen {
    private final Screen parent;
    private final long openedAt = System.currentTimeMillis();
    private int editing = -1;
    private String editText = "";
    private float scroll, scrollTarget;
    private final Map<Object, Float> hover = new HashMap<>();
    private Waypoints.Waypoint confirmDelete;
    private long confirmAt, copiedAt;
    private Waypoints.Waypoint copied;
    private long lastFrame = System.nanoTime();
    private float dt = 1 / 60f;
    private static final int ROW = 34;

    public WaypointsScreen(Screen parent) { super(Text.literal("Waypoints")); this.parent = parent; }
    @Override public boolean shouldPause() { return false; }

    private int px() { return (width - pw()) / 2; }
    private int pw() { return Math.min(420, width - 30); }
    private int top() { return 58; }
    private static boolean in(double mx, double my, double x, double y, double w, double h) { return mx >= x && my >= y && mx < x + w && my < y + h; }
    private float hv(Object k, boolean on) { float v = approach(hover.getOrDefault(k, 0f), on ? 1 : 0, 16, dt); hover.put(k, v); return v; }

    // right-side buttons of a row: {x, w} for copy, eye, delete
    private int[][] buttons(int px, int pw) {
        int dw = 20, ew = 54, cw = 40;
        int dx = px + pw - 8 - dw, ex = dx - 4 - ew, cx = ex - 4 - cw;
        return new int[][]{{cx, cw}, {ex, ew}, {dx, dw}};
    }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        long now = System.nanoTime();
        dt = Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        super.render(c, mx, my, delta);
        c.fillGradient(0, 0, width, height, 0xB0050310, 0xD8050310);
        float intro = easeOut((System.currentTimeMillis() - openedAt) / 350f);
        int px = px(), pw = pw(), y = 18;
        bold(c, "Waypoints", px, y, 0xFFFFFFFF, false);
        String key = Waypoints.worldKey();
        List<Waypoints.Waypoint> list = Waypoints.current();
        String dim = Waypoints.dimension();
        text(c, key == null ? "Join a world to use waypoints" : fit(key.replaceFirst("^(server|world):", "") + "  ·  " + list.size() + " waypoint" + (list.size() == 1 ? "" : "s"), pw - 130, false), px, y + 13, MUTED, false);
        SpaceBackground.button(c, px + pw - 110, y, 110, 20, "+ Add here", mx, my, hv("add", in(mx, my, px + pw - 110, y, 110, 20)), true);

        int top = top();
        scroll = approach(scroll, scrollTarget, 16, dt);
        c.enableScissor(0, top, width, height - 30);
        var p = client.player;
        int[][] b = buttons(px, pw);
        for (int i = 0; i < list.size(); i++) {
            Waypoints.Waypoint w = list.get(i);
            float appear = easeOut((System.currentTimeMillis() - openedAt - i * 35) / 300f);
            int ry = Math.round(top + i * (ROW + 5) - scroll + (1 - appear) * 8);
            if (ry > height || ry + ROW < top) continue;
            boolean here = w.dimension.equals(dim);
            float h = hv(w, in(mx, my, px, ry, pw, ROW) && my >= top);
            round(c, px, ry, pw, ROW, 10, withAlpha(0xFFFFFF, (int) ((22 + 22 * h) * appear * intro)));
            // colour swatch (click = next colour)
            float sh = hv("sw" + i, in(mx, my, px + 6, ry + 6, 22, 22));
            disc(c, px + 17, ry + ROW / 2f, 9 + sh, withAlpha(w.color, (int) (50 * appear)));
            disc(c, px + 17, ry + ROW / 2f, 6 + sh * 0.6f, w.visible ? w.color : withAlpha(w.color, 90));
            disc(c, px + 17, ry + ROW / 2f, 2.2f, 0xEEFFFFFF);
            // name (click = rename)
            int nameW = b[0][0] - (px + 34) - 8;
            if (editing == i) {
                round(c, px + 32, ry + 5, nameW, 14, 7, 0x33FFFFFF);
                text(c, fit(editText, nameW - 12, false) + ((System.currentTimeMillis() / 500) % 2 == 0 ? "_" : ""), px + 38, ry + 8, TEXT, false);
            } else bold(c, fit(w.name, nameW, true), px + 34, ry + 7, w.visible ? 0xFFFFFFFF : FAINT, false);
            String sub = w.x + "  " + w.y + "  " + w.z;
            if (!here) sub += "  ·  " + w.dimension.replace("minecraft:", "").replace("the_", "");
            else if (p != null) sub += "  ·  " + WaypointRenderer.formatDist((float) Math.sqrt(p.squaredDistanceTo(w.x + 0.5, w.y, w.z + 0.5)));
            text(c, fit(sub, nameW, false), px + 34, ry + 20, MUTED, false);
            // buttons
            boolean justCopied = w == copied && System.currentTimeMillis() - copiedAt < 1500;
            String[] labels = {justCopied ? "Copied" : "Copy", w.visible ? "Shown" : "Hidden", confirming(w) ? "?" : "×"};
            for (int k = 0; k < 3; k++) {
                boolean bh = in(mx, my, b[k][0], ry + 8, b[k][1], 18);
                float bv = hv("b" + k + ":" + i, bh);
                int bg = k == 2 ? lerpColor(confirming(w) ? 0xCCE5484D : 0x18FFFFFF, 0xCCE5484D, bv)
                        : k == 1 && w.visible ? lerpColor(withAlpha(w.color, 70), withAlpha(w.color, 120), bv) : lerpColor(0x18FFFFFF, 0x38FFFFFF, bv);
                round(c, b[k][0], ry + 8, b[k][1], 18, 9, bg);
                centered(c, labels[k], b[k][0] + b[k][1] / 2f, ry + 13, k == 1 && !w.visible ? MUTED : 0xFFFFFFFF, false);
            }
        }
        c.disableScissor();
        if (list.isEmpty() && key != null) {
            centered(c, "No waypoints yet", width / 2f, top + 30, TEXT, false);
            centered(c, "Press + Add here, or the waypoint key (N) in-game", width / 2f, top + 44, FAINT, false);
        }
        scrollTarget = Math.max(0, Math.min(Math.max(0, list.size() * (ROW + 5) - (height - 30 - top)), scrollTarget));
        centered(c, "Click the colour to change it  ·  click a name to rename  ·  Esc to close", width / 2f, height - 18, FAINT, false);
    }

    private boolean confirming(Waypoints.Waypoint w) { return w == confirmDelete && System.currentTimeMillis() - confirmAt < 3000; }

    @Override
    protected boolean onClick(double mx, double my, int button) {
        int px = px(), pw = pw(), top = top();
        commitEdit();
        if (in(mx, my, px + pw - 110, 18, 110, 20)) { if (Waypoints.addHere() != null) Sounds.success(); return true; }
        List<Waypoints.Waypoint> list = Waypoints.current();
        int[][] b = buttons(px, pw);
        if (my < top) return false;
        for (int i = 0; i < list.size(); i++) {
            int ry = Math.round(top + i * (ROW + 5) - scroll);
            if (!in(mx, my, px, ry, pw, ROW)) continue;
            Waypoints.Waypoint w = list.get(i);
            if (in(mx, my, b[2][0], ry + 8, b[2][1], 18)) {
                if (confirming(w)) { list.remove(i); Waypoints.save(); Sounds.toggle(false); confirmDelete = null; }
                else { confirmDelete = w; confirmAt = System.currentTimeMillis(); Sounds.click(); }
            } else if (in(mx, my, b[1][0], ry + 8, b[1][1], 18)) { w.visible = !w.visible; Waypoints.save(); Sounds.toggle(w.visible); }
            else if (in(mx, my, b[0][0], ry + 8, b[0][1], 18)) { TextKeys.copy(w.x + " " + w.y + " " + w.z); copied = w; copiedAt = System.currentTimeMillis(); Sounds.click(); }
            else if (in(mx, my, px + 6, ry + 6, 22, 22)) {
                int idx = 0; for (int k = 0; k < Waypoints.COLORS.length; k++) if (Waypoints.COLORS[k] == w.color) idx = k;
                w.color = Waypoints.COLORS[(idx + 1) % Waypoints.COLORS.length]; Waypoints.save(); Sounds.click();
            } else { editing = i; editText = w.name; Sounds.click(); }
            return true;
        }
        return false;
    }

    private void commitEdit() {
        List<Waypoints.Waypoint> list = Waypoints.current();
        if (editing >= 0 && editing < list.size() && !editText.isBlank()) { list.get(editing).name = editText.trim(); Waypoints.save(); }
        editing = -1;
    }

    @Override protected boolean onScroll(double mx, double my, double v) { scrollTarget -= (float) (v * 30); return true; }
    @Override
    protected boolean onKey(int key, int scan, int mods) {
        if (editing >= 0) {
            String e = TextKeys.edit(editText, key, mods, 32);
            if (e != null) { editText = e; return true; }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { commitEdit(); Sounds.success(); return true; }
            if (key == GLFW.GLFW_KEY_ESCAPE) { editing = -1; return true; }
            if (key == GLFW.GLFW_KEY_BACKSPACE && !editText.isEmpty()) { editText = editText.substring(0, editText.length() - 1); return true; }
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) { close(); return true; }
        return false;
    }
    @Override protected boolean onChar(char chr) {
        if (editing < 0) return false;
        if (TextKeys.ctrl(0)) return true;
        if (chr >= ' ' && editText.length() < 32) editText += chr;
        return true;
    }
    @Override public void close() { commitEdit(); client.setScreen(parent); }
}
