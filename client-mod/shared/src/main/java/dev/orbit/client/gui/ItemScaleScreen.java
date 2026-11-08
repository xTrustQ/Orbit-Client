package dev.orbit.client.gui;

import dev.orbit.client.itemscale.ItemScaleItems;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static dev.orbit.client.gui.Render2D.*;

/**
 * Item Scale → choose items: search every item, click to pick it, scroll on a picked item to give it its own size.
 */
public final class ItemScaleScreen extends OrbitBaseScreen {
    private static final int CELL = 24;
    private final Screen parent;
    private final List<Item> all = new ArrayList<>();
    private List<Item> shown = new ArrayList<>();
    private String search = "";
    private boolean onlyChosen;
    private float scroll, scrollTarget;

    public ItemScaleScreen(Screen parent) {
        super(Text.literal("Item Scale"));
        this.parent = parent;
        for (Item i : Registries.ITEM) if (i != Items.AIR) all.add(i);
        refilter();
    }
    @Override public boolean shouldPause() { return false; }
    @Override public void close() { client.setScreen(parent); }

    private void refilter() {
        String q = search.toLowerCase(Locale.ROOT);
        shown = new ArrayList<>();
        for (Item i : all) {
            String id = Registries.ITEM.getId(i).toString();
            if (onlyChosen && !ItemScaleItems.isChosen(id)) continue;
            if (!q.isEmpty() && !id.contains(q) && !i.getName().getString().toLowerCase(Locale.ROOT).contains(q)) continue;
            shown.add(i);
        }
        // picked items first
        shown.sort((a, b) -> Boolean.compare(ItemScaleItems.isChosen(Registries.ITEM.getId(b).toString()), ItemScaleItems.isChosen(Registries.ITEM.getId(a).toString())));
    }

    private int pw() { return Math.min(520, width - 30); }
    private int px() { return (width - pw()) / 2; }
    private int cols() { return Math.max(1, pw() / CELL); }
    private int top() { return 70; }
    private static boolean in(double mx, double my, double x, double y, double w, double h) { return mx >= x && my >= y && mx < x + w && my < y + h; }

    private int hoveredIndex(double mx, double my) {
        if (my < top() || my > height - 28) return -1;
        int col = (int) ((mx - px()) / CELL), row = (int) ((my - top() + scroll) / CELL);
        if (mx < px() || col >= cols()) return -1;
        int i = row * cols() + col;
        return i >= 0 && i < shown.size() ? i : -1;
    }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        super.render(c, mx, my, delta);
        c.fillGradient(0, 0, width, height, 0xB0050310, 0xD0050310);
        int px = px(), pw = pw();
        bold(c, "Item Scale — choose items", px, 14, 0xFFFFFFFF, true);
        text(c, fit("Click to pick an item · scroll on a picked item for its own size · \"Which items\" in the module decides how picks are used", pw, false), px, 26, MUTED, false);
        // search + filter chip
        round(c, px, 40, pw - 120, 20, 10, 0x2AFFFFFF);
        text(c, search.isEmpty() ? "Search items…" : search + ((System.currentTimeMillis() / 500) % 2 == 0 ? "_" : ""), px + 10, 46, search.isEmpty() ? FAINT : TEXT, false);
        String chip = onlyChosen ? "Picked (" + ItemScaleItems.chosen().size() + ")" : "All items";
        round(c, px + pw - 114, 40, 114, 20, 10, onlyChosen ? withAlpha(accent(), 220) : in(mx, my, px + pw - 114, 40, 114, 20) ? 0x40FFFFFF : 0x22FFFFFF);
        centered(c, chip, px + pw - 57, 46, onlyChosen ? 0xFF140A28 : TEXT, false);

        scroll = approach(scroll, scrollTarget, 16, 1 / 60f);
        int cols = cols(), top = top();
        int hov = hoveredIndex(mx, my);
        c.enableScissor(0, top, width, height - 28);
        int firstRow = Math.max(0, (int) (scroll / CELL) - 1), lastRow = (int) ((scroll + height) / CELL) + 1;
        for (int r = firstRow; r <= lastRow; r++) for (int k = 0; k < cols; k++) {
            int i = r * cols + k;
            if (i >= shown.size()) break;
            Item it = shown.get(i);
            String id = Registries.ITEM.getId(it).toString();
            boolean picked = ItemScaleItems.isChosen(id);
            int x = px + k * CELL, y = top + r * CELL - Math.round(scroll);
            round(c, x + 1, y + 1, CELL - 2, CELL - 2, 6, picked ? withAlpha(accent(), i == hov ? 255 : 200) : i == hov ? 0x40FFFFFF : 0x14FFFFFF);
            if (picked) round(c, x + 2.5f, y + 2.5f, CELL - 5, CELL - 5, 5, 0xE0141026);
            item(c, it, x + 4, y + 4, 16);
            float own = ItemScaleItems.own(id);
            if (picked && own > 0) { String s = trim(own); text(c, s, x + CELL - width(s) - 1, y + CELL - 9, 0xFFFFE08A, true); }
        }
        c.disableScissor();
        int rows = (shown.size() + cols - 1) / cols;
        scrollTarget = Math.max(0, Math.min(Math.max(0, rows * CELL - (height - 28 - top)), scrollTarget));
        if (shown.isEmpty()) centered(c, onlyChosen ? "Nothing picked yet" : "No items found", width / 2f, top + 30, FAINT, false);
        if (hov >= 0) { // tooltip line at the bottom
            Item it = shown.get(hov);
            String id = Registries.ITEM.getId(it).toString();
            float own = ItemScaleItems.own(id);
            String info = it.getName().getString() + "  ·  " + id + (ItemScaleItems.isChosen(id) ? "  ·  " + (own > 0 ? "own size " + trim(own) : "normal size (scroll to change)") : "");
            text(c, fit(info, pw - 80, false), px, height - 20, TEXT, false);
        }
        SpaceBackground.button(c, px + pw - 70, height - 24, 70, 18, "Done", mx, my, in(mx, my, px + pw - 70, height - 24, 70, 18) ? 1 : 0, true);
    }

    private static String trim(float f) { return (f == Math.round(f) ? String.valueOf((int) f) : String.format(Locale.ROOT, "%.2f", f).replaceAll("0$", "")) + "x"; }

    @Override protected boolean onClick(double mx, double my, int button) {
        int px = px(), pw = pw();
        if (in(mx, my, px + pw - 70, height - 24, 70, 18)) { Sounds.click(); close(); return true; }
        if (in(mx, my, px + pw - 114, 40, 114, 20)) { onlyChosen = !onlyChosen; scrollTarget = 0; refilter(); Sounds.tab(); return true; }
        int i = hoveredIndex(mx, my);
        if (i >= 0) {
            String id = Registries.ITEM.getId(shown.get(i)).toString();
            ItemScaleItems.toggle(id);
            Sounds.toggle(ItemScaleItems.isChosen(id));
            if (onlyChosen) refilter();
            return true;
        }
        return false;
    }

    @Override protected boolean onScroll(double mx, double my, double v) {
        int i = hoveredIndex(mx, my);
        if (i >= 0) {
            String id = Registries.ITEM.getId(shown.get(i)).toString();
            if (ItemScaleItems.isChosen(id)) {
                float own = ItemScaleItems.own(id), next = own <= 0 ? (v > 0 ? 1.5f : 0.75f) : own + (float) Math.signum(v) * 0.25f;
                if (next < 0.25f) next = 0; // below the smallest size = back to normal
                ItemScaleItems.setOwn(id, Math.min(5f, next));
                return true;
            }
        }
        scrollTarget -= (float) (v * CELL * 2);
        return true;
    }

    @Override protected boolean onKey(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { close(); return true; }
        String e = TextKeys.edit(search, key, mods, 30);
        if (e != null) { search = e; scrollTarget = 0; refilter(); return true; }
        if (key == GLFW.GLFW_KEY_BACKSPACE && !search.isEmpty()) { search = search.substring(0, search.length() - 1); scrollTarget = 0; refilter(); return true; }
        return false;
    }
    @Override protected boolean onChar(char chr) {
        if (TextKeys.ctrl(0)) return true;
        if (chr >= ' ' && search.length() < 30) { search += chr; scrollTarget = 0; refilter(); return true; }
        return false;
    }
}
