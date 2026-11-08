package dev.orbit.client.gui;

import dev.orbit.client.OrbitClient;
import dev.orbit.client.troll.Troll;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.*;

import static dev.orbit.client.gui.Render2D.*;

/** Troll Mod menu: pick a player (or everyone), then Normal / Invisible / Disguise as any entity. */
public final class TrollScreen extends OrbitBaseScreen {
    private final Screen parent;
    private String selected = Troll.EVERYONE;
    private final List<String> extraNames = new ArrayList<>();
    private String search = "", nameInput = "", focus = "";
    private float scroll, scrollTarget, listScroll;
    private List<EntityType<?>> entities = Troll.choices();
    private static final int TILE_W = 100, TILE_H = 22;

    public TrollScreen(Screen parent) { super(Text.literal("Troll Mod")); this.parent = parent; }
    @Override public boolean shouldPause() { return false; }
    @Override public void close() { client.setScreen(parent); }
    private static boolean in(double mx, double my, double x, double y, double w, double h) { return mx >= x && my >= y && mx < x + w && my < y + h; }

    private List<String> targets() {
        List<String> l = new ArrayList<>();
        l.add(Troll.EVERYONE);
        Set<String> seen = new HashSet<>();
        if (client.getNetworkHandler() != null) {
            List<String> online = new ArrayList<>();
            for (var e : client.getNetworkHandler().getPlayerList()) online.add(e.getProfile().name());
            online.sort(String.CASE_INSENSITIVE_ORDER);
            for (String n : online) if (seen.add(n.toLowerCase(Locale.ROOT))) l.add(n);
        }
        for (String n : extraNames) if (seen.add(n.toLowerCase(Locale.ROOT))) l.add(n);
        for (String n : Troll.keys()) if (!n.equals(Troll.EVERYONE) && seen.add(n)) l.add(n); // pranked players who are offline
        return l;
    }
    private String label(String key) {
        if (key.equals(Troll.EVERYONE)) return "Everyone else";
        return client.player != null && key.equalsIgnoreCase(client.player.getGameProfile().name()) ? key + "  (you · F5)" : key;
    }
    private String status(String key) {
        Troll.Prank p = Troll.of(key);
        return switch (p.kind) { case NONE -> "Normal"; case INVISIBLE -> "Invisible"; case DISGUISE -> Troll.name(p.entity); };
    }
    private static Item egg(EntityType<?> t) {
        Identifier id = Registries.ENTITY_TYPE.getId(t);
        Item i = Registries.ITEM.get(Identifier.of(id.getNamespace(), id.getPath() + "_spawn_egg"));
        if (i != Items.AIR) return i;
        return switch (id.getPath()) {
            case "armor_stand" -> Items.ARMOR_STAND; case "tnt" -> Items.TNT; case "end_crystal" -> Items.END_CRYSTAL;
            case "iron_golem" -> Items.IRON_BLOCK; case "snow_golem" -> Items.CARVED_PUMPKIN; case "minecart" -> Items.MINECART;
            case "item_frame" -> Items.ITEM_FRAME; case "glow_item_frame" -> Items.GLOW_ITEM_FRAME; case "boat", "oak_boat" -> Items.OAK_BOAT;
            default -> id.getPath().endsWith("boat") || id.getPath().endsWith("raft") ? Items.OAK_BOAT : id.getPath().contains("minecart") ? Items.MINECART : Items.NAME_TAG;
        };
    }

    private int lw() { return Math.min(170, Math.round(width * 0.3f)); }
    private int rx() { return 16 + lw() + 14; }
    private int rw() { return width - rx() - 16; }
    private int gridTop() { return 112; }
    private int cols() { return Math.max(1, (rw() + 4) / (TILE_W + 4)); }
    private List<EntityType<?>> filtered() {
        String q = search.toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return entities;
        List<EntityType<?>> l = new ArrayList<>();
        for (EntityType<?> t : entities) if (t.getName().getString().toLowerCase(Locale.ROOT).contains(q) || Registries.ENTITY_TYPE.getId(t).getPath().contains(q)) l.add(t);
        return l;
    }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        super.render(c, mx, my, delta);
        c.fillGradient(0, 0, width, height, 0xC0050310, 0xE0050310);
        boolean on = Troll.enabled();
        bold(c, "Troll Mod", 16, 12, 0xFFFFFFFF, true);
        text(c, fit("Only you see this — everyone else sees nothing different", width - 200, false), 16, 24, MUTED, false);
        String tg = on ? "On" : "Off — click to turn on";
        int tw = width(tg) + 18;
        round(c, width - 16 - tw, 10, tw, 18, 9, on ? withAlpha(0x4ADE80, 210) : in(mx, my, width - 16 - tw, 10, tw, 18) ? 0x55FF6B6B : 0x33FF6B6B);
        text(c, tg, width - 16 - tw + 9, 15, on ? 0xFF0B2414 : 0xFFFFC2C2, false);

        // ------------- players
        int lw = lw(), ly = 44;
        text(c, "Players", 16, ly, FAINT, false);
        List<String> ts = targets();
        int rowsTop = ly + 12, rowsBottom = height - 52;
        c.enableScissor(0, rowsTop, 16 + lw + 1, rowsBottom);
        int y = rowsTop - Math.round(listScroll);
        for (String k : ts) {
            boolean sel = k.equalsIgnoreCase(selected), h = in(mx, my, 16, y, lw, 26) && my >= rowsTop && my < rowsBottom;
            round(c, 16, y, lw, 26, 8, sel ? withAlpha(accent(), 110) : h ? 0x2AFFFFFF : 0x14FFFFFF);
            bold(c, fit(label(k), lw - 16, true), 24, y + 4, 0xFFFFFFFF, false);
            Troll.Prank p = Troll.of(k);
            text(c, fit(status(k), lw - 16, false), 24, y + 15, p.kind == Troll.Kind.NONE ? FAINT : 0xFFFFD27A, false);
            y += 30;
        }
        c.disableScissor();
        listScroll = Math.max(0, Math.min(listScroll, Math.max(0, ts.size() * 30 - (rowsBottom - rowsTop))));
        // add someone who isn't online
        round(c, 16, height - 46, lw, 20, 10, focus.equals("name") ? 0x33FFFFFF : 0x1AFFFFFF);
        boolean caret = (System.currentTimeMillis() / 500) % 2 == 0;
        text(c, nameInput.isEmpty() && !focus.equals("name") ? "+ Type a player name…" : nameInput + (focus.equals("name") && caret ? "_" : ""), 24, height - 40, nameInput.isEmpty() && !focus.equals("name") ? FAINT : TEXT, false);

        // ------------- prank for the selected target
        int rx = rx(), rw = rw();
        Troll.Prank cur = Troll.of(selected);
        bold(c, fit(label(selected), rw - 10, true), rx, 44, 0xFFFFFFFF, false);
        text(c, selected.equals(Troll.EVERYONE) ? "Applies to every player you haven't set on their own" : "Only this player", rx, 56, MUTED, false);
        String[] kinds = {"Normal", "Invisible", "Disguise"};
        int kx = rx;
        for (int i = 0; i < 3; i++) {
            int w = width(kinds[i]) + 22;
            boolean a = cur.kind.ordinal() == i, h = in(mx, my, kx, 70, w, 20);
            round(c, kx, 70, w, 20, 10, a ? 0xF2FFFFFF : h ? 0x36FFFFFF : 0x1AFFFFFF);
            centered(c, kinds[i], kx + w / 2f, 76, a ? 0xFF140A28 : TEXT, false);
            kx += w + 5;
        }
        if (cur.kind == Troll.Kind.INVISIBLE) {
            text(c, fit("Completely gone: body, name tag, armor, items and shadow are hidden.", rw, false), rx, 98, MUTED, false);
            text(c, fit("Pick Disguise to show them as a mob instead.", rw, false), rx, 110, FAINT, false);
        }
        // entity picker (picking one switches to Disguise)
        int sw = Math.min(150, rw / 2);
        if (cur.kind != Troll.Kind.INVISIBLE) {
        text(c, fit(cur.kind == Troll.Kind.DISGUISE ? "Shown as: " + Troll.name(cur.entity) : "Pick a mob to disguise as:", rw - sw - 10, false), rx, 96, cur.kind == Troll.Kind.DISGUISE ? 0xFFFFD27A : FAINT, false);
        round(c, rx + rw - sw, 92, sw, 16, 8, focus.equals("search") ? 0x33FFFFFF : 0x1AFFFFFF);
        text(c, search.isEmpty() && !focus.equals("search") ? "Search mobs…" : search + (focus.equals("search") && caret ? "_" : ""), rx + rw - sw + 8, 96, search.isEmpty() && !focus.equals("search") ? FAINT : TEXT, false);
            List<EntityType<?>> list = filtered();
            int cols = cols(), gt = gridTop();
            scroll = approach(scroll, scrollTarget, 16, 1 / 60f);
            c.enableScissor(rx, gt, rx + rw, height - 14);
            for (int i = 0; i < list.size(); i++) {
                int tx = rx + (i % cols) * (TILE_W + 4), ty = gt + (i / cols) * (TILE_H + 4) - Math.round(scroll);
                if (ty > height || ty + TILE_H < gt) continue;
                EntityType<?> t = list.get(i);
                String id = Registries.ENTITY_TYPE.getId(t).toString();
                boolean a = cur.kind == Troll.Kind.DISGUISE && id.equals(cur.entity), h = in(mx, my, tx, ty, TILE_W, TILE_H) && my >= gt;
                round(c, tx, ty, TILE_W, TILE_H, 7, a ? withAlpha(accent(), 220) : h ? 0x30FFFFFF : 0x16FFFFFF);
                item(c, egg(t), tx + 3, ty + 3, 16);
                text(c, fit(t.getName().getString(), TILE_W - 26, false), tx + 22, ty + 7, a ? 0xFF140A28 : TEXT, false);
            }
            c.disableScissor();
            int rows = (list.size() + cols - 1) / cols;
            scrollTarget = Math.max(0, Math.min(scrollTarget, Math.max(0, rows * (TILE_H + 4) - (height - 14 - gt))));
        }
        SpaceBackground.button(c, 16 + lw - 50, height - 22, 50, 16, "Done", mx, my, in(mx, my, 16 + lw - 50, height - 22, 50, 16) ? 1 : 0, true);
        if (Troll.count() > 0) text(c, Troll.count() + " active", 16, height - 18, FAINT, false);
    }

    @Override protected boolean onClick(double mx, double my, int button) {
        String tg = Troll.enabled() ? "On" : "Off — click to turn on";
        int tw = width(tg) + 18;
        if (in(mx, my, width - 16 - tw, 10, tw, 18)) {
            var m = OrbitClient.modules().get("troll");
            m.setEnabled(!m.isEnabled()); OrbitClient.modules().save(); Sounds.toggle(m.isEnabled()); return true;
        }
        int lw = lw();
        if (in(mx, my, 16 + lw - 50, height - 22, 50, 16)) { Sounds.click(); close(); return true; }
        if (in(mx, my, 16, height - 46, lw, 20)) { focus = "name"; return true; }
        int rx = rx(), rw = rw(), sw = Math.min(150, rw / 2);
        if (Troll.of(selected).kind != Troll.Kind.INVISIBLE && in(mx, my, rx + rw - sw, 92, sw, 16)) { focus = "search"; return true; }
        focus = "";
        // players
        int rowsTop = 56, rowsBottom = height - 52, y = rowsTop - Math.round(listScroll);
        if (my >= rowsTop && my < rowsBottom) for (String k : targets()) {
            if (in(mx, my, 16, y, lw, 26)) { selected = k; scroll = scrollTarget = 0; Sounds.click(); return true; }
            y += 30;
        }
        // kinds
        Troll.Prank cur = Troll.of(selected);
        String[] kinds = {"Normal", "Invisible", "Disguise"};
        int kx = rx;
        for (int i = 0; i < 3; i++) {
            int w = width(kinds[i]) + 22;
            if (in(mx, my, kx, 70, w, 20)) { Troll.set(selected, Troll.Kind.values()[i], cur.entity); turnOn(); Sounds.toggle(i != 0); return true; }
            kx += w + 5;
        }
        // entity tiles
        if (cur.kind != Troll.Kind.INVISIBLE && my >= gridTop()) {
            List<EntityType<?>> list = filtered();
            int cols = cols();
            for (int i = 0; i < list.size(); i++) {
                int tx = rx + (i % cols) * (TILE_W + 4), ty = gridTop() + (i / cols) * (TILE_H + 4) - Math.round(scroll);
                if (in(mx, my, tx, ty, TILE_W, TILE_H)) {
                    Troll.set(selected, Troll.Kind.DISGUISE, Registries.ENTITY_TYPE.getId(list.get(i)).toString());
                    turnOn();
                    Sounds.success();
                    return true;
                }
            }
        }
        return false;
    }
    /** picking a prank turns the mod on */
    private void turnOn() { var m = OrbitClient.modules().get("troll"); if (!m.isEnabled()) { m.setEnabled(true); OrbitClient.modules().save(); } }

    @Override protected boolean onScroll(double mx, double my, double v) {
        if (mx < rx()) listScroll -= (float) (v * 30); else scrollTarget -= (float) (v * 30);
        return true;
    }
    @Override protected boolean onKey(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { if (!focus.isEmpty()) { focus = ""; return true; } close(); return true; }
        if (focus.equals("search")) { String e = TextKeys.edit(search, key, mods, 24); if (e != null) { search = e; scrollTarget = 0; return true; } }
        if (focus.equals("name")) { String e = TextKeys.edit(nameInput, key, mods, 16); if (e != null) { nameInput = e.replaceAll("[^A-Za-z0-9_]", ""); return true; } }
        if (focus.equals("search") && key == GLFW.GLFW_KEY_BACKSPACE && !search.isEmpty()) { search = search.substring(0, search.length() - 1); scrollTarget = 0; return true; }
        if (focus.equals("name")) {
            if (key == GLFW.GLFW_KEY_BACKSPACE && !nameInput.isEmpty()) { nameInput = nameInput.substring(0, nameInput.length() - 1); return true; }
            if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && !nameInput.isEmpty()) {
                extraNames.add(nameInput); selected = nameInput; nameInput = ""; focus = ""; Sounds.click(); return true;
            }
        }
        return false;
    }
    @Override protected boolean onChar(char chr) {
        if (!focus.isEmpty() && TextKeys.ctrl(0)) return true;
        if (focus.equals("search") && chr >= ' ' && search.length() < 24) { search += chr; scrollTarget = 0; return true; }
        if (focus.equals("name") && (Character.isLetterOrDigit(chr) || chr == '_') && nameInput.length() < 16) { nameInput += chr; return true; }
        return false;
    }
}
