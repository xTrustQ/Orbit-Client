package dev.orbit.client.cosmetics;

import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * The big props of the cartoon death animations — a grand piano dropping from the sky, a giant bowling ball with
 * pins on a lane, and a flying saucer with a tractor beam. Drawn at the victim's feet (world space, y up, pixels),
 * turned so the camera sees them from the side. t = ticks since the death (with partial ticks).
 */
public final class DeathProps {
    private DeathProps() {}

    public static final int PIANO = 0, BOWLING = 1, UFO = 2;
    /** when the piano lands / the ball hits / the victim is pulled into the saucer (ticks) */
    public static final float PIANO_HIT = 9, BALL_HIT = 8, UFO_TAKE = 6, UFO_GONE = 18;

    public static void render(int kind, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState camera, double x, double y, double z,
                              int light, float t, float phi, float victimHeight, double sqDist) {
        var tex = CosmeticsFeature.white();
        if (tex == null) return;
        matrices.push();
        matrices.translate(x, y, z);
        float turn = -phi + (kind == PIANO ? 0.55f : 0);       // local -z looks at the camera, +x is the camera's left
        matrices.multiply(RotationAxis.POSITIVE_Y.rotation(turn));
        var lit = RenderLayers.entityTranslucent(tex);
        var glow = RenderLayers.entityTranslucentEmissive(tex);
        int bright = Math.max(light, 0x00A000A0);
        switch (kind) {
            case PIANO -> {
                if (t > 50) break;
                queue.submitCustom(matrices, lit, (e, vc) -> pianoScene(new CosmeticsFeature.Mesh(e, vc, bright), t));
            }
            case BOWLING -> {
                if (t > 50) break;
                queue.submitCustom(matrices, lit, (e, vc) -> bowlingScene(new CosmeticsFeature.Mesh(e, vc, bright), t));
                if (t >= BALL_HIT + 1 && t < 42) {
                    float pop = t < BALL_HIT + 3 ? (t - BALL_HIT - 1) * 0.15f : 0.3f;
                    queue.submitLabel(matrices, new Vec3d(0, victimHeight + 1.0 + pop, 0), 0, Text.literal("STRIKE!").formatted(Formatting.GOLD, Formatting.BOLD), true, 0xF000F0, sqDist, camera);
                }
            }
            case UFO -> {
                if (t > 38) break;
                float hover = (victimHeight * 16 + 34);
                queue.submitCustom(matrices, lit, (e, vc) -> saucer(new CosmeticsFeature.Mesh(e, vc, bright), t, hover));
                queue.submitCustom(matrices, glow, (e, vc) -> saucerGlow(new CosmeticsFeature.Mesh(e, vc, CosmeticsFeature.FULL_BRIGHT), t, hover));
            }
            default -> { }
        }
        matrices.pop();
    }

    private static float clamp(float v) { return Math.max(0, Math.min(1, v)); }
    private static int alpha(int argb, float a) { return ((int) (clamp(a) * (argb >>> 24)) << 24) | (argb & 0xFFFFFF); }

    /** a flat outline (x,z) pushed up from y0 to y1, with a top, a bottom and walls */
    private static void extrude(CosmeticsFeature.Mesh m, float[][] o, float y0, float y1, int top, int side, int bottom) {
        float cx = 0, cz = 0;
        for (float[] p : o) { cx += p[0]; cz += p[1]; }
        cx /= o.length; cz /= o.length;
        for (int i = 0; i < o.length; i++) {
            float[] a = o[i], b = o[(i + 1) % o.length];
            m.tri(new float[]{cx, y1, cz}, new float[]{a[0], y1, a[1]}, new float[]{b[0], y1, b[1]}, top);
            m.tri(new float[]{cx, y0, cz}, new float[]{b[0], y0, b[1]}, new float[]{a[0], y0, a[1]}, bottom);
            m.quadC(new float[]{a[0], y0, a[1]}, new float[]{b[0], y0, b[1]}, new float[]{b[0], y1, b[1]}, new float[]{a[0], y1, a[1]}, side, side, top, top);
        }
    }

    // ================================================================== Piano Drop
    private static final float[][] PIANO_OUTLINE = {{-15, -14}, {15, -14}, {15, -4}, {13.5f, 2}, {10, 7}, {6, 12}, {3, 17}, {1, 21}, {-3, 24.5f}, {-9, 25.5f}, {-13.5f, 23.5f}, {-15, 19}};
    private static final int CASE = 0xFF0D0D10, CASE_TOP = 0xFF2C2C34, GOLD = 0xFFD8B04A;

    private static void pianoScene(CosmeticsFeature.Mesh m, float t) {
        float y, tilt = 0, squash = 1;
        if (t < PIANO_HIT) { float k = t / PIANO_HIT; y = 150 * (1 - k * k); }
        else {
            float u = t - PIANO_HIT;
            y = u < 5 ? 5 * (float) Math.sin(u / 5 * Math.PI) * (float) Math.exp(-u * 0.2f) : 0;   // a little bounce
            tilt = u < 8 ? (float) Math.sin(u * 1.4f) * 0.06f * (1 - u / 8) : 0.035f;               // settles a bit crooked
            squash = u < 2 ? 1 - 0.14f * (float) Math.sin(u / 2 * Math.PI) : 1;
            if (t > 38) y -= (t - 38) * 2.6f;                                                         // sinks away at the end
        }
        // shadow growing on the ground while it falls
        if (t < PIANO_HIT + 1) {
            float k = clamp(t / PIANO_HIT), r = 10 + 14 * k;
            ellipse(m, 0, 0.25f, 4, r, r * 0.9f, alpha(0xC0000000, 0.25f + 0.6f * k));
        }
        boolean broken = t >= PIANO_HIT;
        float lid = broken ? 0.75f * clamp((t - PIANO_HIT) / 3) - 0.08f * (float) Math.max(0, Math.sin((t - PIANO_HIT) * 2)) * clamp(1 - (t - PIANO_HIT) / 10) : 0;
        // the whole piano in its own frame: falls, bounces, squashes, tilts
        float fy = y, ft = tilt, fs = squash;
        piano(m, fy, ft, fs, lid, broken, t);
        if (broken) debris(m, t - PIANO_HIT);
    }

    /** the grand piano with its legs on the ground at y = base */
    private static void piano(CosmeticsFeature.Mesh m, float base, float tilt, float squash, float lid, boolean broken, float t) {
        float caseLo = base + 9 * squash, caseHi = base + 17 * squash;
        // legs (the front right one breaks off on impact), gold casters
        float[][] legs = {{-12, -11}, {12, -11}, {-9, 21}};
        for (int i = 0; i < legs.length; i++) {
            if (broken && i == 1) continue;
            float lx = legs[i][0], lz = legs[i][1];
            float lean = i == 0 && broken ? tilt * 30 : 0;
            m.quadC(new float[]{lx - 1.6f, caseLo, lz - 1.6f}, new float[]{lx + 1.6f, caseLo, lz - 1.6f}, new float[]{lx + 1.0f + lean, base + 1, lz - 1.0f}, new float[]{lx - 1.0f + lean, base + 1, lz - 1.0f}, CASE, CASE, CASE_TOP, CASE_TOP);
            m.box(lx - 1.5f, base + 1, lz - 1.5f, lx + 1.5f, caseLo, lz + 1.5f, CASE);
            m.box(lx - 0.9f, base, lz - 0.9f, lx + 0.9f, base + 1.2f, lz + 0.9f, GOLD);
        }
        if (broken) m.box(10.5f, base - 0.5f, -12.5f, 13.5f, caseLo, -9.5f, CASE); // the broken leg stub (piano leans on it)
        // pedal lyre
        m.box(-2, base + 2, -12, 2, caseLo, -10.5f, CASE);
        for (int i = -1; i <= 1; i++) m.box(i * 1.3f - 0.4f, base + 2.2f, -13.6f, i * 1.3f + 0.4f, base + 2.6f, -11.8f, GOLD);
        // case (body) with a glossy rim
        extrude(m, PIANO_OUTLINE, caseLo, caseHi, CASE_TOP, CASE, CASE);
        for (int i = 0; i < PIANO_OUTLINE.length; i++) {
            float[] a = PIANO_OUTLINE[i], b = PIANO_OUTLINE[(i + 1) % PIANO_OUTLINE.length];
            m.line(new float[]{a[0], caseHi + 0.05f, a[1]}, new float[]{b[0], caseHi + 0.05f, b[1]}, 0.18f, 0xFF4E4E5C);
        }
        // keybed, keys, cheeks, fallboard with a gold name stripe
        m.box(-15.5f, caseLo + 2, -19.5f, 15.5f, caseLo + 3.6f, -14, CASE);
        m.box(-13.6f, caseLo + 3.6f, -19.4f, 13.6f, caseLo + 4.1f, -14.6f, 0xFFF4F1EA);
        for (int k = 0; k < 26; k++) m.box(-13.6f + k * 1.05f, caseLo + 3.62f, -19.45f, -13.55f + k * 1.05f, caseLo + 4.12f, -14.6f, 0xFFB8B4AC); // gaps between white keys
        int[] pattern = {1, 1, 0, 1, 1, 1, 0};
        for (int k = 0; k < 25; k++) if (pattern[k % 7] == 1) m.box(-13.0f + k * 1.05f, caseLo + 4.1f, -17.6f, -12.4f + k * 1.05f, caseLo + 4.8f, -14.6f, 0xFF141416);
        for (int s = -1; s <= 1; s += 2) m.box(s * 14.0f - 1, caseLo + 2, -19.5f, s * 14.0f + 1, caseLo + 5.6f, -14, CASE);
        m.box(-13.6f, caseLo + 4.1f, -14.7f, 13.6f, caseLo + 7.0f, -14, CASE);
        m.box(-3, caseLo + 6.2f, -14.75f, 3, caseLo + 6.5f, -14.7f, GOLD);
        // lid: closed while falling, flips open when it lands (hinged on the straight left side), held by a prop stick
        float ca = (float) Math.cos(lid), sa = (float) Math.sin(lid);
        float[][] lp = new float[PIANO_OUTLINE.length][];
        for (int i = 0; i < PIANO_OUTLINE.length; i++) {
            float dx = PIANO_OUTLINE[i][0] + 15;
            lp[i] = new float[]{-15 + dx * ca, caseHi + 0.3f + dx * sa, PIANO_OUTLINE[i][1]};
        }
        float[] c = {0, 0, 0};
        for (float[] p : lp) { c[0] += p[0]; c[1] += p[1]; c[2] += p[2]; }
        c[0] /= lp.length; c[1] /= lp.length; c[2] /= lp.length;
        for (int i = 0; i < lp.length; i++) m.tri(c, lp[i], lp[(i + 1) % lp.length], i % 2 == 0 ? CASE_TOP : 0xFF24242B);
        if (lid > 0.05f) {
            m.line(new float[]{10, caseHi, 2}, new float[]{-15 + 25 * ca, caseHi + 25 * sa, 2}, 0.35f, CASE);  // prop stick
            // inside: gold harp frame and strings, now visible
            extrude(m, new float[][]{{-14, -12}, {14, -12}, {13, -3}, {9, 6}, {3, 15}, {-2, 22}, {-12, 22}}, caseHi - 0.6f, caseHi - 0.2f, 0xFFC99A3A, 0xFFA97E2A, 0xFFA97E2A);
            for (int k = 0; k < 9; k++) m.line(new float[]{-12 + k * 2.8f, caseHi, -11}, new float[]{-12 + k * 1.2f, caseHi, 20 - k * 1.4f}, 0.08f, 0xFFE8E2D0);
        }
    }

    /** keys and the broken leg flying off after the crash */
    private static void debris(CosmeticsFeature.Mesh m, float u) {
        if (u > 30) return;
        for (int i = 0; i < 12; i++) {
            double a = i * 2.39996 + 0.4;
            float sp = 3.2f + (i % 4) * 0.9f, vy = 4.5f + (i % 3) * 1.6f;
            float px = (float) Math.cos(a) * sp * u, pz = -16 + (float) Math.sin(a) * sp * u * 0.6f;
            float py = Math.max(0.3f, 12 + vy * u - 0.9f * u * u);
            boolean black = i % 3 == 0;
            float spin = u * (2 + i % 3);
            float cs = (float) Math.cos(spin), sn = (float) Math.sin(spin), L = black ? 1.6f : 2.4f;
            m.quad(new float[]{px - cs * L, py - sn * L, pz - 0.5f}, new float[]{px + cs * L, py + sn * L, pz - 0.5f},
                    new float[]{px + cs * L, py + sn * L, pz + 0.5f}, new float[]{px - cs * L, py - sn * L, pz + 0.5f}, black ? 0xFF141416 : 0xFFF4F1EA);
        }
        // the broken leg cartwheels away
        float lx = 12 + 4.5f * u, ly = Math.max(1.5f, 10 + 6 * u - 1.1f * u * u), lz = -11 - 2.5f * u;
        float rot = u * 0.9f, cr = (float) Math.cos(rot), sr = (float) Math.sin(rot);
        m.quad(new float[]{lx - sr * 4, ly - cr * 4, lz - 1.4f}, new float[]{lx + sr * 4, ly + cr * 4, lz - 1.4f}, new float[]{lx + sr * 4, ly + cr * 4, lz + 1.4f}, new float[]{lx - sr * 4, ly - cr * 4, lz + 1.4f}, CASE);
        m.quad(new float[]{lx - 1.4f, ly - cr * 4, lz - sr * 4}, new float[]{lx + 1.4f, ly - cr * 4, lz - sr * 4}, new float[]{lx + 1.4f, ly + cr * 4, lz + sr * 4}, new float[]{lx - 1.4f, ly + cr * 4, lz + sr * 4}, CASE);
    }

    private static void ellipse(CosmeticsFeature.Mesh m, float cx, float y, float cz, float rx, float rz, int col) {
        int n = 24;
        for (int i = 0; i < n; i++) {
            double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n;
            m.tri(new float[]{cx, y, cz}, new float[]{cx + (float) Math.cos(a1) * rx, y, cz + (float) Math.sin(a1) * rz}, new float[]{cx + (float) Math.cos(a0) * rx, y, cz + (float) Math.sin(a0) * rz}, col);
        }
    }

    // ================================================================== Bowling Strike
    private static final float BALL_R = 9;
    /** ball centre along local x: comes in fast from the camera's left (+x), rolls straight through */
    public static float ballX(float t) { return 104 - 13 * t; }

    private static void bowlingScene(CosmeticsFeature.Mesh m, float t) {
        float show = clamp(t / 3) * clamp((48 - t) / 6);
        lane(m, show);
        float bx = ballX(t);
        if (bx > -170) ball(m, bx, BALL_R, 0, (104 - bx) / BALL_R);
        // four pins behind the victim, knocked flying as the ball arrives
        float[][] pins = {{-12, -6}, {-12, 6}, {-22, 0}, {-22, -11}};
        for (int i = 0; i < pins.length; i++) {
            float px = pins[i][0], pz = pins[i][1];
            float hitT = (104 - px + 4) / 13f;
            if (t < hitT) { pin(m, px, 0, pz, 0, 0); continue; }
            float u = t - hitT;
            float vx = -6.5f - i * 0.8f, vz = (pz == 0 ? (i % 2 == 0 ? 2.5f : -2.5f) : Math.signum(pz) * 3.2f), vy = 6 + i * 1.3f;
            float x = px + vx * u, z = pz + vz * u, y = Math.max(0, vy * u - 1.0f * u * u);
            float spin = u * (0.8f + i * 0.25f);
            pin(m, x, y, z, spin, y <= 0 ? 1.45f : 0);
        }
    }

    /** a short polished lane with arrows and gutters along the ball's path */
    private static void lane(CosmeticsFeature.Mesh m, float show) {
        if (show <= 0.01f) return;
        float w = 9 * show, y = 0.12f;
        int[] boards = {0xFFE2BC86, 0xFFD6AE76, 0xFFE8C792, 0xFFD9B27C};
        for (int i = 0; i < 6; i++) {
            float z0 = -w + i * (2 * w / 6), z1 = z0 + 2 * w / 6;
            m.quad(new float[]{-150, y, z0}, new float[]{-150, y, z1}, new float[]{120, y, z1}, new float[]{120, y, z0}, alpha(boards[i % 4], show));
        }
        for (int s = -1; s <= 1; s += 2) // gutters
            m.quad(new float[]{-150, y - 0.05f, s * w}, new float[]{-150, y - 0.05f, s * (w + 2.6f * show)}, new float[]{120, y - 0.05f, s * (w + 2.6f * show)}, new float[]{120, y - 0.05f, s * w}, alpha(0xFF3A3F48, show));
        for (int k = -1; k <= 1; k++) { // aiming arrows
            float ax = 46, az = k * 4 * show;
            m.tri(new float[]{ax - 2.4f, y + 0.03f, az}, new float[]{ax + 1.4f, y + 0.03f, az - 1.1f}, new float[]{ax + 1.4f, y + 0.03f, az + 1.1f}, alpha(0xFF6B3A1E, show));
        }
        for (int k = -2; k <= 2; k++) m.box(80 - 0.4f, y, k * 3.2f * show - 0.4f, 80 + 0.4f, y + 0.04f, k * 3.2f * show + 0.4f, alpha(0xFF6B3A1E, show)); // dots
    }

    /** a glossy marbled bowling ball with finger holes, rolled by `roll` radians */
    private static void ball(CosmeticsFeature.Mesh m, float cx, float cy, float cz, float roll) {
        int nu = 20, nv = 12;
        float cr = (float) Math.cos(roll), sr = (float) Math.sin(roll);
        for (int j = 0; j < nv; j++) for (int i = 0; i < nu; i++) {
            float[][] q = new float[4][];
            int col = 0;
            for (int k = 0; k < 4; k++) {
                int ii = i + (k == 1 || k == 2 ? 1 : 0), jj = j + (k >= 2 ? 1 : 0);
                double u = ii * Math.PI * 2 / nu, v = jj * Math.PI / nv;
                // point on the ball in its own (rolling) frame, then rolled around z
                float lx = (float) (Math.sin(v) * Math.cos(u)), ly = (float) Math.cos(v), lz = (float) (Math.sin(v) * Math.sin(u));
                float wx = lx * cr - ly * sr, wy = lx * sr + ly * cr;
                q[k] = new float[]{cx + wx * BALL_R, cy + wy * BALL_R, cz + lz * BALL_R};
                if (k == 0) { // marble swirl, picked in the ball's own frame so it rolls with it
                    double f = Math.sin(lx * 5 + Math.sin(ly * 4) * 2) * Math.cos(lz * 4 - lx * 2);
                    col = f > 0.45 ? 0xFF8A5CF0 : f > 0.1 ? 0xFF5530B8 : f > -0.4 ? 0xFF2E1A72 : 0xFF1B0F48;
                }
            }
            m.quad(q[0], q[1], q[2], q[3], col);
        }
        // three finger holes (in the ball's frame)
        float[][] holes = {{0.25f, 0.55f, -0.8f}, {-0.25f, 0.55f, -0.8f}, {0f, 0.1f, -0.99f}};
        for (float[] h : holes) {
            float l = (float) Math.sqrt(h[0] * h[0] + h[1] * h[1] + h[2] * h[2]);
            float lx = h[0] / l, ly = h[1] / l, lz = h[2] / l;
            float wx = lx * cr - ly * sr, wy = lx * sr + ly * cr;
            m.ball(cx + wx * BALL_R * 0.94f, cy + wy * BALL_R * 0.94f, cz + lz * BALL_R * 0.94f, 1.35f, 0xFF050308);
        }
        // shine
        m.ball(cx - 3.5f, cy + 5, cz - 6, 1.1f, 0x88FFFFFF);
    }

    /** a bowling pin (14.6 px tall) standing at x,y,z, tipped over by `spin` around z; `lie` lays it flat */
    private static void pin(CosmeticsFeature.Mesh m, float x, float y, float z, float spin, float lie) {
        float a = spin + lie;
        float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
        float[] ys = {0, 1.5f, 4, 6.5f, 8.6f, 9.4f, 10.4f, 12, 13.6f, 14.6f};
        float[] rs = {1.6f, 2.3f, 2.6f, 2.0f, 1.1f, 1.0f, 1.05f, 1.5f, 1.25f, 0.05f};
        for (int i = 0; i < ys.length - 1; i++) {
            float y0 = ys[i], y1 = ys[i + 1];
            int col = (y0 >= 8.6f && y1 <= 10.4f) ? 0xFFE0242F : 0xFFF6F4EE;
            List<float[]> path = List.of(new float[]{x - sa * y0, y + ca * y0 + (lie > 0 ? 2.3f : 0), z}, new float[]{x - sa * y1, y + ca * y1 + (lie > 0 ? 2.3f : 0), z});
            m.tube(path, new float[]{rs[i], rs[i + 1]}, 10, col, col);
        }
    }

    // ================================================================== UFO Abduction
    /** saucer height above the feet while hovering (px), y offset over time */
    public static float saucerY(float t, float hover) {
        if (t < UFO_TAKE) { float k = 1 - t / UFO_TAKE; return hover + 140 * k * k; }
        if (t < 24) return hover + (float) Math.sin(t * 0.5f) * 1.2f;
        float u = t - 24;
        return hover + u * u * 5;
    }
    private static float saucerX(float t) { return t < 24 ? 0 : -(t - 24) * (t - 24) * 7; }

    private static void saucer(CosmeticsFeature.Mesh m, float t, float hover) {
        float y = saucerY(t, hover), x = saucerX(t);
        int metal = 0xFFC2CAD4, dark = 0xFF6E7781;
        m.cylinder(x, 0, y, y + 4.2f, 23, 13, 36, metal, 0xFFE6EBF0, false);        // upper hull
        m.cylinder(x, 0, y, y - 3.6f, 23, 12, 36, metal, dark, false);              // underside
        m.cylinder(x, 0, y - 3.6f, y - 4.4f, 9, 7, 24, 0xFF4A5260, 0xFF3A404C, false); // beam emitter
        m.torus(x, y, 0, 23, 0.7f, 48, 6, 0, 0xFF8E98A6);                          // rim
        m.sphere(x, y + 4.2f, 0, 9, 8, 9, 20, 0x6090E0FF, Math.PI);               // glass dome (lower half hides in the hull)
        // the little pilot
        m.ball(x, y + 7.5f, 0, 2.6f, 0xFF7BE36A);
        m.box(x - 1.6f, y + 7.6f, -2.6f, x - 0.5f, y + 8.6f, -2.2f, 0xFF101010);
        m.box(x + 0.5f, y + 7.6f, -2.6f, x + 1.6f, y + 8.6f, -2.2f, 0xFF101010);
        m.line(new float[]{x - 1, y + 9.8f, 0}, new float[]{x - 2, y + 12, 0}, 0.2f, 0xFF7BE36A);
        m.line(new float[]{x + 1, y + 9.8f, 0}, new float[]{x + 2, y + 12, 0}, 0.2f, 0xFF7BE36A);
    }
    private static void saucerGlow(CosmeticsFeature.Mesh m, float t, float hover) {
        float y = saucerY(t, hover), x = saucerX(t);
        int[] cols = {0xFFFF4D6A, 0xFFFFD84D, 0xFF4DFF9A, 0xFF4DC8FF};
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI * 2 / 16 + t * 0.35;
            boolean on = ((int) (t * 1.5f) + i) % 3 != 0;
            m.octa(x + (float) Math.cos(a) * 21, y - 0.6f, (float) Math.sin(a) * 21, 0.9f, 0.6f, 0.9f, on ? cols[i % 4] : 0x50FFFFFF);
        }
        m.octa(x, y + 12.4f, 0, 0.5f, 0.5f, 0.5f, ((int) (t * 2)) % 2 == 0 ? 0xFFFF4D6A : 0x40FF4D6A);
        // tractor beam: a soft cone down to the ground with rings travelling up
        float beam = t < UFO_TAKE - 1 ? 0 : t < 21 ? clamp((t - UFO_TAKE + 1) / 2) : clamp((23 - t) / 2);
        if (beam > 0) {
            float top = y - 4.4f;
            m.cylinder(x, 0, 0.2f, top, 18, 8, 24, alpha(0x60A8F0FF, beam), alpha(0x30A8F0FF, beam), true);
            m.cylinder(x, 0, 0.2f, top, 11, 5, 20, alpha(0x40E8FFFF, beam), alpha(0x18E8FFFF, beam), true);
            for (int r = 0; r < 4; r++) {
                float k = ((t * 0.09f) + r / 4f) % 1f, ry = k * top, rr = 18 + (8 - 18) * k;
                m.torus(x, ry, 0, rr, 0.35f, 36, 4, 0, alpha(0xC0C8F8FF, beam * (float) Math.sin(k * Math.PI)));
            }
            ellipse(m, x, 0.3f, 0, 19, 19, alpha(0x70A8F0FF, beam));
        }
    }
}
