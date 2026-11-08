package dev.orbit.client.gui;

import dev.orbit.client.module.Configs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.text.SimpleDateFormat;
import java.util.*;

import static dev.orbit.client.gui.Render2D.*;

/** Save your whole setup (every mod + its settings) as a config, load one, share it as text. */
public final class ConfigsScreen extends OrbitBaseScreen {
    private final Screen parent;
    private final long openedAt = System.currentTimeMillis();
    private final TextField name = new TextField(32, ch -> Character.isLetterOrDigit(ch) || ch == ' ' || ch == '_' || ch == '-');
    private boolean focus = true;
    private List<Configs.Entry> list = Configs.list();
    private String status = "";
    private int statusCol = MUTED;
    private long statusAt;
    private String confirmDelete = "", confirmLoad = "";
    private long confirmAt;
    private float scroll, scrollTarget;
    private final Map<Object, Float> hover = new HashMap<>();
    private long lastFrame = System.nanoTime();
    private float dt = 1 / 60f;
    private static final int ROW = 34;

    public ConfigsScreen(Screen parent) { super(Text.literal("Configs")); this.parent = parent; }
    @Override public void close() { client.setScreen(parent); }
    @Override public boolean shouldPause() { return false; }

    private int pw() { return Math.min(440, width - 30); }
    private int px() { return (width - pw()) / 2; }
    private static boolean in(double mx, double my, double x, double y, double w, double h) { return mx >= x && my >= y && mx < x + w && my < y + h; }
    private float hv(Object k, boolean on) { float v = approach(hover.getOrDefault(k, 0f), on ? 1 : 0, 16, dt); hover.put(k, v); return v; }
    private void say(String s, boolean ok) { status = s; statusCol = ok ? 0xFF8CF5B4 : 0xFFFF8C8C; statusAt = System.currentTimeMillis(); }
    private boolean confirming(String key, String v) { return v.equals(key) && System.currentTimeMillis() - confirmAt < 3000; }

    // row buttons: load, share, delete
    private int[][] rowButtons(int px, int pw) {
        int dw = 22, sw = 50, lw = 52;
        int dx = px + pw - 8 - dw, sx = dx - 4 - sw, lx = sx - 4 - lw;
        return new int[][]{{lx, lw}, {sx, sw}, {dx, dw}};
    }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        long now = System.nanoTime();
        dt = Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        super.render(c, mx, my, delta);
        c.fillGradient(0, 0, width, height, 0xC0050310, 0xE0050310);
        float intro = easeOut((System.currentTimeMillis() - openedAt) / 350f);
        int px = px(), pw = pw(), y = Math.round(18 + (1 - intro) * 8);
        bold(c, "Configs", px, y, 0xFFFFFFFF, false);
        text(c, fit("Save every mod and setting as a config, switch between them, or share one as text", pw, false), px, y + 13, MUTED, false);

        // save row
        int sy = y + 32;
        name.render(c, px, sy, pw - 196, 20, focus, "Name your config…", focus ? 0x33FFFFFF : 0x1EFFFFFF);
        SpaceBackground.button(c, px + pw - 190, sy, 90, 20, "Save current", mx, my, hv("save", in(mx, my, px + pw - 190, sy, 90, 20)), true);
        SpaceBackground.button(c, px + pw - 96, sy, 96, 20, "Paste shared", mx, my, hv("paste", in(mx, my, px + pw - 96, sy, 96, 20)), false);

        int top = sy + 32;
        scroll = approach(scroll, scrollTarget, 16, dt);
        c.enableScissor(0, top, width, height - 26);
        SimpleDateFormat fmt = new SimpleDateFormat("d MMM, HH:mm");
        int[][] b = rowButtons(px, pw);
        for (int i = 0; i < list.size(); i++) {
            Configs.Entry e = list.get(i);
            float appear = easeOut((System.currentTimeMillis() - openedAt - i * 35) / 300f);
            int ry = Math.round(top + i * (ROW + 5) - scroll + (1 - appear) * 8);
            if (ry > height || ry + ROW < top) continue;
            float h = hv(e.name(), in(mx, my, px, ry, pw, ROW) && my >= top);
            round(c, px, ry, pw, ROW, 10, withAlpha(0xFFFFFF, (int) ((22 + 22 * h) * appear)));
            disc(c, px + 16, ry + ROW / 2f, 7, withAlpha(accent(), 200));
            centered(c, e.name().substring(0, 1).toUpperCase(), px + 16, ry + ROW / 2f - 4, 0xFF140A28, false);
            bold(c, fit(e.name(), b[0][0] - px - 40, true), px + 30, ry + 7, 0xFFFFFFFF, false);
            text(c, e.enabled() + " mods on  ·  saved " + fmt.format(new Date(e.modified())), px + 30, ry + 19, MUTED, false);
            String[] labels = {confirming(confirmLoad, e.name()) ? "Sure?" : "Load", "Share", confirming(confirmDelete, e.name()) ? "?" : "×"};
            for (int k = 0; k < 3; k++) {
                float bv = hv("b" + k + e.name(), in(mx, my, b[k][0], ry + 8, b[k][1], 18));
                int bg = k == 0 ? lerpColor(withAlpha(accent(), 150), accent(), bv) : k == 2 ? lerpColor(0x18FFFFFF, 0xCCE5484D, Math.max(bv, confirming(confirmDelete, e.name()) ? 1 : 0)) : lerpColor(0x18FFFFFF, 0x38FFFFFF, bv);
                round(c, b[k][0], ry + 8, b[k][1], 18, 9, bg);
                centered(c, labels[k], b[k][0] + b[k][1] / 2f, ry + 13, k == 0 ? 0xFF140A28 : 0xFFFFFFFF, false);
            }
        }
        c.disableScissor();
        if (list.isEmpty()) {
            centered(c, "No configs yet", width / 2f, top + 30, TEXT, false);
            centered(c, "Type a name and press Save current — e.g. \"PvP\", \"Building\", \"Recording\"", width / 2f, top + 44, FAINT, false);
        }
        scrollTarget = Math.max(0, Math.min(Math.max(0, list.size() * (ROW + 5) - (height - 26 - top)), scrollTarget));
        if (!status.isEmpty() && System.currentTimeMillis() - statusAt < 4000) {
            int w = width(status) + 20;
            round(c, width / 2f - w / 2f, height - 24, w, 16, 8, 0xE0120E24);
            centered(c, status, width / 2f, height - 20, statusCol, false);
        } else centered(c, "Loading a config replaces your current settings — save them first if you want to keep them", width / 2f, height - 18, FAINT, false);
    }

    private void saveCurrent() {
        String n = Configs.clean(name.text);
        if (n.isEmpty()) { say("Type a name first", false); focus = true; return; }
        if (Configs.save(n)) { Sounds.success(); say("Saved \"" + n + "\"", true); name.set(""); list = Configs.list(); }
        else say("Couldn't save", false);
    }

    @Override
    protected boolean onClick(double mx, double my, int button) {
        int px = px(), pw = pw(), y = 18, sy = y + 32, top = sy + 32;
        if (name.contains(mx, my)) { focus = true; name.click(mx, 0); return true; }
        focus = false;
        if (in(mx, my, px + pw - 190, sy, 90, 20)) { saveCurrent(); return true; }
        if (in(mx, my, px + pw - 96, sy, 96, 20)) {
            String n = Configs.importShared(TextKeys.clipboard());
            if (n == null) say("Clipboard doesn't contain an Orbit config (orbit:…)", false);
            else { Sounds.success(); say("Imported \"" + n + "\" — press Load to use it", true); list = Configs.list(); }
            return true;
        }
        if (my < top) return false;
        int[][] b = rowButtons(px, pw);
        for (int i = 0; i < list.size(); i++) {
            int ry = Math.round(top + i * (ROW + 5) - scroll);
            if (!in(mx, my, px, ry, pw, ROW)) continue;
            Configs.Entry e = list.get(i);
            if (in(mx, my, b[0][0], ry + 8, b[0][1], 18)) {
                if (!confirming(confirmLoad, e.name())) { confirmLoad = e.name(); confirmAt = System.currentTimeMillis(); Sounds.click(); return true; }
                confirmLoad = "";
                if (Configs.load(e.name())) { Sounds.success(); say("Loaded \"" + e.name() + "\"", true); } else say("Couldn't load", false);
            } else if (in(mx, my, b[1][0], ry + 8, b[1][1], 18)) {
                String s = Configs.share(e.name());
                if (s != null) { TextKeys.copy(s); Sounds.click(); say("Copied — send it to a friend, they press Paste shared", true); }
            } else if (in(mx, my, b[2][0], ry + 8, b[2][1], 18)) {
                if (!confirming(confirmDelete, e.name())) { confirmDelete = e.name(); confirmAt = System.currentTimeMillis(); Sounds.click(); return true; }
                Configs.delete(e.name()); confirmDelete = ""; list = Configs.list(); Sounds.toggle(false); say("Deleted \"" + e.name() + "\"", true);
            } else { name.set(e.name()); focus = true; }
            return true;
        }
        return false;
    }
    @Override protected boolean onDrag(double mx, double my, int button) { if (focus) name.drag(mx); return false; }
    @Override protected boolean onRelease(double mx, double my, int button) { name.release(); return false; }
    @Override protected boolean onScroll(double mx, double my, double v) { scrollTarget -= (float) (v * 30); return true; }
    @Override protected boolean onKey(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { if (focus && !name.text.isEmpty()) { name.set(""); return true; } close(); return true; }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { saveCurrent(); return true; }
        if (focus && name.key(key, mods)) return true;
        return false;
    }
    @Override protected boolean onChar(char chr) { focus = true; return name.chr(chr) || true; }
}
