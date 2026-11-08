package dev.orbit.client.gui;

import dev.orbit.client.OrbitClient;
import dev.orbit.client.module.Configs;
import dev.orbit.client.module.Module;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.*;

import static dev.orbit.client.gui.Render2D.*;

/** Command palette (K): type to find any mod, setting page, screen or action and run it with Enter. */
public final class CommandPaletteScreen extends OrbitBaseScreen {
    private record Cmd(String name, String hint, String group, Runnable run) {}
    private final Screen parent;
    private final TextField query = new TextField(48);
    private final long openedAt = System.currentTimeMillis();
    private List<Cmd> all, shown = List.of();
    private int selected;
    private float scroll;
    private String lastQuery = null;

    public CommandPaletteScreen(Screen parent) { super(Text.literal("Command palette")); this.parent = parent; }
    @Override public boolean shouldPause() { return false; }
    @Override public void close() { client.setScreen(parent); }

    private List<Cmd> build() {
        List<Cmd> l = new ArrayList<>();
        var mods = OrbitClient.modules();
        for (Module m : mods.all()) {
            if (m.isToggleable()) l.add(new Cmd((m.isEnabled() ? "Turn off " : "Turn on ") + m.name, m.description, "Mod", () -> { m.toggle(); mods.save(); Sounds.toggle(m.isEnabled()); toast((m.isEnabled() ? "On: " : "Off: ") + m.name); }));
            if (!m.settings().isEmpty()) l.add(new Cmd(m.name + " settings", "Open its settings", "Settings", () -> { OrbitScreen s = new OrbitScreen(null); s.open(m); client.setScreen(s); }));
        }
        l.add(new Cmd("Orbit Mods", "The Right Shift menu", "Open", () -> client.setScreen(new OrbitScreen(null))));
        l.add(new Cmd("Edit HUD", "Move and style HUD elements", "Open", () -> client.setScreen(new HudEditorScreen(null))));
        l.add(new Cmd("Configs", "Save / load / share setups", "Open", () -> client.setScreen(new ConfigsScreen(null))));
        l.add(new Cmd("Locker", "Skins, capes, cosmetics, emotes", "Open", () -> client.setScreen(OrbitTitleScreen.pauseOn("locker"))));
        l.add(new Cmd("Friends", "Your friends and chats", "Open", () -> client.setScreen(OrbitTitleScreen.pauseOn("friends"))));
        l.add(new Cmd("Screenshots", "Your screenshots", "Open", () -> client.setScreen(OrbitTitleScreen.pauseOn("shots"))));
        l.add(new Cmd("Emote wheel", "Play an emote", "Open", () -> client.setScreen(new EmoteWheelScreen())));
        l.add(new Cmd("Waypoints", "Manage waypoints", "Open", () -> client.setScreen(new WaypointsScreen(null))));
        l.add(new Cmd("Troll menu", "Disguise players", "Open", () -> client.setScreen(new TrollScreen(null))));
        l.add(new Cmd("Add waypoint here", "Mark where you stand", "Action", () -> { if (dev.orbit.client.waypoints.Waypoints.addHere() != null) toast("Waypoint added"); }));
        l.add(new Cmd("Copy coordinates", "x y z to the clipboard", "Action", () -> { if (client.player != null) { TextKeys.copy(client.player.getBlockX() + " " + client.player.getBlockY() + " " + client.player.getBlockZ()); toast("Coordinates copied"); } }));
        l.add(new Cmd("Reset fight stats", "Start counting hits/kills again", "Action", () -> { dev.orbit.client.pvp.Combat.reset(dev.orbit.client.pvp.Combat.sessionPlace); toast("Stats reset"); }));
        l.add(new Cmd("Third person view", "Cycle the camera", "Action", () -> client.options.setPerspective(client.options.getPerspective().next())));
        for (String w : new String[]{"Clear", "Rain", "Thunder", "Snow"})
            l.add(new Cmd("Weather: " + w, "Only on your screen", "Action", () -> { if (mods.get("weather") instanceof dev.orbit.client.module.NewModules.WeatherChanger wc) { wc.weather.set(w); wc.setEnabled(true); mods.save(); toast("Weather: " + w); } }));
        for (var c : Configs.list()) l.add(new Cmd("Load config: " + c.name(), c.enabled() + " mods on", "Config", () -> { if (Configs.load(c.name())) toast("Loaded " + c.name()); }));
        for (var e : dev.orbit.client.emote.Emote.values()) l.add(new Cmd("Emote: " + e.title, "Play it now", "Emote", () -> dev.orbit.client.emote.Emotes.playLocal(e)));
        return l;
    }
    private void toast(String s) { if (client.player != null) client.player.sendMessage(Text.literal("§d[Orbit] §f" + s), true); }

    /** fuzzy match score (higher = better), -1 = no match */
    private static int score(String text, String q) {
        if (q.isEmpty()) return 1;
        String t = text.toLowerCase(Locale.ROOT); q = q.toLowerCase(Locale.ROOT);
        int i = t.indexOf(q);
        if (i >= 0) return 1000 - i * 4 - (t.length() - q.length());
        int score = 0, ti = 0, streak = 0;
        for (char ch : q.toCharArray()) {
            int f = t.indexOf(ch, ti);
            if (f < 0) return -1;
            streak = f == ti ? streak + 1 : 0;
            score += 10 + streak * 6 - (f - ti);
            ti = f + 1;
        }
        return score;
    }
    private void refilter() {
        String q = query.text.trim();
        if (q.equals(lastQuery)) return;
        lastQuery = q;
        if (all == null) all = build();
        List<Map.Entry<Cmd, Integer>> r = new ArrayList<>();
        for (Cmd c : all) { int s = Math.max(score(c.name(), q), score(c.group() + " " + c.name(), q) - 50); if (s >= 0) r.add(Map.entry(c, s)); }
        r.sort((a, b) -> b.getValue() - a.getValue());
        shown = r.stream().map(Map.Entry::getKey).limit(60).toList();
        selected = 0;
    }

    private int pw() { return Math.min(420, width - 40); }
    private int px() { return (width - pw()) / 2; }
    private int py() { return Math.max(20, height / 5); }
    private static final int ROW = 22;
    private int visibleRows() { return Math.max(3, Math.min(10, (height - py() - 60) / ROW)); }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        super.render(c, mx, my, delta);
        refilter();
        c.fill(0, 0, width, height, 0x80050310);
        float in = easeOut((System.currentTimeMillis() - openedAt) / 200f);
        int px = px(), pw = pw(), py = Math.round(py() - (1 - in) * 10), rows = Math.min(visibleRows(), Math.max(1, shown.size()));
        int ph = 34 + rows * ROW + 8;
        round(c, px - 1, py - 1, pw + 2, ph + 2, 13, withAlpha(accent(), (int) (120 * in)));
        round(c, px, py, pw, ph, 12, withAlpha(0x0E0B1C, (int) (245 * in)));
        query.render(c, px + 8, py + 7, pw - 16, 20, true, "Type a command… (mods, settings, screens, configs, emotes)", 0x24FFFFFF);
        int first = Math.max(0, Math.min(selected - rows + 1, shown.size() - rows));
        scroll = approach(scroll, first, 20, 1 / 60f);
        int top = py + 34;
        c.enableScissor(px, top, px + pw, top + rows * ROW);
        for (int i = 0; i < shown.size(); i++) {
            int y = Math.round(top + (i - scroll) * ROW);
            if (y < top - ROW || y > top + rows * ROW) continue;
            Cmd cmd = shown.get(i);
            boolean sel = i == selected, hov = in(mx, my, px + 6, y, pw - 12, ROW - 2);
            if (sel || hov) round(c, px + 6, y, pw - 12, ROW - 2, 8, sel ? withAlpha(accent(), 160) : 0x20FFFFFF);
            String g = cmd.group();
            int gw = width(g) + 10;
            round(c, px + 12, y + 5, gw, 11, 5, sel ? 0x40FFFFFF : 0x1AFFFFFF);
            text(c, g, px + 17, y + 7, sel ? 0xFFFFFFFF : FAINT, false);
            text(c, fit(cmd.name(), pw - gw - 40 - Math.min(140, width(cmd.hint())), false), px + 18 + gw, y + 7, 0xFFFFFFFF, false);
            String h = fit(cmd.hint(), 140, false);
            text(c, h, px + pw - 14 - width(h), y + 7, sel ? 0xDDFFFFFF : FAINT, false);
        }
        c.disableScissor();
        if (shown.isEmpty()) text(c, "Nothing found", px + 14, top + 6, FAINT, false);
        centered(c, "↑ ↓ to choose  ·  Enter to run  ·  Esc to close", width / 2f, py + ph + 8, withAlpha(0xFFFFFF, 110), false);
    }
    private static boolean in(double mx, double my, double x, double y, double w, double h) { return mx >= x && my >= y && mx < x + w && my < y + h; }

    private void run(int i) {
        if (i < 0 || i >= shown.size()) return;
        Cmd c = shown.get(i);
        Sounds.click();
        client.setScreen(parent);
        c.run().run();
    }
    @Override protected boolean onClick(double mx, double my, int button) {
        int px = px(), pw = pw(), top = py() + 34, rows = Math.min(visibleRows(), Math.max(1, shown.size()));
        if (query.contains(mx, my)) { query.click(mx, 0); return true; }
        for (int i = 0; i < shown.size(); i++) {
            int y = Math.round(top + (i - scroll) * ROW);
            if (y >= top && y < top + rows * ROW && in(mx, my, px + 6, y, pw - 12, ROW - 2)) { run(i); return true; }
        }
        if (!in(mx, my, px, py(), pw, 40 + rows * ROW)) { close(); return true; }
        return false;
    }
    @Override protected boolean onScroll(double mx, double my, double v) { selected = Math.max(0, Math.min(shown.size() - 1, selected + (v < 0 ? 1 : -1))); return true; }
    @Override protected boolean onKey(int key, int scan, int mods) {
        switch (key) {
            case GLFW.GLFW_KEY_ESCAPE -> { close(); return true; }
            case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_TAB -> { selected = Math.min(shown.size() - 1, selected + 1); return true; }
            case GLFW.GLFW_KEY_UP -> { selected = Math.max(0, selected - 1); return true; }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> { run(selected); return true; }
            default -> { return query.key(key, mods); }
        }
    }
    @Override protected boolean onChar(char chr) { return query.chr(chr) || true; }
}
