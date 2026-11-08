package dev.orbit.client.gui;

import dev.orbit.client.waypoints.Waypoints;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.*;

import static dev.orbit.client.gui.Render2D.*;

/**
 * Waypoint markers on the HUD: a soft light beam, a glowing pin and a clean name pill that fade with
 * distance and grow a little when you look at them; off-screen waypoints become arrows on the screen edge.
 */
public final class WaypointRenderer {
    private WaypointRenderer() {}

    private static final Map<Waypoints.Waypoint, Float> FOCUS = new WeakHashMap<>();
    private static long lastFrame = System.nanoTime();

    public record Options(boolean distance, double maxDistance, boolean beam, boolean arrows, boolean coords) {}

    public static void render(DrawContext c, int sw, int sh, Options o) {
        var mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        String dim = Waypoints.dimension();

        // far ones first so near ones draw on top
        List<Waypoints.Waypoint> list = new ArrayList<>();
        for (var w : Waypoints.current()) if (w.visible && w.dimension.equals(dim)) list.add(w);
        var pp = mc.player.getEntityPos();
        list.sort(Comparator.comparingDouble(w -> -pp.squaredDistanceTo(w.x + 0.5, w.y, w.z + 0.5)));

        for (var w : list) {
            float[] p = WorldProjection.project(w.x + 0.5, w.y + 0.6, w.z + 0.5, sw, sh, o.arrows());
            if (p == null) continue;
            float dist = p[2];
            if (dist > o.maxDistance()) continue;
            boolean onScreen = p[3] > 0.05f && p[0] > 8 && p[0] < sw - 8 && p[1] > 8 && p[1] < sh - 8;
            // fade: in near the max distance, out when you're standing on it
            float a = Math.min(1f, (float) ((o.maxDistance() - dist) / Math.max(1, o.maxDistance() * 0.08)));
            if (dist < 6) a *= Math.max(0.25f, dist / 6f);
            if (a <= 0.02f) continue;
            if (!onScreen) { if (o.arrows()) edgeArrow(c, sw, sh, p, w, dist, a, o); continue; }

            // looking near it -> it grows and shows more
            float off = (float) Math.hypot(p[0] - sw / 2f, p[1] - sh / 2f);
            float focus = approach(FOCUS.getOrDefault(w, 0f), off < 40 ? 1 : 0, 10, dt);
            FOCUS.put(w, focus);

            if (o.beam()) beam(c, sw, sh, w, p, dist, a);

            float size = (float) Math.max(3.2, Math.min(6, 6.5 - dist / 120.0)) * (1 + 0.15f * focus);
            int col = w.color;
            // soft glow + pin
            disc(c, p[0], p[1], size * 2.4f, withAlpha(col, (int) (40 * a * (0.6f + 0.4f * focus))));
            disc(c, p[0], p[1], size + 1.4f, withAlpha(0x0B0818, (int) (200 * a)));
            disc(c, p[0], p[1], size, alpha(col, a));
            disc(c, p[0], p[1], size * 0.42f, withAlpha(0xFFFFFF, (int) (240 * a)));

            // name pill
            String name = w.name, sub = o.distance() ? formatDist(dist) : "";
            if (focus > 0.5f && o.coords()) sub = (sub.isEmpty() ? "" : sub + "  ·  ") + w.x + " " + w.y + " " + w.z;
            int nw = boldWidth(name), sw2 = sub.isEmpty() ? 0 : width(sub) + 6;
            float pw = nw + sw2 + 12, ph = 13, px = p[0] - pw / 2f, py = p[1] - size - ph - 4 - focus * 2;
            round(c, px, py, pw, ph, 6, withAlpha(0x0B0818, (int) ((150 + 60 * focus) * a)));
            round(c, px, py, 2.5f, ph, 1, alpha(col, a));
            bold(c, name, px + 6, py + 3, withAlpha(0xFFFFFF, (int) (255 * a)), false);
            if (!sub.isEmpty()) text(c, sub, px + 6 + nw + 6, py + 3, withAlpha(0xC9C2E6, (int) (230 * a)), false);
        }
    }

    /** a faint vertical light from the waypoint up into the sky */
    private static void beam(DrawContext c, int sw, int sh, Waypoints.Waypoint w, float[] base, float dist, float a) {
        float[] top = WorldProjection.project(w.x + 0.5, w.y + 0.6 + Math.min(120, 20 + dist * 0.6), w.z + 0.5, sw, sh, false);
        if (top == null) return;
        float bw = (float) Math.max(1, Math.min(5, 90 / Math.max(1, dist)));
        int y0 = Math.round(Math.min(top[1], base[1])), y1 = Math.round(Math.max(top[1], base[1]));
        int x0 = Math.round(base[0] - bw / 2), x1 = Math.max(x0 + 1, Math.round(base[0] + bw / 2));
        if (y1 - y0 < 2) return;
        c.fillGradient(x0, y0, x1, y1, withAlpha(w.color, 0), withAlpha(w.color, (int) (120 * a)));
        c.fillGradient(Math.round(base[0]) , y0, Math.round(base[0]) + 1, y1, withAlpha(0xFFFFFF, 0), withAlpha(0xFFFFFF, (int) (110 * a)));
    }

    /** off screen / behind you: a small pointer on the screen edge, pointing to it */
    private static void edgeArrow(DrawContext c, int sw, int sh, float[] p, Waypoints.Waypoint w, float dist, float a, Options o) {
        float cx = sw / 2f, cy = sh / 2f, dx = p[0] - cx, dy = p[1] - cy;
        if (Math.abs(dx) < 0.01f && Math.abs(dy) < 0.01f) dy = 1;
        float margin = 22, sx = (cx - margin) / Math.max(0.001f, Math.abs(dx)), sy = (cy - margin) / Math.max(0.001f, Math.abs(dy));
        float k = Math.min(sx, sy);
        float ex = cx + dx * k, ey = cy + dy * k;
        double ang = Math.atan2(dy, dx);
        float ca = (float) Math.cos(ang), sa = (float) Math.sin(ang);
        int col = w.color;
        disc(c, ex, ey, 9, withAlpha(0x0B0818, (int) (190 * a)));
        disc(c, ex, ey, 7, alpha(col, a * 0.9f));
        disc(c, ex, ey, 2.6f, withAlpha(0xFFFFFF, (int) (240 * a)));
        // pointer: three dots growing smaller toward the waypoint's direction
        for (int i = 1; i <= 3; i++) disc(c, ex + ca * (8 + i * 3.2f), ey + sa * (8 + i * 3.2f), 2.2f - i * 0.45f, alpha(col, a));
        String label = w.name + (o.distance() ? "  " + formatDist(dist) : "");
        int tw = width(label);
        float lx = Math.max(4, Math.min(sw - tw - 4, ex - tw / 2f - ca * (tw / 2f + 14))), ly = Math.max(4, Math.min(sh - 14, ey - 4 - sa * 16));
        round(c, lx - 4, ly - 2, tw + 8, 12, 6, withAlpha(0x0B0818, (int) (150 * a)));
        text(c, label, lx, ly, withAlpha(0xFFFFFF, (int) (230 * a)), false);
    }

    public static String formatDist(float d) { return d >= 1000 ? String.format(Locale.ROOT, "%.1fkm", d / 1000f) : Math.round(d) + "m"; }
}
