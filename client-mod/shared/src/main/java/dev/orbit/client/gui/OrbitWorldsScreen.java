package dev.orbit.client.gui;

import dev.orbit.client.OrbitClient;
import dev.orbit.client.compat.Compat;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.level.storage.LevelSummary;
import org.lwjgl.glfw.GLFW;

import java.io.InputStream;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.orbit.client.gui.Render2D.*;

/** Clean singleplayer world list. Play, create, or fall back to vanilla for editing/deleting worlds. */
public final class OrbitWorldsScreen extends OrbitBaseScreen {
    private final Screen parent;
    private final long openedAt = System.currentTimeMillis();
    private List<LevelSummary> worlds;
    private String error, search = "";
    private float scroll, scrollTarget;
    private final Map<String, Identifier> icons = new HashMap<>();
    private final Map<Object, Float> hover = new HashMap<>();
    private static final int ROW = 40;

    public OrbitWorldsScreen(Screen parent) {
        super(Text.literal("Singleplayer"));
        this.parent = parent;
        try {
            var storage = net.minecraft.client.MinecraftClient.getInstance().getLevelStorage();
            storage.loadSummaries(storage.getLevelList()).whenComplete((list, err) -> {
                if (err != null) error = err.getMessage();
                else worlds = new ArrayList<>(list.stream().sorted((a, b) -> Long.compare(b.getLastPlayed(), a.getLastPlayed())).toList());
            });
        } catch (Exception e) { error = e.getMessage(); }
    }

    private int listX() { return (width - listW()) / 2; }
    private int listW() { return Math.min(460, width - 30); }
    private static final int TOP = 74;

    private List<LevelSummary> filtered() {
        if (worlds == null) return List.of();
        if (search.isEmpty()) return worlds;
        return worlds.stream().filter(w -> w.getDisplayName().toLowerCase().contains(search.toLowerCase())).toList();
    }

    private float h(Object k, boolean on) {
        float v = approach(hover.getOrDefault(k, 0f), on ? 1 : 0, 14, 1 / 60f);
        hover.put(k, v);
        return v;
    }

    @Override public void renderBackground(DrawContext c, int mx, int my, float delta) { SpaceBackground.draw(c, width, height, mx, my, false); }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        super.render(c, mx, my, delta);
        float t = easeOut((System.currentTimeMillis() - openedAt) / 400f);
        int L = listX(), W = listW();
        Compat.push(c);
        Compat.translate(c, L, 22 + (1 - t) * 8);
        Compat.scale(c, 2f);
        bold(c, "Singleplayer", 0, 0, alpha(0xFFFFFFFF, t), false);
        Compat.pop(c);
        text(c, worlds == null ? (error != null ? "Couldn't read worlds: " + error : "Loading worlds…") : worlds.size() + " world" + (worlds.size() == 1 ? "" : "s"), L, 44, MUTED, false);

        // search + actions
        int sw = 130;
        round(c, L, 54, sw, 16, 8, search.isEmpty() ? 0x44FFFFFF : withAlpha(accent(), 200));
        round(c, L + 1, 55, sw - 2, 14, 7, 0xE00A0818);
        text(c, search.isEmpty() ? "Search worlds" : search + ((System.currentTimeMillis() / 500) % 2 == 0 ? "_" : ""), L + 8, 58, search.isEmpty() ? FAINT : TEXT, false);
        SpaceBackground.button(c, L + W - 92, 52, 92, 18, "+ New world", mx, my, h("new", in(mx, my, L + W - 92, 52, 92, 18)), true);
        SpaceBackground.button(c, L + W - 190, 52, 92, 18, "Edit worlds", mx, my, h("vanilla", in(mx, my, L + W - 190, 52, 92, 18)), false);

        List<LevelSummary> list = filtered();
        scroll = approach(scroll, scrollTarget, 18, 1 / 60f);
        c.enableScissor(0, TOP, width, height - 26);
        for (int i = 0; i < list.size(); i++) {
            LevelSummary w = list.get(i);
            int y = TOP + 4 + i * (ROW + 6) - Math.round(scroll);
            if (y > height || y + ROW < TOP) continue;
            boolean hov = in(mx, my, L, y, W, ROW) && my > TOP && my < height - 26;
            float hv = h(w.getName(), hov);
            round(c, L, y, W, ROW, 8, lerpColor(0x30FFFFFF, 0x90FFFFFF, hv));
            round(c, L + 1, y + 1, W - 2, ROW - 2, 7, lerpColor(0xC80A0818, 0xE01B1636, hv));
            Identifier icon = icon(w);
            if (icon != null) Compat.blit(c, icon, L + 6, y + 4, 32, 32, 64, 64);
            else { round(c, L + 6, y + 4, 32, 32, 5, 0xFF1B1636); item(c, Items.GRASS_BLOCK, L + 14, y + 12, 16); }
            bold(c, w.getDisplayName(), L + 46, y + 8, 0xFFFFFFFF, false);
            String mode = w.isHardcore() ? "Hardcore" : cap(w.getGameMode().asString());
            String when = w.getLastPlayed() > 0 ? new SimpleDateFormat("d MMM yyyy, HH:mm").format(new Date(w.getLastPlayed())) : "never played";
            text(c, when + "  ·  " + mode + "  ·  " + w.getVersion().getString(), L + 46, y + 22, MUTED, false);
            if (hv > 0.05f) {
                int bx = L + W - 54;
                round(c, bx, y + 11, 46, 18, 9, alpha(0xFFF4F1FF, hv));
                centered(c, "Play ▶", bx + 23, y + 16, alpha(0xFF140A28, hv), false);
            }
        }
        c.disableScissor();
        if (worlds != null && list.isEmpty()) centered(c, worlds.isEmpty() ? "No worlds yet — create your first one" : "No world matches \"" + search + "\"", width / 2f, TOP + 40, MUTED, false);
        float max = Math.max(0, list.size() * (ROW + 6) - (height - 26 - TOP));
        scrollTarget = Math.max(0, Math.min(max, scrollTarget));
        SpaceBackground.button(c, 10, height - 24, 60, 16, "‹ Back", mx, my, h("back", in(mx, my, 10, height - 24, 60, 16)), false);
    }

    private Identifier icon(LevelSummary w) {
        if (icons.containsKey(w.getName())) return icons.get(w.getName());
        Identifier id = null;
        try {
            var path = w.getIconPath();
            if (path != null && Files.isRegularFile(path)) {
                try (InputStream in = Files.newInputStream(path)) {
                    NativeImage img = NativeImage.read(in);
                    id = Compat.id("world/" + Integer.toHexString(w.getName().hashCode()));
                    client.getTextureManager().registerTexture(id, Compat.texture("orbit-world-icon", img, false));
                }
            }
        } catch (Exception e) { OrbitClient.LOG.debug("World icon failed", e); }
        icons.put(w.getName(), id);
        return id;
    }

    private static String cap(String s) { return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }
    private static boolean in(double mx, double my, double x, double y, double w, double h) { return mx >= x && my >= y && mx < x + w && my < y + h; }

    @Override
    protected boolean onClick(double mx, double my, int button) {
        int L = listX(), W = listW();
        if (in(mx, my, 10, height - 24, 60, 16)) { close(); return true; }
        if (in(mx, my, L + W - 92, 52, 92, 18)) { Compat.createWorld(this); return true; }
        if (in(mx, my, L + W - 190, 52, 92, 18)) { client.setScreen(new SelectWorldScreen(this)); return true; }
        if (my < TOP || my > height - 26) return false;
        List<LevelSummary> list = filtered();
        for (int i = 0; i < list.size(); i++) {
            int y = TOP + 4 + i * (ROW + 6) - Math.round(scroll);
            if (in(mx, my, L, y, W, ROW)) {
                LevelSummary w = list.get(i);
                if (w.isLocked()) return true;
                Compat.joinWorld(w.getName(), this);
                return true;
            }
        }
        return false;
    }

    @Override protected boolean onScroll(double mx, double my, double v) { scrollTarget -= (float) (v * 30); return true; }
    @Override protected boolean onKey(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { if (!search.isEmpty()) search = ""; else close(); return true; }
        if (key == GLFW.GLFW_KEY_BACKSPACE && !search.isEmpty()) { search = search.substring(0, search.length() - 1); return true; }
        return false;
    }
    @Override protected boolean onChar(char chr) { if (chr >= ' ' && search.length() < 30) { search += chr; scrollTarget = 0; return true; } return false; }
    @Override public void close() { client.setScreen(parent); }
}
