package dev.orbit.client.gui;

import dev.orbit.client.OrbitClient;
import dev.orbit.client.compat.Compat;
import dev.orbit.client.module.HudModule;
import dev.orbit.client.module.Module;
import dev.orbit.client.module.setting.Setting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.orbit.client.gui.Render2D.*;

/** The Right-Shift menu: full-screen, category pills on top, a clean card grid, per-module settings. */
public final class OrbitScreen extends OrbitBaseScreen {
    private static final Module.Category[] CATS = Module.Category.values();
    private static final Item[] CAT_ICONS = {Items.ENDER_EYE, Items.DIAMOND_SWORD, Items.CLOCK, Items.PLAYER_HEAD, Items.COMPARATOR};
    private static final int[] SWATCHES = {0xFFFFFFFF, 0xFFB8C2CC, 0xFF000000, 0xFFFF5D6C, 0xFFFFA24D, 0xFFFFD84D, 0xFF4DFF88, 0xFF34F5A5, 0xFF4DD8FF, 0xFF4D7BFF, 0xFFB18CFF, 0xFFFF7BD0};
    private enum Filter { ALL, ON, FAV }
    private static Module.Category category = Module.Category.RENDER;
    private static Filter filter = Filter.ALL;

    private final long openedAt = System.currentTimeMillis();
    private Module selected;
    private String search = "";
    private float scroll, scrollTarget;
    private Setting.Slider dragging;
    private int dragX, dragW;
    private Setting.Key capturing;
    private long lastFrame = System.nanoTime();
    private final Map<Object, Float> anim = new HashMap<>();

    public OrbitScreen(Module open) {
        super(Text.literal("Orbit"));
        if (open != null) { selected = open; category = open.category; }
        Sounds.open();
    }

    @Override public boolean shouldPause() { return false; }

    @Override
    public void renderBackground(DrawContext c, int mouseX, int mouseY, float delta) {
        if (OrbitClient.modules().ui().blur.on()) super.renderBackground(c, mouseX, mouseY, delta);
        float t = easeOut((System.currentTimeMillis() - openedAt) / 260f);
        c.fillGradient(0, 0, width, height, withAlpha(0x05030D, (int) (150 * t)), withAlpha(0x0B0718, (int) (205 * t)));
    }

    private float a(Object key, float target, float speed, float dt) {
        float v = anim.getOrDefault(key, target == 1 ? 0f : target);
        v = approach(v, target, speed, dt);
        anim.put(key, v);
        return v;
    }

    // ------------------------------------------------------------------------------ layout
    private int left() { return Math.max(12, (width - contentW()) / 2); }
    private int contentW() { return Math.min(width - 24, 760); }
    private int cols() { return contentW() >= 520 ? 3 : 2; }
    private int cardW() { return (contentW() - (cols() - 1) * 8) / cols(); }
    private static final int CARD_H = 44, TOP = 40, GRID_Y = 92;
    private int gridBottom() { return height - 20; }

    private List<Module> visible() {
        List<Module> out = new ArrayList<>();
        for (Module m : OrbitClient.modules().all()) {
            boolean searching = !search.isEmpty();
            if (!searching && m.category != category) continue;
            if (searching && !(m.name + " " + m.description).toLowerCase().contains(search.toLowerCase())) continue;
            if (filter == Filter.ON && !m.isEnabled()) continue;
            if (filter == Filter.FAV && !m.isFavourite()) continue;
            out.add(m);
        }
        return out;
    }

    private String countLabel() {
        List<Module> mods = visible();
        return "● " + mods.stream().filter(Module::isEnabled).count() + " of " + mods.size() + " active";
    }

    // ------------------------------------------------------------------------------ render
    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        super.render(c, mx, my, delta);
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        float open = easeOut((System.currentTimeMillis() - openedAt) / 300f);
        int acc = accent();

        Compat.push(c);
        Compat.translate(c, 0, Render2D.snap((1 - open) * 8));
        renderTopBar(c, mx, my, dt, acc);
        if (selected == null) renderGrid(c, mx, my, dt, acc); else renderSettings(c, mx, my, dt, acc);
        Compat.pop(c);

        String hint = selected == null ? "Click to toggle  ·  Right-click for settings  ·  Middle-click to favourite  ·  Esc to close"
                : capturing != null ? "Press a key  ·  Backspace to unbind  ·  Esc to cancel" : "Esc to go back";
        centered(c, hint, width / 2f, height - 13, withAlpha(0xFFFFFF, (int) (70 * open)), false);
    }

    private void renderTopBar(DrawContext c, int mx, int my, float dt, int acc) {
        int L = left(), R = left() + contentW();
        logo(c, L + 8, TOP - 15, 5f);
        bold(c, "Orbit", L + 22, TOP - 19, 0xFFFFFFFF, false);

        // segmented tab bar
        int[] xs = pillLayout();
        int barX = xs[0] - 3, barW = xs[CATS.length - 1] + pillW(CATS.length - 1) + 3 - barX;
        round(c, barX, TOP - 27, barW, 22, 11, 0x1EFFFFFF);
        int ai = search.isEmpty() ? category.ordinal() : -1;
        if (ai >= 0) {
            float sx = a("tabx", xs[ai], 18, dt), sw = a("tabw", pillW(ai), 18, dt);
            round(c, sx, TOP - 24, sw, 16, 8, 0xF2FFFFFF);
        }
        for (int i = 0; i < CATS.length; i++) {
            boolean active = i == ai;
            boolean hov = in(mx, my, xs[i], TOP - 26, pillW(i), 20);
            int col = active ? 0xFF140A28 : hov ? TEXT : MUTED;
            centered(c, CATS[i].title, xs[i] + pillW(i) / 2f, TOP - 20, col, false);
        }

        int tabsEnd = barX + barW;
        int sw = Math.max(70, Math.min(130, R - tabsEnd - 10)), sx = R - sw;
        boolean focus = !search.isEmpty();
        round(c, sx, TOP - 27, sw, 22, 11, focus ? withAlpha(acc, 60) : 0x1EFFFFFF);
        boolean caret = (System.currentTimeMillis() / 500) % 2 == 0;
        searchGlass(c, sx + 10, TOP - 21, focus ? TEXT : FAINT);
        if (search.isEmpty()) text(c, "Search", sx + 22, TOP - 20, FAINT, false);
        else text(c, fit(search + (caret ? "_" : ""), sw - 30, false), sx + 22, TOP - 20, TEXT, false);
    }

    /** A tiny smooth magnifying glass. */
    private static void searchGlass(DrawContext c, int x, int y, int col) {
        round(c, x, y, 7, 7, 4, col);
        round(c, x + 1.5f, y + 1.5f, 4, 4, 2, 0xFF1A1630);
        Compat.push(c); Compat.translate(c, x + 6, y + 6);
        round(c, 0, 0, 3, 1.5f, 1, col); round(c, 1.2f, 1.2f, 2, 1.5f, 1, col);
        Compat.pop(c);
    }

    private int pillW(int i) { return width(CATS[i].title) + 18; }
    private int[] pillLayout() {
        int total = 0;
        for (int i = 0; i < CATS.length; i++) total += pillW(i) + 2;
        total -= 2;
        int[] xs = new int[CATS.length];
        // centred, but never under the logo or the search box (min 70px) on small windows
        int L = left(), R = left() + contentW();
        int x = Math.max(L + 66, Math.min(width / 2 - total / 2, R - 80 - total));
        for (int i = 0; i < CATS.length; i++) { xs[i] = x; x += pillW(i) + 2; }
        return xs;
    }

    private void renderGrid(DrawContext c, int mx, int my, float dt, int acc) {
        int L = left(), R = left() + contentW();
        String title = search.isEmpty() ? category.title : "Results";
        Compat.push(c);
        Compat.translate(c, L, TOP + 10);
        Compat.scale(c, Render2D.pixelScale(1.6f));
        display(c, title, 0, 0, 0xFFFFFFFF, false);
        Compat.pop(c);
        text(c, search.isEmpty() ? category.subtitle : "for \"" + search + "\"", L, TOP + 28, FAINT, false);

        String count = countLabel();
        int cx = R - width(count);
        text(c, count, cx, TOP + 20, FAINT, false);
        String[] labels = {"All", "On", "Favourites"};
        int fx = cx - 12;
        for (int i = labels.length - 1; i >= 0; i--) {
            int w = width(labels[i]) + 16;
            fx -= w;
            boolean on = filter.ordinal() == i;
            boolean hov = in(mx, my, fx, TOP + 14, w, 18);
            round(c, fx, TOP + 15, w, 16, 8, on ? 0xF2FFFFFF : hov ? 0x30FFFFFF : 0x18FFFFFF);
            text(c, labels[i], fx + 8, TOP + 19, on ? 0xFF140A28 : MUTED, false);
            fx -= 4;
        }
        // configs: save / load whole setups
        int cw = width("Configs") + 22, cfx = fx - 8 - cw;
        boolean ch = in(mx, my, cfx, TOP + 14, cw, 18);
        round(c, cfx, TOP + 15, cw, 16, 8, ch ? withAlpha(acc, 200) : withAlpha(acc, 120));
        text(c, "Configs", cfx + 11, TOP + 19, 0xFFFFFFFF, false);
        // move HUD elements (coordinates, FPS, …) around the screen
        int ew = width("Edit HUD") + 22, efx = cfx - 6 - ew;
        boolean eh = in(mx, my, efx, TOP + 14, ew, 18);
        round(c, efx, TOP + 15, ew, 16, 8, eh ? 0x40FFFFFF : 0x24FFFFFF);
        text(c, "Edit HUD", efx + 11, TOP + 19, 0xFFFFFFFF, false);

        List<Module> mods = visible();
        scroll = approach(scroll, scrollTarget, 18, dt);
        c.enableScissor(0, GRID_Y - 4, width, gridBottom());
        int w = cardW();
        for (int i = 0; i < mods.size(); i++) {
            Module m = mods.get(i);
            int x = L + (i % cols()) * (w + 8);
            int y0 = GRID_Y + (i / cols()) * (CARD_H + 8) - Math.round(scroll);
            if (y0 > gridBottom() || y0 + CARD_H < GRID_Y - 4) continue;
            boolean hov = in(mx, my, x, y0, w, CARD_H) && my > GRID_Y - 4 && my < gridBottom();
            float h = a("card" + m.id, hov ? 1 : 0, 14, dt);
            // cards cascade in when the page opens / changes, and lift a little under the mouse
            float appear = Render2D.easeOut((System.currentTimeMillis() - gridAnimAt - Math.min(i, 18) * 22) / 260f);
            int y = Math.round(y0 + (1 - appear) * 12 - h * 1.5f);
            Render2D.fade = appear;
            float on = a("on" + m.id, m.isEnabled() ? 1 : 0, 12, dt);
            // glass card, a soft accent wash when the mod is on
            round(c, x, y, w, CARD_H, 9, lerpColor(0x16FFFFFF, 0x24FFFFFF, h));
            if (on > 0.01f) round(c, x, y, w, CARD_H, 9, withAlpha(acc, (int) (26 * on)));
            round(c, x + 9, y + 9, 26, 26, 8, lerpColor(0x1AFFFFFF, withAlpha(acc, 70), on));
            item(c, m.icon(), x + 14, y + 14, 16);
            int textW = w - 44 - 64;
            bold(c, fit(m.name, textW, true), x + 43, y + 12, 0xFFFFFFFF, false);
            text(c, fit(m.description, textW, false), x + 43, y + 24, MUTED, false);
            if (m.isFavourite()) text(c, "★", x + 43 + boldWidth(fit(m.name, textW, true)) + 4, y + 12, 0xFFFFD84D, false);
            if (!m.settings().isEmpty()) {
                boolean gh = in(mx, my, x + w - 58, y + 12, 20, 20);
                float g = a("gear" + m.id, gh ? 1 : hov ? 0.6f : 0.25f, 16, dt);
                if (gh) round(c, x + w - 58, y + 12, 20, 20, 10, 0x22FFFFFF);
                for (int k = 0; k < 3; k++) round(c, x + w - 53.5f + k * 4.5f, y + 21, 2.5f, 2.5f, 2, alpha(0xFFFFFFFF, g));
            }
            if (m.isToggleable()) toggle(c, x + w - 32, y + 15, m.isEnabled(), "tg" + m.id, dt, acc);
            Render2D.fade = 1;
        }
        c.disableScissor();
        int rows = (mods.size() + cols() - 1) / cols();
        float maxScroll = Math.max(0, rows * (CARD_H + 8) - (gridBottom() - GRID_Y));
        scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
        if (mods.isEmpty()) centered(c, filter == Filter.FAV ? "No favourites yet — middle-click a mod to add it" : "Nothing here", width / 2f, GRID_Y + 30, MUTED, false);
    }

    private void toggle(DrawContext c, int x, int y, boolean on, String key, float dt, int acc) {
        float t = a(key, on ? 1 : 0, 16, dt);
        round(c, x, y, 22, 13, 7, lerpColor(0x33FFFFFF, acc, t));
        round(c, x + 1.5f + t * 9, y + 1.5f, 10, 10, 5, 0xFFFFFFFF);
    }

    private static String fit(String s, int max, boolean b) { return Render2D.fit(s, max, b); }

    // ------------------------------------------------------------------------------ settings view
    private static final int ROW = 24;
    private int panelX() { return (width - panelW()) / 2; }
    private int panelW() { return Math.min(440, width - 24); }

    private void renderSettings(DrawContext c, int mx, int my, float dt, int acc) {
        int px = panelX(), pw = panelW(), top = TOP + 6;
        round(c, px, top, pw, 40, 10, 0x1CFFFFFF);
        boolean bh = in(mx, my, px + 6, top + 10, 20, 20);
        round(c, px + 6, top + 10, 20, 20, 10, bh ? 0x33FFFFFF : 0x14FFFFFF);
        // chevron
        for (int k = 0; k < 4; k++) { round(c, px + 14 + k, top + 16 + k, 1.5f, 1.5f, 1, TEXT); round(c, px + 14 + k, top + 23 - k, 1.5f, 1.5f, 1, TEXT); }
        round(c, px + 32, top + 8, 24, 24, 8, withAlpha(acc, 70));
        item(c, selected.icon(), px + 36, top + 12, 16);
        bold(c, selected.name, px + 58, top + 10, 0xFFFFFFFF, false);
        text(c, fit(selected.description, pw - 140, false), px + 58, top + 22, MUTED, false);
        boolean sh = in(mx, my, px + pw - 58, top + 12, 16, 16);
        text(c, "★", px + pw - 54, top + 16, selected.isFavourite() ? 0xFFFFD84D : sh ? TEXT : FAINT, false);
        if (selected.isToggleable()) toggle(c, px + pw - 34, top + 13, selected.isEnabled(), "stg" + selected.id, dt, acc);

        int listTop = top + 48;
        scroll = approach(scroll, scrollTarget, 18, dt);
        c.enableScissor(0, listTop, width, gridBottom());
        int y = listTop - Math.round(scroll), right = px + pw - 8;
        for (Setting<?> st : selected.settings()) {
            if (!st.isVisible()) continue;
            boolean hov = in(mx, my, px, y, pw, ROW - 2) && my > listTop && my < gridBottom();
            float h = a("row" + selected.id + st.id, hov ? 1 : 0, 16, dt);
            round(c, px, y, pw, ROW - 2, 8, lerpColor(0x12FFFFFF, 0x22FFFFFF, h));
            text(c, st.name, px + 10, y + 7, TEXT, false);
            if (st instanceof Setting.Bool b) toggle(c, right - 24, y + 4, b.on(), "s" + selected.id + st.id, dt, acc);
            else if (st instanceof Setting.Slider sl) {
                int tw = 110, tx = right - tw, ty = y + 9;
                if (dragging == sl) sl.set(sl.min + (sl.max - sl.min) * Math.max(0, Math.min(1, (mx - dragX) / (double) dragW)));
                float f = a("sl" + selected.id + st.id, (float) sl.fraction(), 22, dt);
                round(c, tx, ty, tw, 4, 2, 0x26FFFFFF);
                round(c, tx, ty, Math.max(4, tw * f), 4, 2, acc);
                round(c, tx + tw * f - 5, ty - 3, 10, 10, 5, 0xFFFFFFFF);
                String v = sl.display();
                text(c, v, tx - width(v) - 8, y + 7, MUTED, false);
            } else if (st instanceof Setting.Color col) {
                int sz = 10, gap = 3, n = SWATCHES.length, sx = right - n * (sz + gap) + gap;
                for (int i = 0; i < n; i++) {
                    int x = sx + i * (sz + gap);
                    if ((col.argb() & 0xFFFFFF) == (SWATCHES[i] & 0xFFFFFF)) round(c, x - 2, y + 4, sz + 4, sz + 4, 7, 0xFFFFFFFF);
                    round(c, x, y + 6, sz, sz, 5, 0xFF000000 | SWATCHES[i]);
                }
            } else if (st instanceof Setting.Mode mode) {
                String v = mode.get();
                int bw = Math.max(80, width(v) + 30), bx = right - bw;
                round(c, bx, y + 3, bw, 16, 8, 0x1EFFFFFF);
                text(c, "‹", bx + 6, y + 7, MUTED, false);
                text(c, "›", bx + bw - 10, y + 7, MUTED, false);
                centered(c, v, bx + bw / 2f, y + 7, TEXT, false);
            } else if (st instanceof Setting.Key key) {
                String v = capturing == key ? "Press a key…" : Compat.keyName(key.binding.get());
                int bw = Math.max(70, width(v) + 20), bx = right - bw;
                round(c, bx, y + 3, bw, 16, 8, capturing == key ? withAlpha(acc, 70) : 0x1EFFFFFF);
                centered(c, v, bx + bw / 2f, y + 7, capturing == key ? acc : TEXT, false);
            } else if (st instanceof Setting.Action act) {
                int bw = width(act.label) + 20, bx = right - bw;
                boolean ah = in(mx, my, bx, y + 3, bw, 16);
                round(c, bx, y + 3, bw, 16, 8, ah ? lerpColor(acc, 0xFFFFFFFF, 0.25f) : acc);
                centered(c, act.label, bx + bw / 2f, y + 7, 0xFF140A28, false);
            }
            y += ROW;
        }
        y += 4;
        boolean rh = in(mx, my, px, y, 100, 18);
        round(c, px, y, 100, 18, 9, rh ? 0x30FFFFFF : 0x1AFFFFFF);
        centered(c, "Reset settings", px + 50, y + 5, MUTED, false);
        if (selected instanceof HudModule) {
            boolean eh = in(mx, my, px + 106, y, 100, 18);
            round(c, px + 106, y, 100, 18, 9, eh ? lerpColor(acc, 0xFFFFFFFF, 0.25f) : acc);
            centered(c, "Move on screen", px + 156, y + 5, 0xFF140A28, false);
        }
        c.disableScissor();
        int contentH = (y + 24) - (listTop - Math.round(scroll));
        scrollTarget = Math.max(0, Math.min(Math.max(0, contentH - (gridBottom() - listTop)), scrollTarget));
    }

    // ------------------------------------------------------------------------------ input
    private static boolean in(double mx, double my, double x, double y, double w, double h) { return mx >= x && my >= y && mx < x + w && my < y + h; }

    @Override
    protected boolean onClick(double mx, double my, int button) {
        int[] xs = pillLayout();
        for (int i = 0; i < CATS.length; i++) {
            if (in(mx, my, xs[i], TOP - 27, pillW(i), 22)) { switchTo(CATS[i]); return true; }
        }
        if (selected != null) return clickSettings(mx, my);

        int R = left() + contentW();
        String[] labels = {"All", "On", "Favourites"};
        int fx = R - width(countLabel()) - 12;
        for (int i = labels.length - 1; i >= 0; i--) {
            int w = width(labels[i]) + 16;
            fx -= w;
            if (in(mx, my, fx, TOP + 14, w, 18)) { filter = Filter.values()[i]; scrollTarget = 0; gridAnimAt = System.currentTimeMillis(); Sounds.tab(); return true; }
            fx -= 4;
        }
        int cw = width("Configs") + 22;
        if (in(mx, my, fx - 8 - cw, TOP + 14, cw, 18)) { Sounds.click(); client.setScreen(new ConfigsScreen(this)); return true; }
        int ew = width("Edit HUD") + 22;
        if (in(mx, my, fx - 8 - cw - 6 - ew, TOP + 14, ew, 18)) { Sounds.click(); client.setScreen(new HudEditorScreen(this)); return true; }
        if (my < GRID_Y - 4 || my > gridBottom()) return false;
        List<Module> mods = visible();
        int w = cardW();
        for (int i = 0; i < mods.size(); i++) {
            Module m = mods.get(i);
            int x = left() + (i % cols()) * (w + 8);
            int y = GRID_Y + (i / cols()) * (CARD_H + 8) - Math.round(scroll);
            if (!in(mx, my, x, y, w, CARD_H)) continue;
            if (button == 2) { m.setFavourite(!m.isFavourite()); return true; }
            boolean gear = in(mx, my, x + w - 60, y + 10, 24, 24);
            if ((gear || button == 1 || !m.isToggleable()) && !m.settings().isEmpty()) { open(m); Sounds.click(); return true; }
            m.toggle();
            Sounds.toggle(m.isEnabled());
            return true;
        }
        return false;
    }

    private boolean clickSettings(double mx, double my) {
        int px = panelX(), pw = panelW(), top = TOP + 6;
        if (in(mx, my, px + 6, top + 10, 20, 20)) { back(); return true; }
        if (in(mx, my, px + pw - 58, top + 12, 16, 16)) { selected.setFavourite(!selected.isFavourite()); return true; }
        if (selected.isToggleable() && in(mx, my, px + pw - 36, top + 10, 28, 20)) { selected.toggle(); Sounds.toggle(selected.isEnabled()); return true; }
        int listTop = top + 48;
        if (my < listTop || my > gridBottom()) return false;
        int y = listTop - Math.round(scroll), right = px + pw - 8;
        for (Setting<?> st : selected.settings()) {
            if (!st.isVisible()) continue;
            if (in(mx, my, px, y, pw, ROW - 2)) {
                if (st instanceof Setting.Bool b) { b.toggle(); Sounds.toggle(b.on()); }
                else if (st instanceof Setting.Slider sl && mx > right - 120) { dragging = sl; dragX = right - 110; dragW = 110; }
                else if (st instanceof Setting.Mode mode) {
                    int bw = Math.max(80, width(mode.get()) + 30), bx = right - bw;
                    if (in(mx, my, bx, y, bw, ROW)) mode.cycle(mx < bx + bw / 2.0 ? -1 : 1);
                } else if (st instanceof Setting.Color col) {
                    int sz = 10, gap = 3, sx = right - SWATCHES.length * (sz + gap) + gap;
                    for (int i = 0; i < SWATCHES.length; i++) if (in(mx, my, sx + i * (sz + gap) - 1, y + 4, sz + 2, sz + 4)) col.set(SWATCHES[i]);
                } else if (st instanceof Setting.Key key) capturing = capturing == key ? null : key;
                else if (st instanceof Setting.Action act) act.get().run();
                return true;
            }
            y += ROW;
        }
        y += 4;
        if (in(mx, my, px, y, 100, 18)) { selected.settings().forEach(Setting::reset); if (selected instanceof HudModule h) h.resetPosition(); return true; }
        if (selected instanceof HudModule && in(mx, my, px + 106, y, 100, 18)) { client.setScreen(new HudEditorScreen(this)); return true; }
        return false;
    }

    private void switchTo(Module.Category c) { if (c != category) { Sounds.tab(); gridAnimAt = System.currentTimeMillis(); } category = c; selected = null; search = ""; scroll = scrollTarget = 0; capturing = null; }
    private long gridAnimAt = System.currentTimeMillis();
    public void open(Module m) { selected = m; category = m.category; scroll = scrollTarget = 0; capturing = null; }
    /** Opens on a given tab (used by tests and shortcuts). */
    public OrbitScreen tab(Module.Category c) { switchTo(c); return this; }
    private void back() { selected = null; scroll = scrollTarget = 0; capturing = null; }

    @Override protected boolean onRelease(double mx, double my, int button) { dragging = null; return false; }
    @Override protected boolean onScroll(double mx, double my, double v) { scrollTarget -= (float) (v * 26); return true; }

    @Override
    protected boolean onKey(int key, int scan, int mods) {
        if (capturing != null) {
            if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) Compat.rebind(capturing.binding.get(), -1);
            else if (key != GLFW.GLFW_KEY_ESCAPE) Compat.rebind(capturing.binding.get(), key);
            capturing = null;
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (!search.isEmpty()) { search = ""; return true; }
            if (selected != null) { back(); return true; }
            close();
            return true;
        }
        if (Compat.matches(OrbitClient.menuKey, key, scan) && search.isEmpty()) { close(); return true; }
        if (selected == null && search.isEmpty() && (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT)) {
            int i = category.ordinal() + (key == GLFW.GLFW_KEY_RIGHT ? 1 : -1);
            switchTo(CATS[Math.floorMod(i, CATS.length)]);
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE && !search.isEmpty() && selected == null) { search = search.substring(0, search.length() - 1); return true; }
        return false;
    }

    @Override
    protected boolean onChar(char chr) {
        if (capturing != null) return true;
        if (selected == null && chr == '/' && search.isEmpty()) return true;
        if (selected == null && chr >= ' ' && search.length() < 24) { search += chr; scroll = scrollTarget = 0; return true; }
        return false;
    }

    @Override public void close() {
        OrbitClient.modules().save();
        Sounds.close();
        super.close();
    }
}
