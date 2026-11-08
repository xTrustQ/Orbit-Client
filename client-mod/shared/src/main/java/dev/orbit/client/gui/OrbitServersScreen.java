package dev.orbit.client.gui;

import dev.orbit.client.compat.Compat;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.MultiplayerServerListPinger;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static dev.orbit.client.gui.Render2D.*;

/**
 * Clean server list: favicon, MOTD, players & ping. Everything happens here — join, add, edit,
 * delete and direct connect — without the vanilla list.
 */
public final class OrbitServersScreen extends OrbitBaseScreen {
    private static final ExecutorService PING = Executors.newFixedThreadPool(4, r -> { Thread t = new Thread(r, "Orbit-Ping"); t.setDaemon(true); return t; });
    private final Screen parent;
    private final long openedAt = System.currentTimeMillis();
    private final List<ServerInfo> servers = new ArrayList<>();
    private final MultiplayerServerListPinger pinger = new MultiplayerServerListPinger();
    private final Map<ServerInfo, Identifier> icons = new HashMap<>();
    private final Map<ServerInfo, byte[]> iconBytes = new HashMap<>();
    private final Map<Object, Float> hover = new HashMap<>();
    private float scroll, scrollTarget;
    private static final int ROW = 34, TOP = 78;
    /** a server's name, or its address when it still has the default name */
    private static String title(ServerInfo s) { return s.name == null || s.name.isBlank() || s.name.equals("Minecraft Server") ? s.address : s.name; }
    /** the first line of the description, without "[item/…]" placeholders and extra spaces */
    private static String cleanMotd(ServerInfo s) {
        if (s.label == null) return "";
        String m = s.label.getString().split("\n")[0];
        m = m.replaceAll("\\[(item|block|entity|atlas|player)[^\\]]*\\]", "").replaceAll("[\\u2580-\\u259F\\u25A0-\\u25FF]+", " ").replaceAll("\\s{2,}", " ").trim();
        return m;
    }
    // smooth reordering: every row glides to its slot; a pressed row can be dragged to a new place
    private final Map<ServerInfo, Float> rowPos = new HashMap<>();
    private ServerInfo pressed, dragging;
    private double pressY, grab;
    private long lastFrame = System.nanoTime();

    // form (add / edit / direct connect)
    private enum Form { NONE, ADD, EDIT, DIRECT }
    private Form form = Form.NONE;
    private int editIndex = -1, field;
    private final TextField nameF = new TextField(64), addrF = new TextField(128, ch -> ch > ' ');
    private String error = "";
    private String confirmDelete = "";
    private long confirmAt;

    public OrbitServersScreen(Screen parent) { super(Text.literal("Multiplayer")); this.parent = parent; }

    @Override
    protected void init() {
        servers.clear();
        ServerList list = new ServerList(client);
        list.loadFile();
        for (int i = 0; i < list.size(); i++) servers.add(list.get(i));
        for (ServerInfo s : servers) ping(s);
    }

    private void ping(ServerInfo s) {
        s.ping = -2;
        s.label = Text.literal("Pinging…");
        PING.submit(() -> { try { Compat.ping(pinger, s, () -> {}); } catch (Exception e) { s.ping = -1; s.label = Text.literal("Can't reach server"); } });
    }

    @Override public void tick() { pinger.tick(); }
    @Override public void removed() { pinger.cancel(); }

    private int listX() { return (width - listW()) / 2; }
    private int listW() { return Math.min(500, width - 30); }
    private float h(Object k, boolean on) { float v = approach(hover.getOrDefault(k, 0f), on ? 1 : 0, 14, 1 / 60f); hover.put(k, v); return v; }

    @Override public void renderBackground(DrawContext c, int mx, int my, float delta) { SpaceBackground.draw(c, width, height, mx, my, false); }

    // toolbar geometry
    private int[] tb() { int L = listX(), W = listW(); return new int[]{L + W - 86, L + W - 186, L + W - 252}; } // add, direct, refresh x
    private static final int TB_Y = 48;

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        super.render(c, mx, my, delta);
        boolean modal = form != Form.NONE;
        int fmx = modal ? -1 : mx, fmy = modal ? -1 : my; // the list ignores the mouse while the form is open
        float t = easeOut((System.currentTimeMillis() - openedAt) / 400f);
        int L = listX(), W = listW();
        float k = Render2D.pixelScale(2f);
        Compat.push(c);
        Compat.translate(c, L, Render2D.snap(20 + (1 - t) * 8));
        Compat.scale(c, k);
        bold(c, "Multiplayer", 0, 0, alpha(0xFFFFFFFF, t), false);
        Compat.pop(c);
        long online = servers.stream().filter(s -> s.ping >= 0).count();
        text(c, servers.size() + " servers  ·  " + online + " online", L, 42, MUTED, false);

        int[] x = tb();
        SpaceBackground.button(c, x[0], TB_Y, 86, 18, "+ Add server", fmx, fmy, h("add", in(fmx, fmy, x[0], TB_Y, 86, 18)), true);
        SpaceBackground.button(c, x[1], TB_Y, 94, 18, "Direct connect", fmx, fmy, h("direct", in(fmx, fmy, x[1], TB_Y, 94, 18)), false);
        SpaceBackground.button(c, x[2], TB_Y, 60, 18, "Refresh", fmx, fmy, h("refresh", in(fmx, fmy, x[2], TB_Y, 60, 18)), false);

        scroll = approach(scroll, scrollTarget, 18, 1 / 60f);
        long nowN = System.nanoTime();
        float dt = Math.min(0.1f, (nowN - lastFrame) / 1e9f);
        lastFrame = nowN;
        c.enableScissor(0, TOP, width, height - 28);
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < servers.size(); i++) if (servers.get(i) != dragging) order.add(i);
        for (int i = 0; i < servers.size(); i++) if (servers.get(i) == dragging) order.add(i); // dragged row on top
        for (int i : order) {
            ServerInfo s = servers.get(i);
            float pos = approach(rowPos.getOrDefault(s, (float) i), i, 16, dt);
            rowPos.put(s, pos);
            boolean lifted = s == dragging;
            int y = lifted ? (int) Math.round(my - grab) : Math.round(TOP + 4 + pos * (ROW + 6) - scroll);
            if (y > height || y + ROW < TOP) continue;
            boolean hov = !lifted && dragging == null && in(fmx, fmy, L, y, W, ROW) && fmy > TOP && fmy < height - 28;
            float hv = lifted ? 0 : h(s, hov);
            if (lifted) { round(c, L - 2, y + 3, W + 4, ROW + 2, 12, 0x60000000); round(c, L - 1, y - 1, W + 2, ROW + 2, 11, withAlpha(accent(), 200)); }
            round(c, L, y, W, ROW, 10, lifted ? 0xF0221A40 : lerpColor(0x1AFFFFFF, 0x2EFFFFFF, hv));
            Identifier icon = icon(s);
            if (icon != null) Compat.blit(c, icon, L + 6, y + 5, 24, 24, 64, 64);
            else { round(c, L + 6, y + 5, 24, 24, 6, 0x22FFFFFF); item(c, Items.ENDER_PEARL, L + 10, y + 9, 16); }
            int right = L + W - 10, textW = W - 38 - 120;
            bold(c, fit(title(s), textW, true), L + 38, y + 7, 0xFFFFFFFF, false);
            String motd = cleanMotd(s);
            text(c, fit(motd, textW, false), L + 38, y + 19, withAlpha(0xFFFFFF, 120), false);
            if (lifted) { text(c, "Drop to move here", right - width("Drop to move here"), y + 13, MUTED, false); }
            else if (hv > 0.5f) { // actions replace the stats while hovered
                String del = confirming(s) ? "Sure?" : "×";
                int dw = width(del) + 14, ew = width("Edit") + 16, jw = width("Join") + 18;
                int dx = right - dw, ex = dx - 4 - ew, jx = ex - 4 - jw;
                round(c, jx, y + 9, jw, 16, 8, in(fmx, fmy, jx, y + 9, jw, 16) ? lerpColor(accent(), 0xFFFFFFFF, 0.3f) : accent());
                text(c, "Join", jx + 9, y + 13, 0xFF140A28, false);
                round(c, ex, y + 9, ew, 16, 8, in(fmx, fmy, ex, y + 9, ew, 16) ? 0x44FFFFFF : 0x22FFFFFF);
                text(c, "Edit", ex + 8, y + 13, TEXT, false);
                round(c, dx, y + 9, dw, 16, 8, confirming(s) ? 0xFFE5484D : in(fmx, fmy, dx, y + 9, dw, 16) ? 0x55E5484D : 0x22FFFFFF);
                text(c, del, dx + 7, y + 13, 0xFFFFFFFF, false);
                // move up / down
                int dnx = jx - 4 - 16, upx = dnx - 3 - 16;
                if (i > 0) { round(c, upx, y + 9, 16, 16, 8, in(fmx, fmy, upx, y + 9, 16, 16) ? 0x44FFFFFF : 0x22FFFFFF); centered(c, "↑", upx + 8, y + 13, TEXT, false); }
                if (i < servers.size() - 1) { round(c, dnx, y + 9, 16, 16, 8, in(fmx, fmy, dnx, y + 9, 16, 16) ? 0x44FFFFFF : 0x22FFFFFF); centered(c, "↓", dnx + 8, y + 13, TEXT, false); }
            } else {
                String players = s.playerCountLabel == null ? "" : s.playerCountLabel.getString();
                pingBars(c, right - 10, y + 7, s.ping);
                if (!players.isEmpty()) text(c, players, right - 16 - width(players), y + 7, MUTED, false);
                if (s.ping >= 0) text(c, s.ping + " ms", right - width(s.ping + " ms"), y + 20, FAINT, false);
            }
        }
        c.disableScissor();
        if (servers.isEmpty()) centered(c, "No servers yet — press + Add server", width / 2f, TOP + 40, MUTED, false);
        float max = Math.max(0, servers.size() * (ROW + 6) - (height - 28 - TOP));
        scrollTarget = Math.max(0, Math.min(max, scrollTarget));
        SpaceBackground.button(c, 10, height - 24, 60, 16, "‹ Back", fmx, fmy, h("back", in(fmx, fmy, 10, height - 24, 60, 16)), false);

        if (modal) renderForm(c, mx, my);
    }

    // ------------------------------------------------------------------ form
    private int fw() { return Math.min(300, width - 40); }
    private int fx() { return (width - fw()) / 2; }
    private int fy() { return height / 2 - (form == Form.DIRECT ? 50 : 66); }

    private void renderForm(DrawContext c, int mx, int my) {
        c.fill(0, 0, width, height, 0xA0050310);
        int x = fx(), y = fy(), w = fw(), hgt = form == Form.DIRECT ? 100 : 132;
        round(c, x, y, w, hgt, 12, 0xF0161228);
        bold(c, form == Form.ADD ? "Add server" : form == Form.EDIT ? "Edit server" : "Direct connect", x + 14, y + 12, 0xFFFFFFFF, false);
        int fyy = y + 30;
        if (form != Form.DIRECT) { field(c, "Name", 0, x + 14, fyy, w - 28); fyy += 34; }
        field(c, "Address", form == Form.DIRECT ? 0 : 1, x + 14, fyy, w - 28);
        if (!error.isEmpty()) text(c, error, x + 14, y + hgt - 20, 0xFFFF8C8C, false);
        String ok = form == Form.DIRECT ? "Join" : "Save";
        SpaceBackground.button(c, x + w - 74, y + hgt - 24, 60, 16, ok, mx, my, h("fok", in(mx, my, x + w - 74, y + hgt - 24, 60, 16)), true);
        SpaceBackground.button(c, x + w - 140, y + hgt - 24, 60, 16, "Cancel", mx, my, h("fcancel", in(mx, my, x + w - 140, y + hgt - 24, 60, 16)), false);
    }
    private void field(DrawContext c, String label, int idx, int x, int y, int w) {
        text(c, label, x, y, MUTED, false);
        boolean focus = field == idx;
        tf(idx).render(c, x, y + 10, w, 18, focus, form == Form.DIRECT || idx == 1 ? "play.example.net" : "Server name", focus ? 0x33FFFFFF : 0x1AFFFFFF);
    }
    /** the text box for form field i */
    private TextField tf(int i) { return form == Form.DIRECT || i == 1 ? addrF : nameF; }
    private int fieldCount() { return form == Form.DIRECT ? 1 : 2; }


    private void openForm(Form f, int index) {
        form = f; editIndex = index; field = 0; error = "";
        if (f == Form.EDIT) { ServerInfo s = servers.get(index); nameF.set(s.name); addrF.set(s.address); }
        else { nameF.set(f == Form.ADD ? "Minecraft Server" : ""); addrF.set(""); if (f == Form.ADD) field = 1; }
        Sounds.click();
    }

    private void submitForm() {
        String addr = addrF.text.trim();
        if (addr.isEmpty()) { error = "Type the server address"; return; }
        if (form == Form.DIRECT) {
            ServerInfo s = new ServerInfo("Direct connect", addr, ServerInfo.ServerType.OTHER);
            form = Form.NONE;
            Compat.connect(this, s);
            return;
        }
        String name = nameF.text.trim().isEmpty() ? "Minecraft Server" : nameF.text.trim();
        ServerList list = new ServerList(client);
        list.loadFile();
        if (form == Form.EDIT && editIndex >= 0 && editIndex < list.size()) {
            ServerInfo s = list.get(editIndex);
            s.name = name; s.address = addr;
            list.set(editIndex, s);
        } else list.add(new ServerInfo(name, addr, ServerInfo.ServerType.OTHER), false);
        list.saveFile();
        Sounds.success();
        form = Form.NONE;
        init();
    }

    /** swaps two servers (also in servers.dat), keeping their ping info */
    private void move(int a, int b) {
        java.util.Collections.swap(servers, a, b); // rows glide to their new places
        saveOrder();
        Sounds.click();
    }

    private boolean confirming(ServerInfo s) { return (s.address + "|" + s.name).equals(confirmDelete) && System.currentTimeMillis() - confirmAt < 3000; }
    private void delete(int i) {
        ServerInfo s = servers.get(i);
        if (!confirming(s)) { confirmDelete = s.address + "|" + s.name; confirmAt = System.currentTimeMillis(); Sounds.click(); return; }
        ServerList list = new ServerList(client);
        list.loadFile();
        if (i < list.size()) { list.remove(list.get(i)); list.saveFile(); }
        confirmDelete = "";
        Sounds.toggle(false);
        init();
    }

    // ------------------------------------------------------------------ drawing helpers
    private static void pingBars(DrawContext c, int x, int y, long ping) {
        int lit = ping < 0 ? 0 : ping < 150 ? 5 : ping < 300 ? 4 : ping < 600 ? 3 : ping < 1000 ? 2 : 1;
        int col = ping < 0 ? 0xFF6C6390 : ping < 150 ? 0xFF4ADE80 : ping < 300 ? 0xFFA3E635 : ping < 600 ? 0xFFFACC15 : 0xFFF87171;
        if (ping == -2) {
            int k = (int) (System.currentTimeMillis() / 150 % 5);
            for (int i = 0; i < 5; i++) c.fill(x + i * 2, y + 8 - (i + 1) * 2 + 2, x + i * 2 + 1, y + 8, i == k ? 0xFFB18CFF : 0xFF2B2450);
            return;
        }
        for (int i = 0; i < 5; i++) c.fill(x + i * 2, y + 8 - (i + 1) * 2 + 2, x + i * 2 + 1, y + 8, i < lit ? col : 0xFF2B2450);
    }

    private Identifier icon(ServerInfo s) {
        byte[] fav = s.getFavicon();
        if (fav == null) return null;
        if (Arrays.equals(fav, iconBytes.get(s))) return icons.get(s);
        iconBytes.put(s, fav);
        try {
            NativeImage img = NativeImage.read(fav);
            Identifier id = Compat.id("server/" + Integer.toHexString(s.address.hashCode()));
            client.getTextureManager().registerTexture(id, Compat.texture("orbit-server-icon", img, false));
            icons.put(s, id);
            return id;
        } catch (Exception e) { icons.put(s, null); return null; }
    }

    private static boolean in(double mx, double my, double x, double y, double w, double h) { return mx >= x && my >= y && mx < x + w && my < y + h; }

    // ------------------------------------------------------------------ input
    @Override
    protected boolean onClick(double mx, double my, int button) {
        if (form != Form.NONE) {
            int x = fx(), y = fy(), w = fw(), hgt = form == Form.DIRECT ? 100 : 132;
            if (in(mx, my, x + w - 74, y + hgt - 24, 60, 16)) { submitForm(); return true; }
            if (in(mx, my, x + w - 140, y + hgt - 24, 60, 16) || !in(mx, my, x, y, w, hgt)) { form = Form.NONE; Sounds.click(); return true; }
            int fyy = y + 30;
            for (int i = 0; i < fieldCount(); i++) { if (in(mx, my, x + 14, fyy + 10, w - 28, 18)) { field = i; tf(i).click(mx, 0); return true; } fyy += 34; }
            return true;
        }
        int L = listX(), W = listW();
        if (in(mx, my, 10, height - 24, 60, 16)) { close(); return true; }
        int[] x = tb();
        if (in(mx, my, x[0], TB_Y, 86, 18)) { openForm(Form.ADD, -1); return true; }
        if (in(mx, my, x[1], TB_Y, 94, 18)) { openForm(Form.DIRECT, -1); return true; }
        if (in(mx, my, x[2], TB_Y, 60, 18)) { Sounds.click(); init(); return true; }
        if (my < TOP || my > height - 28) return false;
        for (int i = 0; i < servers.size(); i++) {
            int y = TOP + 4 + i * (ROW + 6) - Math.round(scroll);
            if (!in(mx, my, L, y, W, ROW)) continue;
            ServerInfo s = servers.get(i);
            int right = L + W - 10;
            String del = confirming(s) ? "Sure?" : "×";
            int dw = width(del) + 14, ew = width("Edit") + 16, dx = right - dw, ex = dx - 4 - ew;
            if (in(mx, my, dx, y + 9, dw, 16)) { delete(i); return true; }
            if (in(mx, my, ex, y + 9, ew, 16)) { openForm(Form.EDIT, i); return true; }
            int jw = width("Join") + 18, jx = ex - 4 - jw, dnx = jx - 4 - 16, upx = dnx - 3 - 16;
            if (i > 0 && in(mx, my, upx, y + 9, 16, 16)) { move(i, i - 1); return true; }
            if (i < servers.size() - 1 && in(mx, my, dnx, y + 9, 16, 16)) { move(i, i + 1); return true; }
            pressed = s; pressY = my; grab = my - y; // join on release, or drag to reorder
            return true;
        }
        return false;
    }

    @Override protected boolean onDrag(double mx, double my, int button) {
        if (form != Form.NONE) { tf(field).drag(mx); return true; }
        if (pressed == null) return false;
        if (dragging == null && Math.abs(my - pressY) > 4) { dragging = pressed; Sounds.click(); }
        if (dragging != null) {
            int from = servers.indexOf(dragging);
            int to = (int) Math.round((my - grab - TOP - 4 + scroll) / (ROW + 6));
            to = Math.max(0, Math.min(servers.size() - 1, to));
            if (from >= 0 && to != from) { servers.remove(from); servers.add(to, dragging); Sounds.tab(); }
            // scroll when dragging near the edges
            if (my < TOP + 16) scrollTarget -= 6; else if (my > height - 44) scrollTarget += 6;
        }
        return true;
    }

    @Override protected boolean onRelease(double mx, double my, int button) {
        if (form != Form.NONE) { tf(field).release(); return true; }
        ServerInfo p = pressed, d = dragging;
        pressed = null; dragging = null;
        if (d != null) {
            rowPos.put(d, (float) ((my - grab - TOP - 4 + scroll) / (ROW + 6))); // glide from where it was dropped
            saveOrder();
            Sounds.success();
            return true;
        }
        if (p != null) { Sounds.click(); Compat.connect(this, p); return true; }
        return false;
    }

    /** writes the on-screen order to servers.dat */
    private void saveOrder() {
        ServerList list = new ServerList(client);
        list.loadFile();
        if (list.size() != servers.size()) return;
        for (int i = 0; i < servers.size(); i++) list.set(i, servers.get(i));
        list.saveFile();
    }

    @Override protected boolean onScroll(double mx, double my, double v) { if (form == Form.NONE) scrollTarget -= (float) (v * 30); return true; }

    @Override
    protected boolean onKey(int key, int scan, int mods) {
        if (form != Form.NONE) {
            if (key == GLFW.GLFW_KEY_ESCAPE) { form = Form.NONE; return true; }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { submitForm(); return true; }
            if (key == GLFW.GLFW_KEY_TAB) { field = (field + 1) % fieldCount(); return true; }
            if (tf(field).key(key, mods)) { error = ""; return true; } // cursor, selection, Ctrl+A/C/X/V
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) { close(); return true; }
        return false;
    }

    @Override
    protected boolean onChar(char chr) {
        if (form == Form.NONE || chr < ' ') return false;
        tf(field).chr(chr);
        return true;
    }

    @Override public void close() { client.setScreen(parent); }
    /** test hooks */
    public void testOpenForm(boolean direct) { openForm(direct ? Form.DIRECT : Form.ADD, -1); }
    public String testAddress() { return addrF.text; }
}
