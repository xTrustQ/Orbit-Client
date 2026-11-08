package dev.orbit.client.gui;

import dev.orbit.client.OrbitClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/** Small 2D drawing toolkit: rounded rects, colours, chroma text, easing. */
public final class Render2D {
    private Render2D() {}

    public static TextRenderer font() { return MinecraftClient.getInstance().textRenderer; }

    // ------------------------------------------------------------ colours
    public static int alpha(int argb, float a) { return ((int) (Math.max(0, Math.min(1, a)) * ((argb >>> 24) & 0xFF)) << 24) | (argb & 0xFFFFFF); }
    /** global opacity for everything drawn with these helpers (screen transitions); keep at 1 otherwise */
    public static float fade = 1;
    private static int f(int argb) { return fade >= 1 ? argb : alpha(argb, fade); }
    public static int withAlpha(int rgb, int a) { return (Math.max(0, Math.min(255, a)) << 24) | (rgb & 0xFFFFFF); }
    public static int lerpColor(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int aa = a >>> 24, ar = a >> 16 & 255, ag = a >> 8 & 255, ab = a & 255;
        int ba = b >>> 24, br = b >> 16 & 255, bg = b >> 8 & 255, bb = b & 255;
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }
    public static int hsv(float h, float s, float v) { return 0xFF000000 | java.awt.Color.HSBtoRGB(h, s, v) & 0xFFFFFF; }
    public static int chroma(float offset, float speed) {
        float hue = ((System.currentTimeMillis() % (long) (10000 / speed)) / (10000f / speed) + offset) % 1f;
        return hsv(hue, 0.65f, 1f);
    }

    // theme accent (picked in the Interface module)
    public static int accent() { return OrbitClient.modules().ui().accent(); }
    public static int accent2() { return OrbitClient.modules().ui().accent2(); }
    /** Deep-space palette shared by every Orbit screen. */
    public static final int BG = 0xF00A0818, PANEL = 0xFF120E24, PANEL_2 = 0xFF1B1636, LINE = 0xFF2B2450, TEXT = 0xFFF1ECFF, MUTED = 0xFFA49CC4, FAINT = 0xFF6C6390;

    /** Filled circle (smooth). */
    public static void circle(DrawContext c, float cx, float cy, float r, int color) { disc(c, cx, cy, r, color); }

    /** A small ringed planet — the Orbit logo, drawn with smooth discs. */
    public static void logo(DrawContext c, float cx, float cy, float r) {
        int a = accent(), b = accent2();
        for (int i = 0; i < 360; i += 3) { // back half of the ring
            double t = Math.toRadians(i);
            if (Math.sin(t) < 0) ringDot(c, cx, cy, r, t, a);
        }
        disc(c, cx, cy, r, lerpColor(0xFFFFFFFF, a, 0.6f));
        disc(c, cx - r * 0.18f, cy + r * 0.2f, r * 0.78f, lerpColor(a, b, 0.5f));
        disc(c, cx - r * 0.35f, cy - r * 0.35f, r * 0.28f, 0x66FFFFFF);
        for (int i = 0; i < 360; i += 3) { // front half
            double t = Math.toRadians(i);
            if (Math.sin(t) >= 0) ringDot(c, cx, cy, r, t, a);
        }
    }
    private static void ringDot(DrawContext c, float cx, float cy, float r, double t, int color) {
        double x = Math.cos(t) * r * 1.9, y = Math.sin(t) * r * 0.5;
        double rx = x * Math.cos(-0.35) - y * Math.sin(-0.35), ry = x * Math.sin(-0.35) + y * Math.cos(-0.35);
        disc(c, cx + (float) rx, cy + (float) ry, Math.max(0.45f, r * 0.07f), color);
    }

    // ------------------------------------------------------------ shapes
    public static void rect(DrawContext c, float x, float y, float w, float h, int color) {
        c.fill(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h), color);
    }

    /** Rounded rectangle (see roundF). */
    public static void round(DrawContext c, float xf, float yf, float wf, float hf, int r, int color) {
        roundF(c, xf, yf, wf, hf, r, f(color));
    }

    // 128x128 white anti-aliased circle, linear-filtered; each quadrant is one rounded corner.
    private static final int CIRCLE = 128;
    private static net.minecraft.util.Identifier circleTex;
    private static boolean circleFailed;
    private static net.minecraft.util.Identifier circleTexture() {
        if (circleTex != null || circleFailed) return circleTex;
        try {
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(CIRCLE, CIRCLE, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            double rad = CIRCLE / 2.0;
            for (int y = 0; y < CIRCLE; y++) for (int x = 0; x < CIRCLE; x++) {
                int hits = 0; // 4x4 supersampled coverage
                for (int sy = 0; sy < 4; sy++) for (int sx = 0; sx < 4; sx++) {
                    double dx = x + (sx + 0.5) / 4 - rad, dy = y + (sy + 0.5) / 4 - rad;
                    if (dx * dx + dy * dy <= rad * rad) hits++;
                }
                img.setRGB(x, y, (Math.round(hits / 16f * 255) << 24) | 0xFFFFFF);
            }
            java.io.ByteArrayOutputStream png = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(img, "png", png);
            var ni = net.minecraft.client.texture.NativeImage.read(png.toByteArray());
            var id = dev.orbit.client.compat.Compat.id("ui/circle");
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, dev.orbit.client.compat.Compat.texture("orbit-circle", ni, true));
            circleTex = id;
        } catch (Exception e) {
            circleFailed = true;
            OrbitClient.LOG.warn("Could not build the rounded-corner texture, using square corners", e);
        }
        return circleTex;
    }

    /**
     * Rounded rectangle: 3 fills for the body plus 4 corners cut from a smooth circle texture.
     * Anti-aliased at any GUI scale and ~7 draw calls (the old scanline version needed hundreds,
     * which is what made the HUD and menu lag).
     */
    public static void roundF(DrawContext c, float xf, float yf, float wf, float hf, float r, int color) {
        if (wf <= 0 || hf <= 0 || (color >>> 24) == 0) return;
        int w = Math.max(1, Math.round(wf)), h = Math.max(1, Math.round(hf));
        int R = Math.min(Math.round(r), Math.min(w, h) / 2);
        var tex = R >= 1 ? circleTexture() : null;
        dev.orbit.client.compat.Compat.push(c);
        dev.orbit.client.compat.Compat.translate(c, xf, yf);
        if (tex == null) c.fill(0, 0, w, h, color);
        else {
            c.fill(R, 0, w - R, h, color);
            if (h > 2 * R) { c.fill(0, R, R, h - R, color); c.fill(w - R, R, w, h - R, color); }
            int half = CIRCLE / 2;
            dev.orbit.client.compat.Compat.blitTint(c, tex, 0, 0, R, R, 0, 0, half, half, CIRCLE, CIRCLE, color);
            dev.orbit.client.compat.Compat.blitTint(c, tex, w - R, 0, R, R, half, 0, half, half, CIRCLE, CIRCLE, color);
            dev.orbit.client.compat.Compat.blitTint(c, tex, 0, h - R, R, R, 0, half, half, half, CIRCLE, CIRCLE, color);
            dev.orbit.client.compat.Compat.blitTint(c, tex, w - R, h - R, R, R, half, half, half, half, CIRCLE, CIRCLE, color);
        }
        dev.orbit.client.compat.Compat.pop(c);
    }

    // ------------------------------------------------------------ smooth icons (generated, anti-aliased)
    private static final java.util.Map<String, net.minecraft.util.Identifier> ICONS = new java.util.HashMap<>();
    private interface Shape { boolean in(double x, double y); }
    private static boolean tri(double x, double y, double ax, double ay, double bx, double by, double cx, double cy) {
        double d1 = (x - bx) * (ay - by) - (ax - bx) * (y - by), d2 = (x - cx) * (by - cy) - (bx - cx) * (y - cy), d3 = (x - ax) * (cy - ay) - (cx - ax) * (y - ay);
        boolean neg = d1 < 0 || d2 < 0 || d3 < 0, pos = d1 > 0 || d2 > 0 || d3 > 0;
        return !(neg && pos);
    }
    private static boolean box(double x, double y, double x0, double y0, double x1, double y1) { return x >= x0 && x <= x1 && y >= y0 && y <= y1; }
    private static Shape shape(String name) {
        return switch (name) {
            case "play" -> (x, y) -> tri(x, y, 0.22, 0.1, 0.22, 0.9, 0.9, 0.5);
            case "pause" -> (x, y) -> box(x, y, 0.2, 0.12, 0.4, 0.88) || box(x, y, 0.6, 0.12, 0.8, 0.88);
            case "next" -> (x, y) -> tri(x, y, 0.1, 0.14, 0.1, 0.86, 0.66, 0.5) || box(x, y, 0.7, 0.14, 0.84, 0.86);
            case "prev" -> (x, y) -> tri(x, y, 0.9, 0.14, 0.9, 0.86, 0.34, 0.5) || box(x, y, 0.16, 0.14, 0.3, 0.86);
            // area outside a rounded corner: top-left, top-right, bottom-left, bottom-right
            case "corner0" -> (x, y) -> (x - 1) * (x - 1) + (y - 1) * (y - 1) > 1;
            case "corner1" -> (x, y) -> x * x + (y - 1) * (y - 1) > 1;
            case "corner2" -> (x, y) -> (x - 1) * (x - 1) + y * y > 1;
            case "corner3" -> (x, y) -> x * x + y * y > 1;
            default -> (x, y) -> false;
        };
    }
    private static net.minecraft.util.Identifier iconTexture(String name) {
        if (ICONS.containsKey(name)) return ICONS.get(name);
        net.minecraft.util.Identifier id = null;
        try {
            int n = 64;
            Shape sh = shape(name);
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(n, n, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) {
                int hits = 0;
                for (int sy = 0; sy < 4; sy++) for (int sx = 0; sx < 4; sx++) if (sh.in((x + (sx + 0.5) / 4) / n, (y + (sy + 0.5) / 4) / n)) hits++;
                img.setRGB(x, y, (Math.round(hits / 16f * 255) << 24) | 0xFFFFFF);
            }
            java.io.ByteArrayOutputStream png = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(img, "png", png);
            id = dev.orbit.client.compat.Compat.id("ui/icon_" + name);
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, dev.orbit.client.compat.Compat.texture("orbit-icon-" + name, net.minecraft.client.texture.NativeImage.read(png.toByteArray()), true));
        } catch (Exception e) {
            OrbitClient.LOG.warn("Could not build icon {}", name, e);
        }
        ICONS.put(name, id);
        return id;
    }
    /** Smooth icon ("play", "pause", "prev", "next") of the given size, tinted. */
    public static void icon(DrawContext c, String name, float x, float y, float size, int color) {
        var tex = iconTexture(name);
        if (tex == null) return;
        dev.orbit.client.compat.Compat.push(c);
        dev.orbit.client.compat.Compat.translate(c, x, y);
        dev.orbit.client.compat.Compat.scale(c, size / 32f);
        dev.orbit.client.compat.Compat.blitTint(c, tex, 0, 0, 32, 32, 0, 0, 64, 64, 64, 64, color);
        dev.orbit.client.compat.Compat.pop(c);
    }
    /**
     * Rounds the corners of something already drawn (e.g. album art) by painting the area outside
     * each corner in the background colour.
     */
    public static void cutCorners(DrawContext c, float x, float y, float w, float h, float r, int bg) {
        float[][] pos = {{x, y}, {x + w - r, y}, {x, y + h - r}, {x + w - r, y + h - r}};
        for (int i = 0; i < 4; i++) icon(c, "corner" + i, pos[i][0], pos[i][1], r, bg);
    }

    /** Smooth filled disc of any (fractional) radius. */
    public static void disc(DrawContext c, float cx, float cy, float radius, int color) {
        color = f(color);
        if (radius <= 0 || (color >>> 24) == 0) return;
        var tex = circleTexture();
        float d = radius * 2;
        int size = 64;
        dev.orbit.client.compat.Compat.push(c);
        dev.orbit.client.compat.Compat.translate(c, cx - radius, cy - radius);
        dev.orbit.client.compat.Compat.scale(c, d / size);
        if (tex == null) c.fill(0, 0, size, size, color);
        else dev.orbit.client.compat.Compat.blitTint(c, tex, 0, 0, size, size, 0, 0, CIRCLE, CIRCLE, CIRCLE, CIRCLE, color);
        dev.orbit.client.compat.Compat.pop(c);
    }

    public static void roundOutline(DrawContext c, float x, float y, float w, float h, int r, int color) {
        round(c, x, y, w, h, r, color);
    }

    public static void outline(DrawContext c, int x, int y, int w, int h, int color) {
        c.fill(x, y, x + w, y + 1, color);
        c.fill(x, y + h - 1, x + w, y + h, color);
        c.fill(x, y + 1, x + 1, y + h - 1, color);
        c.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    /** Bordered rounded panel: draws border colour then the fill inset by 1px. */
    public static void panel(DrawContext c, float x, float y, float w, float h, int r, int fill, int border) {
        round(c, x, y, w, h, r, border);
        round(c, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), fill);
    }

    // ------------------------------------------------------------ text
    // Smooth fonts bundled in assets/orbit/font (Montserrat, Minecraft's font as fallback for other scripts).
    // There is one copy per oversample 1..8: text is drawn with the copy whose resolution matches the
    // real on-screen size (GUI scale x current matrix scale), so glyphs map 1:1 to screen pixels —
    // anti-aliased and sharp instead of blurry/jagged.
    private static final String[] KINDS = {"ui", "ui_bold", "display"};
    private static final net.minecraft.text.Style[][] STYLES = new net.minecraft.text.Style[3][9];
    /** true while Orbit draws the in-game HUD */
    public static boolean hudPass;
    public static boolean smooth() {
        // Orbit's menus (title screen, servers, worlds, locker…) always use the smooth font so they look modern;
        // the in-game HUD follows the "Smooth font" setting (Minecraft font by default).
        MinecraftClient mc = MinecraftClient.getInstance();
        // every Orbit menu (also in-game: pause menu, Troll Mod, waypoints…) uses the smooth font
        if (!hudPass && mc.currentScreen instanceof OrbitBaseScreen) return true;
        try { return OrbitClient.modules().ui().smoothFont.on(); } catch (Throwable t) { return true; }
    }
    private static double guiScale() { return Math.max(1, MinecraftClient.getInstance().getWindow().getScaleFactor()); }
    private static int oversample(DrawContext c) {
        float m = c == null ? 1f : dev.orbit.client.compat.Compat.matScale(c);
        return Math.max(1, Math.min(8, (int) Math.round(guiScale() * m)));
    }
    private static net.minecraft.text.Style style(int kind, int os) {
        net.minecraft.text.Style s = STYLES[kind][os];
        if (s == null) s = STYLES[kind][os] = dev.orbit.client.compat.Compat.font(dev.orbit.client.compat.Compat.id(KINDS[kind] + "_" + os));
        return s;
    }
    private static net.minecraft.text.Text styled(String s, int kind, DrawContext c) {
        if (kind < 2 && !smooth()) {
            net.minecraft.text.MutableText t = net.minecraft.text.Text.literal(s);
            return kind == 1 ? t.formatted(net.minecraft.util.Formatting.BOLD) : t;
        }
        return net.minecraft.text.Text.literal(s).setStyle(style(kind, oversample(c)));
    }
    // widths don't depend on the oversample, so they are measured once and cached
    private static final java.util.Map<String, Integer> WIDTHS = new java.util.LinkedHashMap<>(512, 0.75f, true) {
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<String, Integer> e) { return size() > 4000; }
    };
    private static int measure(String s, int kind) {
        String key = (smooth() ? kind : kind + 3) + s;
        Integer w = WIDTHS.get(key);
        if (w == null) { w = font().getWidth(styled(s, kind, null)); WIDTHS.put(key, w); }
        return w;
    }

    /** Regular text in the current Orbit font. */
    public static net.minecraft.text.Text t(String s) { return styled(s, 0, null); }
    public static int width(String s) { return measure(s, 0); }

    // bold text
    public static net.minecraft.text.Text boldText(String s) { return styled(s, 1, null); }
    public static int boldWidth(String s) { return measure(s, 1); }
    public static void bold(DrawContext c, String s, float x, float y, int color, boolean shadow) {
        color = f(color);
        if ((color >>> 24) < 8) return;
        c.drawText(font(), styled(s, 1, c), Math.round(x), Math.round(y), color, shadow);
    }

    /** Big heavy text (sky lyrics, headings) — always the smooth display font. */
    public static net.minecraft.text.Text displayText(String s) { return styled(s, 2, null); }
    public static int displayWidth(String s) { return measure(s, 2); }
    public static void display(DrawContext c, String s, float x, float y, int color, boolean shadow) {
        color = f(color);
        if ((color >>> 24) < 8) return;
        c.drawText(font(), styled(s, 2, c), Math.round(x), Math.round(y), color, shadow);
    }

    /** Shortens text with "…" to fit a width (kind 0 regular, 1 bold); cached. */
    private static final java.util.Map<String, String> FITS = new java.util.LinkedHashMap<>(256, 0.75f, true) {
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<String, String> e) { return size() > 1000; }
    };
    public static String fit(String s, int max, boolean bold) {
        int kind = bold ? 1 : 0;
        String key = (smooth() ? "s" : "p") + kind + ":" + max + ":" + s;
        String r = FITS.get(key);
        if (r != null) return r;
        r = s;
        if (measure(s, kind) > max) {
            int lo = 0, hi = s.length();
            while (lo < hi) { int mid = (lo + hi + 1) / 2; if (measure(s.substring(0, mid) + "…", kind) <= max) lo = mid; else hi = mid - 1; }
            r = s.substring(0, lo).stripTrailing() + "…";
        }
        FITS.put(key, r);
        return r;
    }

    /** Draws an item icon at x,y scaled to `size` pixels. */
    private static final java.util.Map<net.minecraft.item.Item, net.minecraft.item.ItemStack> STACKS = new java.util.HashMap<>();
    public static void item(DrawContext c, net.minecraft.item.Item item, float x, float y, float size) {
        net.minecraft.item.ItemStack st = STACKS.computeIfAbsent(item, net.minecraft.item.ItemStack::new);
        dev.orbit.client.compat.Compat.push(c);
        dev.orbit.client.compat.Compat.translate(c, x, y);
        dev.orbit.client.compat.Compat.scale(c, size / 16f);
        c.drawItem(st, 0, 0);
        dev.orbit.client.compat.Compat.pop(c);
    }

    public static void text(DrawContext c, String s, float x, float y, int color, boolean shadow) {
        color = f(color);
        if ((color >>> 24) < 8) return; // fully transparent text renders opaque in vanilla; skip instead
        c.drawText(font(), styled(s, 0, c), Math.round(x), Math.round(y), color, shadow);
    }

    public static void centered(DrawContext c, String s, float cx, float y, int color, boolean shadow) {
        text(c, s, cx - width(s) / 2f, y, color, shadow);
    }

    /** Draws text letter-by-letter with a moving rainbow when chroma is enabled. */
    public static void chromaText(DrawContext c, String s, float x, float y, int color, boolean chroma, boolean shadow) {
        if (!chroma) { text(c, s, x, y, color, shadow); return; }
        float cx = x;
        for (int i = 0; i < s.length(); i++) {
            String ch = String.valueOf(s.charAt(i));
            text(c, ch, cx, y, chroma((cx) / 160f, 1.2f), shadow);
            cx += width(ch);
        }
    }

    // ------------------------------------------------------------ animation
    /**
     * Rounds a scale so that (GUI scale x it) is a whole number: font glyphs then map onto whole
     * screen pixels instead of being resampled, which is what made scaled text look jagged.
     */
    public static float pixelScale(float k) {
        double gs = guiScale();
        return (float) (Math.max(1, Math.round(k * gs)) / gs);
    }
    /** Rounds a GUI coordinate to the nearest real screen pixel. */
    public static float snap(float v) {
        double gs = guiScale();
        return (float) (Math.round(v * gs) / gs);
    }
    public static float lerp(float a, float b, float t) { return a + (b - a) * Math.max(0, Math.min(1, t)); }
    public static float easeOut(float t) { t = Math.max(0, Math.min(1, t)); return 1 - (float) Math.pow(1 - t, 3); }
    /** Frame-rate independent approach of current toward target. */
    public static float approach(float current, float target, float speed, float dtSeconds) {
        return current + (target - current) * (1 - (float) Math.exp(-speed * dtSeconds));
    }
}
