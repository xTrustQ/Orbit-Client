package dev.orbit.client.gui;

import dev.orbit.client.OrbitClient;
import net.minecraft.client.gui.DrawContext;

import java.util.Random;

import static dev.orbit.client.gui.Render2D.*;

/** Shared animated space backdrop for the Orbit title, worlds and servers screens. */
public final class SpaceBackground {
    /** Menu palettes: {top, bottom, haze1, haze2, haze3, planetLight, planetDark}. Picked with the "Sky:" button. */
    public static final String[] NAMES = {"Nebula", "Aurora", "Deep Space", "Sunset", "Mono", "Nova", "Ocean", "Emerald", "Crimson", "Midnight"};
    private static final int[][] PALETTES = {
            {0xFF04030B, 0xFF0B0718, 0xFF8B5CF6, 0xFFDB2777, 0xFF3B82F6, 0xFFF0ABFC, 0xFF3B0764},
            {0xFF02080A, 0xFF051512, 0xFF10B981, 0xFF22D3EE, 0xFF8B5CF6, 0xFFBBF7D0, 0xFF064E3B},
            {0xFF02040A, 0xFF060B1C, 0xFF2563EB, 0xFF0EA5E9, 0xFF6366F1, 0xFFBAE6FD, 0xFF0C1A40},
            {0xFF0A0408, 0xFF1A0A12, 0xFFF97316, 0xFFEC4899, 0xFFA855F7, 0xFFFDE68A, 0xFF7C2D12},
            {0xFF040404, 0xFF0B0B0B, 0xFF737373, 0xFF525252, 0xFFA3A3A3, 0xFFF5F5F5, 0xFF262626},
            {0xFF0B0510, 0xFF1C0A1E, 0xFFFFB347, 0xFFFF5E9C, 0xFFFFE27A, 0xFFFFF1C2, 0xFF8A2D00}, // Nova: a star going supernova
            {0xFF010812, 0xFF03192B, 0xFF0EA5E9, 0xFF14B8A6, 0xFF38BDF8, 0xFFCFFAFE, 0xFF083344}, // Ocean
            {0xFF020A05, 0xFF06180D, 0xFF22C55E, 0xFF84CC16, 0xFF10B981, 0xFFDCFCE7, 0xFF14532D}, // Emerald
            {0xFF0C0204, 0xFF1F050A, 0xFFEF4444, 0xFFF97316, 0xFFBE123C, 0xFFFECACA, 0xFF7F1D1D}, // Crimson
            {0xFF000000, 0xFF05060C, 0xFF1E3A8A, 0xFF312E81, 0xFF4338CA, 0xFFC7D2FE, 0xFF0B1033}, // Midnight
    };
    private static final float[][] STARS = new float[260][5];
    static {
        Random r = new Random(7);
        for (float[] s : STARS) { s[0] = r.nextFloat(); s[1] = r.nextFloat(); s[2] = r.nextFloat() < 0.65f ? 0 : r.nextFloat() < 0.75f ? 1 : 2; s[3] = r.nextFloat() * 6.28f; s[4] = 0.6f + r.nextFloat() * 1.8f; }
    }
    private static float shootX, shootY, shootLife;
    private static long last = System.nanoTime();

    private SpaceBackground() {}

    public static int index() { return Math.max(0, java.util.Arrays.asList(NAMES).indexOf(OrbitClient.modules().ui().menuSky.get())); }
    public static int[] palette() { return PALETTES[index()]; }
    public static int[] paletteOf(int i) { return PALETTES[Math.max(0, Math.min(PALETTES.length - 1, i))]; }

    public static void draw(DrawContext c, int width, int height, int mouseX, int mouseY, boolean planet) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - last) / 1e9f);
        last = now;
        float time = (System.currentTimeMillis() % 10_000_000L) / 1000f;
        int[] p = palette();

        c.fillGradient(0, 0, width, height, p[0], p[1]);
        for (int i = 0; i < 3; i++) { // soft drifting nebula bands
            float yy = height * (0.22f + i * 0.18f) + (float) Math.sin(time * 0.15f + i * 2) * 10;
            int col = p[2 + i], bandH = 60 + i * 18;
            c.fillGradient(0, Math.round(yy - bandH), width, Math.round(yy), withAlpha(col, 0), withAlpha(col, 22 - i * 4));
            c.fillGradient(0, Math.round(yy), width, Math.round(yy + bandH), withAlpha(col, 22 - i * 4), withAlpha(col, 0));
        }
        float mx = mouseX / (float) Math.max(1, width) - 0.5f, my = mouseY / (float) Math.max(1, height) - 0.5f;
        for (float[] s : STARS) { // round, twinkling, with mouse parallax
            int depth = (int) s[2];
            float drift = (time * (depth + 1) * 0.004f) % 1f;
            float x = ((s[0] - drift + 1f) % 1f) * width - mx * (depth + 1) * 6;
            float y = s[1] * height - my * (depth + 1) * 6;
            float tw = 0.55f + 0.45f * (float) Math.sin(time * s[4] + s[3]);
            int a = (int) (tw * new int[]{120, 190, 255}[depth]);
            float r = depth == 2 ? 0.95f : depth == 1 ? 0.65f : 0.45f;
            int col = depth == 2 && s[3] > 5 ? withAlpha(p[2], a) : withAlpha(0xFFFFFF, a);
            disc(c, x, y, r, col);
            if (depth == 2) disc(c, x, y, r * 2.8f, withAlpha(col, a / 7));
        }
        shootLife -= dt;
        if (shootLife <= 0 && Math.random() < dt * 0.3) { shootX = width * (0.4f + (float) Math.random() * 0.6f); shootY = height * (float) Math.random() * 0.35f; shootLife = 0.9f; }
        if (shootLife > 0) {
            shootX -= dt * 420; shootY += dt * 150;
            for (int k = 0; k < 30; k++) disc(c, shootX + k * 2.2f, shootY - k * 0.8f, 0.9f * (1 - k / 34f), withAlpha(0xFFFFFF, (int) (230 * shootLife * (1 - k / 30f))));
        }
        if (planet && OrbitClient.modules().ui().menuPlanet.on()) {
            float r = Math.min(width, height) * 0.13f;
            Art.planet(c, width * 0.9f, height * 0.7f, r, p);
        }
        if (OrbitClient.modules().ui().menuParticles.on()) { // glowing motes drifting up
            for (int i = 0; i < 28; i++) {
                float[] s = STARS[i * 9 % STARS.length];
                float speed = 0.015f + s[4] * 0.01f;
                float yy = 1.1f - ((time * speed + s[1]) % 1.2f);
                float xx = s[0] + (float) Math.sin(time * 0.4f + s[3]) * 0.02f;
                float a = Math.min(1, Math.min(yy * 3, (1.1f - yy) * 3));
                int col = i % 3 == 0 ? p[3] : p[2];
                disc(c, xx * width, yy * height, 1.6f + s[4] * 0.5f, withAlpha(col, (int) (40 * a)));
                disc(c, xx * width, yy * height, 0.8f, withAlpha(0xFFFFFF, (int) (170 * a)));
            }
        }
        if (OrbitClient.modules().ui().menuHills.on()) Art.hills(c, width, height, p);
    }

    /**
     * Smooth background art (planet, hills) painted once with Java2D — anti-aliased gradients at the
     * screen's real resolution — then drawn as textures. Re-painted when the size or sky changes.
     */
    private static final class Art {
        private static String planetKey = "", hillsKey = "";
        private static net.minecraft.util.Identifier planetTex, hillsTex;
        private static int planetW, planetH, hillsW, hillsH;

        private static double gs() { return Math.max(1, net.minecraft.client.MinecraftClient.getInstance().getWindow().getScaleFactor()); }
        private static java.awt.Color col(int argb, int alpha) { return new java.awt.Color(argb >> 16 & 255, argb >> 8 & 255, argb & 255, alpha); }

        static void planet(DrawContext c, float cx, float cy, float r, int[] p) {
            double gs = gs();
            int R = (int) Math.round(r * gs);
            String key = R + ":" + index();
            if (!key.equals(planetKey) || planetTex == null) {
                if (planetTex != null) Tex.forget("bg-planet:" + planetKey);
                planetKey = key;
                planetW = (int) (R * 4.4); planetH = (int) (R * 2.6);
                planetTex = paint("bg-planet:" + key, planetW, planetH, g -> paintPlanet(g, planetW / 2.0, planetH / 2.0, R, p));
            }
            if (planetTex == null) return;
            float w = (float) (planetW / gs), h = (float) (planetH / gs);
            dev.orbit.client.compat.Compat.push(c);
            dev.orbit.client.compat.Compat.translate(c, cx - w / 2, cy - h / 2);
            dev.orbit.client.compat.Compat.scale(c, (float) (1 / gs));
            dev.orbit.client.compat.Compat.blitTint(c, planetTex, 0, 0, planetW, planetH, 0, 0, planetW, planetH, planetW, planetH, 0xFFFFFFFF);
            dev.orbit.client.compat.Compat.pop(c);
        }

        private static void paintPlanet(java.awt.Graphics2D g, double cx, double cy, double R, int[] p) {
            var ringBack = new java.awt.geom.Ellipse2D.Double(cx - R * 1.95, cy - R * 0.42, R * 3.9, R * 0.84);
            var tilt = java.awt.geom.AffineTransform.getRotateInstance(-0.32, cx, cy);
            java.awt.Shape ring = tilt.createTransformedShape(ringBack);
            float ringW = (float) Math.max(1.5, R * 0.05);
            // back half of the ring (behind the planet)
            g.setClip(new java.awt.Rectangle(0, 0, (int) (cx * 2), (int) cy));
            g.setClip(tilt.createTransformedShape(new java.awt.geom.Rectangle2D.Double(0, 0, cx * 2, cy)));
            g.setStroke(new java.awt.BasicStroke(ringW)); g.setColor(col(p[2], 110)); g.draw(ring);
            g.setStroke(new java.awt.BasicStroke(ringW * 3)); g.setColor(col(p[5], 26)); g.draw(ring);
            g.setClip(null);
            // atmosphere glow
            for (int i = 6; i >= 1; i--) {
                double rr = R * (1 + i * 0.045);
                g.setColor(col(p[2], 10));
                g.fill(new java.awt.geom.Ellipse2D.Double(cx - rr, cy - rr, rr * 2, rr * 2));
            }
            var body = new java.awt.geom.Ellipse2D.Double(cx - R, cy - R, R * 2, R * 2);
            // sphere: light from the top-left
            g.setPaint(new java.awt.RadialGradientPaint(new java.awt.geom.Point2D.Double(cx - R * 0.45, cy - R * 0.5), (float) (R * 1.7),
                    new float[]{0f, 0.45f, 1f}, new java.awt.Color[]{col(p[5], 255), col(lerpColor(p[5], p[2], 0.6f), 255), col(p[6], 255)}));
            g.fill(body);
            // soft cloud bands
            g.setClip(body);
            for (int i = 0; i < 7; i++) {
                double y = cy - R + R * 2 * (i + 0.5) / 7;
                double h = R * (0.08 + 0.05 * Math.sin(i * 1.7));
                g.setColor(col(i % 2 == 0 ? p[6] : p[5], 34));
                g.fill(new java.awt.geom.Ellipse2D.Double(cx - R * 1.3, y - h / 2, R * 2.6, h));
            }
            // night side shadow
            g.setPaint(new java.awt.RadialGradientPaint(new java.awt.geom.Point2D.Double(cx + R * 0.95, cy + R * 0.7), (float) (R * 1.6),
                    new float[]{0f, 0.55f, 1f}, new java.awt.Color[]{col(p[0], 235), col(p[0], 120), col(p[0], 0)}));
            g.fill(body);
            // rim light
            g.setStroke(new java.awt.BasicStroke((float) Math.max(1, R * 0.03)));
            g.setColor(col(p[5], 70));
            g.draw(new java.awt.geom.Arc2D.Double(cx - R, cy - R, R * 2, R * 2, 95, 120, java.awt.geom.Arc2D.OPEN));
            g.setClip(null);
            // front half of the ring
            g.setClip(tilt.createTransformedShape(new java.awt.geom.Rectangle2D.Double(0, cy, cx * 2, cy)));
            g.setStroke(new java.awt.BasicStroke(ringW * 3)); g.setColor(col(p[5], 30)); g.draw(ring);
            g.setStroke(new java.awt.BasicStroke(ringW)); g.setColor(col(lerpColor(p[2], p[5], 0.4f), 200)); g.draw(ring);
            g.setClip(null);
        }

        static void hills(DrawContext c, int width, int height, int[] p) {
            double gs = gs();
            int W = (int) Math.round(width * gs), H = (int) Math.round(height * 0.22 * gs);
            String key = W + "x" + H + ":" + index();
            if (!key.equals(hillsKey) || hillsTex == null) {
                if (hillsTex != null) Tex.forget("bg-hills:" + hillsKey);
                hillsKey = key; hillsW = W; hillsH = H;
                hillsTex = paint("bg-hills:" + key, W, H, g -> {
                    for (int layer = 0; layer < 2; layer++) {
                        var path = new java.awt.geom.Path2D.Double();
                        double base = H * (layer == 0 ? 0.38 : 0.66);
                        path.moveTo(0, H);
                        for (int x = 0; x <= W; x += 4) {
                            double xs = x / gs;
                            double n = Math.sin(xs * 0.011 + layer * 3) * 12 + Math.sin(xs * 0.027 + layer) * 6 + Math.sin(xs * 0.063 + layer * 5) * 3;
                            path.lineTo(x, base - n * gs);
                        }
                        path.lineTo(W, H); path.closePath();
                        int top = layer == 0 ? lerpColor(p[1], p[2], 0.10f) : p[0];
                        int bottom = layer == 0 ? lerpColor(p[1], p[0], 0.4f) : p[0];
                        g.setPaint(new java.awt.GradientPaint(0, (float) (base - 20 * gs), col(top, 255), 0, H, col(bottom, 255)));
                        g.fill(path);
                        if (layer == 0) { g.setStroke(new java.awt.BasicStroke((float) Math.max(1, gs * 0.6))); g.setColor(col(p[2], 40)); g.draw(path); }
                    }
                });
            }
            if (hillsTex == null) return;
            dev.orbit.client.compat.Compat.push(c);
            dev.orbit.client.compat.Compat.translate(c, 0, height - (float) (hillsH / gs));
            dev.orbit.client.compat.Compat.scale(c, (float) (1 / gs));
            dev.orbit.client.compat.Compat.blitTint(c, hillsTex, 0, 0, hillsW, hillsH, 0, 0, hillsW, hillsH, hillsW, hillsH, 0xFFFFFFFF);
            dev.orbit.client.compat.Compat.pop(c);
        }

        private static net.minecraft.util.Identifier paint(String key, int w, int h, java.util.function.Consumer<java.awt.Graphics2D> painter) {
            try {
                var img = new java.awt.image.BufferedImage(Math.max(1, w), Math.max(1, h), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                var g = img.createGraphics();
                g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);
                g.setRenderingHint(java.awt.RenderingHints.KEY_STROKE_CONTROL, java.awt.RenderingHints.VALUE_STROKE_PURE);
                painter.accept(g);
                g.dispose();
                var out = new java.io.ByteArrayOutputStream();
                javax.imageio.ImageIO.write(img, "png", out);
                return Tex.fromPng(key, out.toByteArray(), true);
            } catch (Exception e) {
                OrbitClient.LOG.warn("Could not paint menu background", e);
                return null;
            }
        }
    }

    // ------------------------------------------------------------------ shared widgets for the menu screens
    /** A clean, see-through rounded button. Returns true when hovered. */
    public static boolean button(DrawContext c, int x, int y, int w, int h, String label, int mx, int my, float hover, boolean primary) {
        boolean hov = mx >= x && my >= y && mx < x + w && my < y + h;
        int acc = accent();
        if (primary) round(c, x, y, w, h, h / 2, lerpColor(0xFFF4F1FF, 0xFFFFFFFF, hover));
        else {
            round(c, x, y, w, h, h / 2, lerpColor(0x38FFFFFF, 0x99FFFFFF, hover));
            round(c, x + 1, y + 1, w - 2, h - 2, h / 2 - 1, lerpColor(0x8C0A0818, withAlpha(lerpColor(0x1B1636, acc, 0.25f), 220), hover));
        }
        int tc = primary ? 0xFF140A28 : lerpColor(0xFFE6E0FF, 0xFFFFFFFF, hover);
        centered(c, label, x + w / 2f + hover * 2, y + (h - 8) / 2f, tc, !primary);
        if (hover > 0.05f && !primary) text(c, "›", x + w - 12 + hover * 2, y + (h - 8) / 2f, alpha(0xFFFFFFFF, hover), false);
        return hov;
    }
}
