package dev.orbit.client.gui;

import dev.orbit.client.OrbitClient;
import dev.orbit.client.compat.Compat;
import net.minecraft.client.MinecraftClient;

import java.awt.image.BufferedImage;
import java.io.InputStream;

/**
 * The 9x9 shape of the vanilla full hunger icon, read from the active texture (so resource packs
 * line up too). Used by the Saturation mod to draw AppleSkin-style outlines on the exact shank pixels.
 */
public final class FoodIcon {
    private FoodIcon() {}
    private static boolean[] mask;
    private static long loadedAt;

    public static boolean[] mask() {
        // re-read now and then so a resource-pack change is picked up
        if (mask == null || System.currentTimeMillis() - loadedAt > 30_000) { mask = load(); loadedAt = System.currentTimeMillis(); }
        return mask;
    }

    private static boolean[] load() {
        boolean[] m = read("textures/gui/sprites/hud/food_full.png", 0, 0, 9, 9);   // 1.20.2+
        if (m == null) m = read("textures/gui/icons.png", 52, 27, 9, 9);          // 1.20.1 sheet (256x256)
        return m != null ? m : fallback();
    }

    private static boolean[] read(String path, int u, int v, int w, int h) {
        try {
            var res = MinecraftClient.getInstance().getResourceManager().getResource(Compat.vanilla(path));
            if (res.isEmpty()) return null;
            BufferedImage img;
            try (InputStream in = res.get().getInputStream()) { img = javax.imageio.ImageIO.read(in); }
            if (img == null) return null;
            double scale = path.endsWith("icons.png") ? img.getWidth() / 256.0 : img.getWidth() / (double) w;
            boolean[] m = new boolean[81];
            int count = 0;
            for (int y = 0; y < 9; y++) for (int x = 0; x < 9; x++) {
                int px = (int) ((u + x + 0.5) * scale), py = (int) ((v + y + 0.5) * scale);
                if (px >= img.getWidth() || py >= img.getHeight()) continue;
                m[y * 9 + x] = (img.getRGB(px, py) >>> 24) > 40;
                if (m[y * 9 + x]) count++;
            }
            return count > 10 ? m : null;
        } catch (Exception e) {
            OrbitClient.LOG.debug("Could not read food icon {}", path, e);
            return null;
        }
    }

    /** A plain drumstick if no texture could be read. */
    private static boolean[] fallback() {
        String[] rows = {
                "....####.",
                "...######",
                "..#######",
                "..#######",
                "...######",
                "..##.###.",
                ".##......",
                "##.......",
                ".#......."};
        boolean[] m = new boolean[81];
        for (int y = 0; y < 9; y++) for (int x = 0; x < 9; x++) m[y * 9 + x] = rows[y].charAt(x) == '#';
        return m;
    }
}
