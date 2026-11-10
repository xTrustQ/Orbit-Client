package dev.orbit.client.cosmetics;

import dev.orbit.client.emote.Emote;
import dev.orbit.client.emote.Emotes;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;

import java.util.List;

/**
 * Props for emotes (a sports car, a sun lounger, a campfire, a DJ booth, a hoverboard) and the bat-swinging
 * log buddy of the "Tung Tung Bonk" death animation. All original low-poly designs drawn with Mesh.
 * Emote props are drawn in player model space: pixels, y down, feet at y = 24, front = -z.
 */
public final class Props extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {
    public Props(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> ctx) { super(ctx); }

    // ------------------------------------------------------------------ which emote / how long
    /** {emote ordinal, seconds} for this rendered player, or null */
    private static float[] emoteOf(PlayerEntityRenderState s) {
        if (s.id <= CosmeticsFeature.PREVIEW_ID) {
            Emote e = Emotes.PREVIEW.get(s.id);
            return e == null || !e.hasProp() ? null : new float[]{e.ordinal(), Emotes.previewSeconds() % Math.max(0.5f, e.seconds)};
        }
        var w = MinecraftClient.getInstance().world;
        if (w == null || !(w.getEntityById(s.id) instanceof PlayerEntity p)) return null;
        float[] i = Emotes.info(p.getUuid());
        return i == null || !Emote.values()[(int) i[0]].hasProp() ? null : i;
    }

    @Override
    public void render(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, PlayerEntityRenderState state, float limbAngle, float yaw) {
        float[] info = emoteOf(state);
        if (info == null || state.invisible) return;
        net.minecraft.util.Identifier tex = CosmeticsFeature.white();
        if (tex == null) return;
        Emote e = Emote.values()[(int) info[0]];
        float t = info[1];
        queue.submitCustom(matrices, RenderLayers.entityTranslucent(tex), (en, vc) -> lit(new CosmeticsFeature.Mesh(en, vc, light), e, t));
        queue.submitCustom(matrices, RenderLayers.entityTranslucentEmissive(tex), (en, vc) -> glow(new CosmeticsFeature.Mesh(en, vc, CosmeticsFeature.FULL_BRIGHT), e, t));
    }

    private static void lit(CosmeticsFeature.Mesh m, Emote e, float t) {
        switch (e) {
            case JOYRIDE -> carLit(m, t);
            case SUNBATHE -> loungerLit(m, t);
            case CAMPFIRE -> campLit(m, t);
            case DJ -> djLit(m, t);
            case HOVERBOARD -> boardLit(m, t);
            case MONEY -> moneyLit(m, t);
            default -> { }
        }
    }
    private static void glow(CosmeticsFeature.Mesh m, Emote e, float t) {
        switch (e) {
            case JOYRIDE -> carGlow(m, t);
            case SUNBATHE -> loungerGlow(m, t);
            case CAMPFIRE -> campGlow(m, t);
            case DJ -> djGlow(m, t);
            case HOVERBOARD -> boardGlow(m, t);
            case MONEY -> moneyGlow(m, t);
            default -> { }
        }
    }

    // ------------------------------------------------------------------ shared shapes
    /** a wheel / disc standing upright, axis along X */
    private static void wheel(CosmeticsFeature.Mesh m, float cx, float cy, float cz, float r, float w, float spin, int tire, int rim) {
        int n = 14;
        for (int i = 0; i < n; i++) {
            double a0 = spin + i * Math.PI * 2 / n, a1 = spin + (i + 1) * Math.PI * 2 / n;
            float y0 = cy + (float) Math.sin(a0) * r, z0 = cz + (float) Math.cos(a0) * r, y1 = cy + (float) Math.sin(a1) * r, z1 = cz + (float) Math.cos(a1) * r;
            m.quad(new float[]{cx - w / 2, y0, z0}, new float[]{cx + w / 2, y0, z0}, new float[]{cx + w / 2, y1, z1}, new float[]{cx - w / 2, y1, z1}, tire);
            for (float side : new float[]{-w / 2 - 0.01f, w / 2 + 0.01f}) {
                float ri = r * 0.62f;
                m.quad(new float[]{cx + side, cy, cz}, new float[]{cx + side, cy, cz},
                        new float[]{cx + side, cy + (float) Math.sin(a1) * ri, cz + (float) Math.cos(a1) * ri}, new float[]{cx + side, cy + (float) Math.sin(a0) * ri, cz + (float) Math.cos(a0) * ri}, i % 2 == 0 ? rim : 0xFF2A2A2E);
                m.quad(new float[]{cx + side, cy + (float) Math.sin(a0) * ri, cz + (float) Math.cos(a0) * ri}, new float[]{cx + side, cy + (float) Math.sin(a1) * ri, cz + (float) Math.cos(a1) * ri},
                        new float[]{cx + side, y1, z1}, new float[]{cx + side, y0, z0}, 0xFF18181B);
            }
        }
    }
    /** a ring of quads in the plane spanned by (ux,uy,uz) and (vx,vy,vz) */
    private static void ringIn(CosmeticsFeature.Mesh m, float cx, float cy, float cz, float[] u, float[] v, float r, float thick, int n, int col) {
        for (int i = 0; i < n; i++) {
            double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n;
            float[] p0 = pt(cx, cy, cz, u, v, (float) Math.cos(a0) * r, (float) Math.sin(a0) * r), p1 = pt(cx, cy, cz, u, v, (float) Math.cos(a1) * r, (float) Math.sin(a1) * r);
            m.line(p0, p1, thick, col);
        }
    }
    private static float[] pt(float cx, float cy, float cz, float[] u, float[] v, float a, float b) { return new float[]{cx + u[0] * a + v[0] * b, cy + u[1] * a + v[1] * b, cz + u[2] * a + v[2] * b}; }

    // ================================================================== Joyride: a black BMW-style M convertible, parked
    private static final int PAINT_TOP = 0xFF30333A, PAINT = 0xFF121316, PAINT_LOW = 0xFF070708, TRIM = 0xFF101013, CHROME = 0xFFDCE0E8, GLASS = 0x8CA8D8FF;
    private static final int CALIPER = 0xFF1E5BD8; // M-style blue brakes
    private static float carSpin(float t) { return 0.4f; } // parked: the wheels don't turn

    /**
     * A smooth body made of cross-sections {z, centreX, halfWidth, topY, bottomY} joined by quads, shaded
     * lighter on top and darker low down. The roof between openZ0..openZ1 stays open (the cockpit).
     */
    private static void hull(CosmeticsFeature.Mesh m, float[][] secs, float openZ0, float openZ1) {
        int n = 8;
        float[][][] P = new float[secs.length][][];
        for (int i = 0; i < secs.length; i++) {
            float z = secs[i][0], cx = secs[i][1], h = secs[i][2], t = secs[i][3], b = secs[i][4];
            P[i] = new float[][]{{cx - h + 1.4f, b, z}, {cx - h, b - 2.4f, z}, {cx - h + 0.3f, t + 2.2f, z}, {cx - h + 2f, t, z},
                    {cx + h - 2f, t, z}, {cx + h - 0.3f, t + 2.2f, z}, {cx + h, b - 2.4f, z}, {cx + h - 1.4f, b, z}};
        }
        int[] col = {PAINT_LOW, PAINT_LOW, PAINT, PAINT_TOP, PAINT_TOP, PAINT, PAINT_LOW, PAINT_LOW};
        for (int i = 0; i < secs.length - 1; i++) {
            boolean open = secs[i][0] >= openZ0 && secs[i + 1][0] <= openZ1;
            for (int k = 0; k < n; k++) {
                int kk = (k + 1) % n;
                if (open && k == 3) continue;
                m.quadC(P[i][k], P[i][kk], P[i + 1][kk], P[i + 1][k], col[k], col[kk], col[kk], col[k]);
            }
        }
        for (int end : new int[]{0, secs.length - 1}) { // nose and tail caps
            float[] c = {secs[end][1], (secs[end][3] + secs[end][4]) / 2, secs[end][0]};
            for (int k = 0; k < n; k++) m.tri(c, P[end][k], P[end][(k + 1) % n], end == 0 ? PAINT : PAINT_LOW);
        }
    }

    /** sport wheel: tyre, 5-spoke chrome rim, red brake caliper (axis X) */
    private static void sportWheel(CosmeticsFeature.Mesh m, float cx, float cy, float cz, float r, float w, float spin, boolean left, int rim, int spokes) {
        int n = 16;
        for (int i = 0; i < n; i++) { // tyre tread
            double a0 = spin + i * Math.PI * 2 / n, a1 = spin + (i + 1) * Math.PI * 2 / n;
            float y0 = cy + (float) Math.sin(a0) * r, z0 = cz + (float) Math.cos(a0) * r, y1 = cy + (float) Math.sin(a1) * r, z1 = cz + (float) Math.cos(a1) * r;
            m.quad(new float[]{cx - w / 2, y0, z0}, new float[]{cx + w / 2, y0, z0}, new float[]{cx + w / 2, y1, z1}, new float[]{cx - w / 2, y1, z1}, i % 2 == 0 ? 0xFF141417 : 0xFF1C1C20);
        }
        float face = left ? cx - w / 2 - 0.02f : cx + w / 2 + 0.02f, inner = left ? cx + w / 2 + 0.02f : cx - w / 2 - 0.02f;
        for (float fx : new float[]{face, inner}) { // sidewalls
            float rr = r * 0.7f;
            for (int i = 0; i < n; i++) {
                double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n;
                m.quad(new float[]{fx, cy + (float) Math.sin(a0) * rr, cz + (float) Math.cos(a0) * rr}, new float[]{fx, cy + (float) Math.sin(a1) * rr, cz + (float) Math.cos(a1) * rr},
                        new float[]{fx, cy + (float) Math.sin(a1) * r, cz + (float) Math.cos(a1) * r}, new float[]{fx, cy + (float) Math.sin(a0) * r, cz + (float) Math.cos(a0) * r}, 0xFF0F0F12);
            }
        }
        float rimX = face + (left ? -0.05f : 0.05f), rr = r * 0.7f;
        // dark wheel well behind the spokes, brake disc + caliper
        for (int i = 0; i < n; i++) {
            double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n;
            float[] c = {face + (left ? 0.6f : -0.6f), cy, cz};
            m.tri(c, new float[]{c[0], cy + (float) Math.sin(a0) * rr, cz + (float) Math.cos(a0) * rr}, new float[]{c[0], cy + (float) Math.sin(a1) * rr, cz + (float) Math.cos(a1) * rr}, 0xFF2A2C33);
        }
        float bx = face + (left ? 0.4f : -0.4f);
        m.box(bx - 0.25f, cy - r * 0.55f, cz + r * 0.1f, bx + 0.25f, cy - r * 0.15f, cz + r * 0.5f, CALIPER);
        for (int k = 0; k < spokes; k++) { // spokes
            double a = spin + k * Math.PI * 2 / spokes;
            m.line(new float[]{rimX, cy, cz}, new float[]{rimX, cy + (float) Math.sin(a) * rr, cz + (float) Math.cos(a) * rr}, spokes > 6 ? 0.38f : 0.55f, rim);
        }
        for (int i = 0; i < n; i++) { // rim lip
            double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n;
            m.line(new float[]{rimX, cy + (float) Math.sin(a0) * rr, cz + (float) Math.cos(a0) * rr}, new float[]{rimX, cy + (float) Math.sin(a1) * rr, cz + (float) Math.cos(a1) * rr}, 0.3f, rim);
        }
        m.box(rimX - 0.2f, cy - 0.7f, cz - 0.7f, rimX + 0.2f, cy + 0.7f, cz + 0.7f, 0xFF9AA0AC); // hub
    }

    // Joyride car: a big black luxury saloon (two-tone: black on top, grey lower sides), glass cabin over the driver.
    // Driver at x = 0, body centre x = 6, 33 px wide, 80 px long (nose z = -47, tail z = 33), belt line y ~ 9.8.
    private static final int GREY = 0xFF7E828A, GREY_DARK = 0xFF5E6168, TINT = 0x5E3C4E5E, PILLAR = 0xFF0C0C0F;
    private static final float WHEEL_R = 6.3f, WHEEL_Y = 17.7f;

    private static void carLit(CosmeticsFeature.Mesh m, float t) {
        float[][] body = {
                {-47, 6, 13.2f, 11.8f, 21.0f},
                {-45, 6, 15.6f, 11.2f, 21.6f},
                {-38, 6, 16.3f, 10.7f, 21.8f},
                {-26, 6, 16.5f, 10.2f, 21.8f},
                {-15, 6, 16.6f, 9.8f, 21.8f},
                {0, 6, 16.7f, 9.6f, 21.8f},
                {14, 6, 16.7f, 9.6f, 21.8f},
                {24, 6, 16.5f, 9.8f, 21.6f},
                {30, 6, 15.8f, 10.2f, 21.2f},
                {33, 6, 14.0f, 11.2f, 20.6f}};
        hull(m, body, 99, 99); // closed body
        // two-tone: grey lower sides with a thin chrome line on top
        for (int i = 0; i < body.length - 1; i++) {
            float[] a = body[i], b = body[i + 1];
            for (int s = -1; s <= 1; s += 2) {
                float xa = a[1] + s * (a[2] + 0.07f), xb = b[1] + s * (b[2] + 0.07f);
                float ya = a[4] - 2.4f, yb = b[4] - 2.4f;
                m.quad(new float[]{xa, 15.0f, a[0]}, new float[]{xb, 15.0f, b[0]}, new float[]{xb, yb, b[0]}, new float[]{xa, ya, a[0]}, i % 2 == 0 ? GREY : 0xFF80848C);
                m.line(new float[]{xa + s * 0.02f, 15.0f, a[0]}, new float[]{xb + s * 0.02f, 15.0f, b[0]}, 0.12f, CHROME);
            }
        }
        // wheels: big, dark multi-spoke rims
        for (float wz : new float[]{-32, 19}) {
            sportWheel(m, -11.2f, WHEEL_Y, wz, WHEEL_R, 3.9f, carSpin(t), true, 0xFF4A4D55, 10);
            sportWheel(m, 23.2f, WHEEL_Y, wz, WHEEL_R, 3.9f, carSpin(t), false, 0xFF4A4D55, 10);
            for (float ax : new float[]{-10.75f, 22.75f}) for (int i = 0; i <= 8; i++) { // dark arch lips
                double a0 = Math.PI + i * Math.PI / 9, a1 = Math.PI + (i + 1) * Math.PI / 9;
                m.line(new float[]{ax, WHEEL_Y + (float) Math.sin(a0) * 7.2f, wz + (float) Math.cos(a0) * 7.2f}, new float[]{ax, WHEEL_Y + (float) Math.sin(a1) * 7.2f, wz + (float) Math.cos(a1) * 7.2f}, 0.4f, TRIM);
            }
        }
        // interior seen through the glass: floor, seats, dashboard, wheel
        m.box(-8, 19.6f, -14, 20, 20.1f, 22, 0xFF1D1D23);
        for (float sx : new float[]{0, 12}) {
            m.box(sx - 4.4f, 17.6f, -5, sx + 4.4f, 19.6f, 4, 0xFF4A2A20);
            m.box(sx - 4.4f, 6.4f, 3.4f, sx + 4.4f, 19.6f, 6.2f, 0xFF4A2A20);
            m.box(sx - 2.5f, 3.4f, 4.0f, sx + 2.5f, 6.4f, 6.0f, 0xFF4A2A20);
        }
        m.box(-7.6f, 8.0f, 12, 19.6f, 19.6f, 16, 0xFF4A2A20);                    // rear bench
        m.box(-8, 9.6f, -15, 20, 12.5f, -10.2f, 0xFF202027);                     // dashboard
        m.box(3.2f, 9.4f, -11.4f, 4.4f, 9.55f, -10.4f, 0xFF2A2A30);              // start button
        float[] u = {1, 0, 0}, v = {0, 0.75f, 0.66f};
        ringIn(m, 0, 8.4f, -9.4f, u, v, 3.1f, 0.5f, 14, 0xFF141417);             // steering wheel
        m.line(new float[]{0, 8.4f, -9.4f}, new float[]{0, 10.6f, -12}, 0.5f, 0xFF141417);
        // the glass cabin: windscreen, roof, rear window, side windows with pillars
        float bz0 = -15.5f, rz0 = -5.5f, rz1 = 8.5f, bz1 = 21f, belt = 9.7f, roof = -1.6f;
        float xbL = -8.6f, xbR = 20.6f, xrL = -7.0f, xrR = 19.0f;               // tumblehome: narrower at the roof
        m.quad(new float[]{xbL, belt, bz0}, new float[]{xbR, belt, bz0}, new float[]{xrR, roof, rz0}, new float[]{xrL, roof, rz0}, TINT);   // windscreen
        m.quad(new float[]{xrL, roof, rz1}, new float[]{xrR, roof, rz1}, new float[]{xbR, belt, bz1}, new float[]{xbL, belt, bz1}, TINT);   // rear window
        m.quad(new float[]{xrL, roof - 0.35f, rz0 - 0.3f}, new float[]{xrR, roof - 0.35f, rz0 - 0.3f}, new float[]{xrR, roof - 0.35f, rz1 + 0.3f}, new float[]{xrL, roof - 0.35f, rz1 + 0.3f}, PAINT); // roof
        m.quad(new float[]{xrL, roof - 0.34f, rz0 - 0.3f}, new float[]{xrR, roof - 0.34f, rz0 - 0.3f}, new float[]{xrR, roof - 0.34f, rz1 + 0.3f}, new float[]{xrL, roof - 0.34f, rz1 + 0.3f}, PAINT_TOP);
        for (int s = -1; s <= 1; s += 2) {
            float xb = s < 0 ? xbL : xbR, xr = s < 0 ? xrL : xrR;
            float[] A = {xb, belt, bz0}, B = {xr, roof, rz0}, C = {xr, roof, rz1}, D = {xb, belt, bz1};
            m.quad(A, B, C, D, TINT);                                             // side glass
            m.line(A, B, 0.55f, PILLAR); m.line(C, D, 1.1f, PILLAR);              // A and C pillars
            m.line(new float[]{xb + (xr - xb) * 0.5f, belt, 3.2f}, new float[]{xr, roof, 3.2f}, 0.6f, PILLAR);   // B pillar
            m.line(B, C, 0.25f, CHROME); m.line(A, D, 0.2f, CHROME);              // chrome window line
            m.line(new float[]{xb, belt, bz1 - 3}, new float[]{xr + (xb - xr) * 0.35f, belt - 3.8f, bz1 - 4.2f}, 0.2f, CHROME); // kink at the back
            // mirrors, door lines, handles
            m.box(s < 0 ? -12.6f : 22.4f, 8.2f, -14.4f, s < 0 ? -10.4f : 24.6f, 9.9f, -12.2f, PAINT);
            float sx = s < 0 ? -10.7f : 22.7f;
            for (float dz : new float[]{-15.5f, 3.2f, 20.5f}) m.line(new float[]{sx, 10.4f, dz}, new float[]{sx, 19.2f, dz}, 0.1f, PILLAR);
            for (float hz : new float[]{-3.5f, 14f}) m.box(sx - 0.15f, 11.6f, hz, sx + 0.15f, 12.1f, hz + 2.2f, CHROME);
        }
        // nose: the big kidney grille (chrome frame, black vertical slats), split headlight housings, lower intakes
        for (float kx : new float[]{3.0f, 9.0f}) {
            m.box(kx - 2.75f, 12.2f, -47.55f, kx + 2.75f, 20.4f, -46.7f, CHROME);
            m.box(kx - 2.35f, 12.6f, -47.65f, kx + 2.35f, 20.0f, -47.5f, 0xFF060607);
            for (int i = 0; i < 7; i++) m.box(kx - 2.2f + i * 0.72f, 12.7f, -47.8f, kx - 1.95f + i * 0.72f, 19.9f, -47.6f, 0xFF35363D);
        }
        for (int s = -1; s <= 1; s += 2) {
            float x0 = s < 0 ? -6.4f : 12.8f, x1 = s < 0 ? -0.6f : 18.6f;
            m.box(x0, 12.3f, -47.35f, x1, 13.2f, -46.9f, 0xFF15161A);              // slim upper light strip housing
            m.box(x0 + 0.6f, 15.3f, -47.35f, x1 - 0.6f, 17.6f, -46.9f, 0xFF101114); // main lamp housing below
        }
        m.box(-5.5f, 20.5f, -47.3f, 17.5f, 21.3f, -46.8f, TRIM);                  // lower intake
        m.box(-7.5f, 21.3f, -48.0f, 19.5f, 21.7f, -44.0f, GREY_DARK);             // grey splitter
        m.line(new float[]{2, 11.7f, -44}, new float[]{2, 10.3f, -18}, 0.15f, PAINT_TOP);   // bonnet creases
        m.line(new float[]{10, 11.7f, -44}, new float[]{10, 10.3f, -18}, 0.15f, PAINT_TOP);
        // tail: dark light-bar housing across the boot, chrome strip, grey diffuser
        m.box(-6.5f, 12.4f, 33.0f, 18.5f, 13.6f, 33.35f, 0xFF2A0A0E);
        m.box(-4f, 17.6f, 33.2f, 16f, 17.9f, 33.4f, CHROME);
        m.box(-6f, 19.6f, 32.4f, 18f, 20.8f, 33.4f, GREY_DARK);
    }
    private static void carGlow(CosmeticsFeature.Mesh m, float t) {
        boolean pressed = t >= START_PRESS;
        m.box(3.35f, 9.35f, -11.25f, 4.25f, 9.4f, -10.55f, pressed ? 0xFFFF3B3B : 0x60FF3B3B);
        if (!pressed) return;
        float wake = Math.min(1, (t - START_PRESS) / 0.5f);
        int dashA = (int) (255 * wake) << 24;
        m.box(-3, 9.5f, -12.4f, 3, 9.6f, -10.3f, dashA | 0x59D2FF);              // driver display
        m.box(6, 9.5f, -12.4f, 12, 9.6f, -10.3f, dashA | 0x8FB8FF);              // centre screen
        if (t < CRANK_FROM) return;
        float on = engineOn(t) ? 1 : 0.3f + 0.3f * (float) Math.abs(Math.sin(t * 30)); // flicker while cranking
        int white = ((int) (255 * on) << 24) | 0xF2F7FF;
        // slim daytime-running strip on top, main lamps below
        for (int s = -1; s <= 1; s += 2) {
            float x0 = s < 0 ? -6.2f : 13.0f, x1 = s < 0 ? -0.8f : 18.4f;
            m.box(x0, 12.5f, -47.5f, x1, 13.0f, -47.35f, white);
            for (int k = 0; k < 3; k++) { float cx = x0 + 1.4f + k * 1.5f; m.box(cx - 0.45f, 16.0f, -47.5f, cx + 0.45f, 16.9f, -47.35f, ((int) (220 * on) << 24) | 0xDDEBFF); }
        }
        if (!engineOn(t)) return;
        // the grille outline lights up
        int glow = 0xFFE8F2FF;
        for (float kx : new float[]{3.0f, 9.0f}) {
            float x0 = kx - 2.8f, x1 = kx + 2.8f, y0 = 12.15f, y1 = 20.45f, z = -47.62f;
            m.line(new float[]{x0, y0, z}, new float[]{x1, y0, z}, 0.13f, glow); m.line(new float[]{x0, y1, z}, new float[]{x1, y1, z}, 0.13f, glow);
            m.line(new float[]{x0, y0, z}, new float[]{x0, y1, z}, 0.13f, glow); m.line(new float[]{x1, y0, z}, new float[]{x1, y1, z}, 0.13f, glow);
        }
        // thin red light bar across the tail
        m.box(-6.2f, 12.7f, 33.36f, 18.2f, 13.3f, 33.5f, 0xFFFF2236);
        m.box(-7.6f, 12.5f, 31.8f, -6.2f, 14.6f, 33.5f, 0xFFFF2236); m.box(18.2f, 12.5f, 31.8f, 19.6f, 14.6f, 33.5f, 0xFFFF2236);
    }

    // ================================================================== Money Flex: cash stack, bill fan, money rain, gold chain
    private static void bill(CosmeticsFeature.Mesh m, float cx, float cy, float cz, float yaw, float roll, float s) {
        float cyaw = (float) Math.cos(yaw), syaw = (float) Math.sin(yaw), cr = (float) Math.cos(roll), sr = (float) Math.sin(roll);
        float hw = 3.2f * s, hh = 1.5f * s;
        float[][] c = new float[4][];
        float[][] local = {{-hw, -hh}, {hw, -hh}, {hw, hh}, {-hw, hh}};
        for (int i = 0; i < 4; i++) {
            float x = local[i][0], zz = local[i][1];
            float y = x * sr; x = x * cr;               // tumble
            c[i] = new float[]{cx + x * cyaw - zz * syaw, cy + y, cz + x * syaw + zz * cyaw};
        }
        m.quad(c[0], c[1], c[2], c[3], 0xFF4FAE5B);
        float[] mid0 = lerp3(c[0], c[3], 0.5f), mid1 = lerp3(c[1], c[2], 0.5f);
        m.line(lerp3(mid0, mid1, 0.35f), lerp3(mid0, mid1, 0.65f), 0.35f * s, 0xFF2E7A3A); // portrait band
    }
    private static float[] lerp3(float[] a, float[] b, float k) { return new float[]{a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k, a[2] + (b[2] - a[2]) * k}; }

    private static void moneyLit(CosmeticsFeature.Mesh m, float t) {
        // cash stack in the left hand (left arm pitch -1.0 -> hand at about x 5.5, y 8, z -8.5)
        m.box(3.4f, 7.2f, -11.4f, 8.2f, 9.6f, -7.2f, 0xFF4FAE5B);
        m.box(3.35f, 7.15f, -9.8f, 8.25f, 9.65f, -8.8f, 0xFFF2E6C2); // paper band
        for (int i = 0; i < 4; i++) m.box(3.5f, 7.4f + i * 0.55f, -11.45f, 8.1f, 7.6f + i * 0.55f, -11.35f, 0xFF3B8A47);
        // a fan of bills in the throwing hand (above the head, in front)
        float flick = Math.max(0, (float) Math.sin(t * 7));
        float p = -2.3f - 0.55f * flick;
        float hy = 2 + 10 * (float) Math.cos(p), hz = 10 * (float) Math.sin(p);
        for (int i = 0; i < 4; i++) bill(m, -5 + (i - 1.5f) * 1.2f, hy - 1, hz - 0.5f, 1.2f + (i - 1.5f) * 0.25f, 1.3f, 0.75f);
        // money rain all around: bills tumbling down, looping
        for (int i = 0; i < 22; i++) {
            float seed = i * 12.9898f;
            float ang = (seed * 0.37f) % 6.283f, rad = 6 + (i * 7.3f) % 16;
            float fall = ((t * (9 + i % 5) + i * 6.1f) % 58);
            float y = -34 + fall;
            float sway = (float) Math.sin(t * 2.2f + i) * 3;
            float x = (float) Math.cos(ang) * rad + sway, z = (float) Math.sin(ang) * rad;
            if (y > 23.5f) y = 23.5f; // settled on the ground for a moment
            bill(m, x, y, z, t * (1.5f + i % 3) + i, y >= 23.5f ? 0 : (float) Math.sin(t * 5 + i) * 1.2f, 0.9f);
        }
    }
    private static void moneyGlow(CosmeticsFeature.Mesh m, float t) {
        // gold chain hanging on the chest with a big pendant
        int gold = 0xFFFFC93D;
        float sw = (float) Math.sin(t * 3.5f) * 0.3f;
        float[] prev = null;
        for (int i = 0; i <= 12; i++) {
            float a = (float) (Math.PI * i / 12);
            float[] p = {-3.6f * (float) Math.cos(a) + sw * (float) Math.sin(a), 0.4f + 4.2f * (float) Math.sin(a), -2.3f - 0.4f * (float) Math.sin(a)};
            if (prev != null) m.line(prev, p, 0.42f, gold);
            prev = p;
        }
        m.box(-1.3f + sw, 4.5f, -2.95f, 1.3f + sw, 7.1f, -2.6f, gold);
        m.box(-0.5f + sw, 5.3f, -3.0f, 0.5f + sw, 6.3f, -2.9f, 0xFFFFF4C2); // sparkle
    }

    // ================================================================== Sunbathe: lounger, umbrella, drink
    private static final float LOUNGE_A = 0.95f;
    private static void loungerLit(CosmeticsFeature.Mesh m, float t) {
        int wood = 0xFFB98A5C, woodDark = 0xFF8A6440;
        // leg rest under the legs (y = 21) and backrest along the reclined back
        for (int i = 0; i < 6; i++) {
            float z0 = -24 + i * 4, z1 = z0 + 4;
            m.box(-5, 20.6f, z0, 5, 21.4f, z1, i % 2 == 0 ? 0xFF2BB3C8 : 0xFFF2F2F2);
        }
        float c = (float) Math.cos(LOUNGE_A), s = (float) Math.sin(LOUNGE_A);
        for (int i = 0; i < 4; i++) { // reclined backrest, striped
            float a0 = i * 4, a1 = a0 + 4;
            float[] p0 = {-5, 21 - a0 * c, 2.2f + a0 * s}, p1 = {5, 21 - a0 * c, 2.2f + a0 * s}, p2 = {5, 21 - a1 * c, 2.2f + a1 * s}, p3 = {-5, 21 - a1 * c, 2.2f + a1 * s};
            m.quad(p0, p1, p2, p3, i % 2 == 0 ? 0xFFF2F2F2 : 0xFF2BB3C8);
        }
        // frame
        m.box(-5.6f, 21.4f, -24, -4.8f, 22.2f, 2, wood); m.box(4.8f, 21.4f, -24, 5.6f, 22.2f, 2, wood);
        for (float lz : new float[]{-23, 0}) { m.box(-5.6f, 22, lz, -4.6f, 24, lz + 1, woodDark); m.box(4.6f, 22, lz, 5.6f, 24, lz + 1, woodDark); }
        m.line(new float[]{-5.2f, 21, 2.2f}, new float[]{-5.2f, 21 - 16 * c, 2.2f + 16 * s}, 0.45f, wood);
        m.line(new float[]{5.2f, 21, 2.2f}, new float[]{5.2f, 21 - 16 * c, 2.2f + 16 * s}, 0.45f, wood);
        m.line(new float[]{-5.2f, 21 - 12 * c, 2.2f + 12 * s}, new float[]{-5.2f, 24, 6}, 0.45f, woodDark);
        m.line(new float[]{5.2f, 21 - 12 * c, 2.2f + 12 * s}, new float[]{5.2f, 24, 6}, 0.45f, woodDark);
        // umbrella
        float ux = 16, uz = -2;
        m.box(ux - 0.5f, -22, uz - 0.5f, ux + 0.5f, 24, uz + 0.5f, 0xFFE8E2D6);
        int n = 10; float R = 20;
        float sway = (float) Math.sin(t * 1.1f) * 0.6f;
        for (int i = 0; i < n; i++) {
            double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n;
            float[] top = {ux + sway, -27, uz};
            float[] e0 = {ux + (float) Math.cos(a0) * R, -18, uz + (float) Math.sin(a0) * R}, e1 = {ux + (float) Math.cos(a1) * R, -18, uz + (float) Math.sin(a1) * R};
            m.tri(top, e0, e1, i % 2 == 0 ? 0xFFF25C54 : 0xFFFFF4E0);
        }
        // little side table with a drink
        m.box(-12, 17, -8, -7, 18, -3, wood); m.box(-10, 18, -6, -9, 24, -5, woodDark);
        m.cylinder(-9.5f, -5.5f, 13.5f, 17, 1.1f, 1.4f, 8, 0x99D6F5FF, 0x99D6F5FF, false);
        m.line(new float[]{-9.2f, 14, -5.4f}, new float[]{-8.4f, 11.2f, -5.6f}, 0.25f, 0xFFF25C54);
        m.disc(-9.5f, 13.4f, -5.5f, 1.2f, 8, 0xFF7CD957); // lime slice on top
    }
    private static void loungerGlow(CosmeticsFeature.Mesh m, float t) {
        m.cylinder(-9.5f, -5.5f, 14.6f, 16.8f, 0.95f, 1.25f, 8, 0xFFFF9E3D, 0xFFFFC94D, false); // orange drink
    }

    // ================================================================== Campfire: log seat, fire, marshmallow
    private static void campLit(CosmeticsFeature.Mesh m, float t) {
        int bark = 0xFF6B4A2F, barkDark = 0xFF4E3521, ring = 0xFFC9A26B;
        m.box(-11, 18, -1, 11, 24, 5, bark);
        for (float x : new float[]{-11.01f, 11.01f}) m.quad(new float[]{x, 18.5f, -0.5f}, new float[]{x, 18.5f, 4.5f}, new float[]{x, 23.5f, 4.5f}, new float[]{x, 23.5f, -0.5f}, ring);
        for (int i = 0; i < 5; i++) m.box(-10 + i * 4.5f, 17.9f, -1.1f, -9 + i * 4.5f, 24, -0.9f, barkDark);
        // fire pit 18 px in front: stones + crossed logs
        float fz = -20;
        for (int i = 0; i < 9; i++) {
            double a = i * Math.PI * 2 / 9;
            float sx = (float) Math.cos(a) * 7, sz = fz + (float) Math.sin(a) * 7;
            m.box(sx - 1.6f, 21.5f, sz - 1.4f, sx + 1.6f, 24, sz + 1.4f, i % 2 == 0 ? 0xFF8C8C92 : 0xFF6E6E74);
        }
        m.box(-5, 22, fz - 1, 5, 24, fz + 1, barkDark); m.box(-1, 22, fz - 5, 1, 24, fz + 5, bark);
        // roasting stick from the right hand to above the fire
        float wob = (float) Math.sin(t * 1.7f) * 0.6f;
        float[] hand = {-5, 15.5f, -8}, tip = {-1.5f + wob, 13 + wob * 0.4f, fz + 2};
        m.line(hand, tip, 0.35f, 0xFF7A5634);
        float roast = Math.min(1, t / 7f);
        int mallow = dev.orbit.client.gui.Render2D.lerpColor(0xFFFFFFFF, 0xFFC98A3D, roast);
        m.box(tip[0] - 1.1f, tip[1] - 1.1f, tip[2] - 1.1f, tip[0] + 1.1f, tip[1] + 1.1f, tip[2] + 1.1f, mallow);
    }
    private static void campGlow(CosmeticsFeature.Mesh m, float t) {
        float fz = -20;
        for (int i = 0; i < 4; i++) { // flickering flame blades in crossed planes
            double a = i * Math.PI / 4;
            float h = 9 + 2.5f * (float) Math.sin(t * 9 + i * 1.7f), w = 4.5f - i * 0.4f;
            float dx = (float) Math.cos(a) * w, dz = (float) Math.sin(a) * w;
            float[] l = {-dx, 22.5f, fz - dz}, r = {dx, 22.5f, fz + dz}, top = {(float) Math.sin(t * 7 + i) * 0.8f, 22.5f - h, fz};
            m.triC(l, r, top, 0xFFFF6A1A, 0xFFFF6A1A, 0xFFFFE27A);
        }
        m.box(-2, 21.6f, fz - 2, 2, 22.4f, fz + 2, 0xFFFF9A2E); // embers
    }

    // ================================================================== DJ Set: booth with two decks
    private static void djLit(CosmeticsFeature.Mesh m, float t) {
        m.box(-13, 12.5f, -17, 13, 24, -8, 0xFF1E1E26);
        m.box(-13.5f, 12, -17.5f, 13.5f, 12.5f, -7.5f, 0xFF2E2E3A);
        for (float dx : new float[]{-6.5f, 6.5f}) { // records (spinning: a stripe shows it)
            m.disc(dx, 11.9f, -12.5f, 4.2f, 16, 0xFF0E0E10);
            double a = t * (dx < 0 ? 9 : 6);
            m.line(new float[]{dx, 11.85f, -12.5f}, new float[]{dx + (float) Math.cos(a) * 4, 11.85f, -12.5f + (float) Math.sin(a) * 4}, 0.3f, 0xFF3A3A44);
            m.line(new float[]{dx + 4.6f, 11.6f, -16}, new float[]{dx + 2, 11.6f, -12.5f}, 0.3f, 0xFFB9BCC6); // tone arm
        }
        for (float sx : new float[]{-19, 19}) { // speakers
            m.box(sx - 4, 6, -15, sx + 4, 24, -9, 0xFF15151B);
        }
    }
    private static void djGlow(CosmeticsFeature.Mesh m, float t) {
        for (int i = 0; i < 6; i++) { // light bars on the front, colour-cycling with the beat
            float hue = (t * 0.35f + i / 6f) % 1f, beat = Math.abs((float) Math.sin(t * 4.6f));
            int col = 0xFF000000 | java.awt.Color.HSBtoRGB(hue, 0.75f, 0.6f + 0.4f * beat) & 0xFFFFFF;
            m.box(-11 + i * 3.8f, 15, -17.2f, -9 + i * 3.8f, 22, -17.05f, col);
        }
        for (float dx : new float[]{-6.5f, 6.5f}) m.disc(dx, 11.8f, -12.5f, 1.3f, 10, dx < 0 ? 0xFFFF4DB8 : 0xFF4DD8FF); // labels
        for (float sx : new float[]{-19, 19}) {
            float pulse = 2.2f + Math.abs((float) Math.sin(t * 4.6f)) * 0.8f;
            for (float cy : new float[]{10, 18}) { // speaker cones facing forward
                float[] u = {1, 0, 0}, v = {0, 1, 0};
                ringIn(m, sx, cy, -15.1f, u, v, pulse, 0.35f, 10, 0xFFB18CFF);
            }
        }
    }

    // ================================================================== Hoverboard
    private static void boardLit(CosmeticsFeature.Mesh m, float t) {
        float y = 24 - Emote.hoverLift(t);
        m.box(-12, y, -5, 12, y + 1.6f, 5, 0xFF22252E);
        m.box(-13.5f, y + 0.2f, -4, -12, y + 1.4f, 4, 0xFF22252E); m.box(12, y + 0.2f, -4, 13.5f, y + 1.4f, 4, 0xFF22252E);
        m.box(-11, y - 0.1f, -4, 11, y, 4, 0xFF3B4152); // grip tape
        for (float tx : new float[]{-8, 8}) m.cylinder(tx, 0, y + 1.6f, y + 3.2f, 2.2f, 1.6f, 10, 0xFF9AA3B8, 0xFF5B6275, false);
    }
    private static void boardGlow(CosmeticsFeature.Mesh m, float t) {
        float y = 24 - Emote.hoverLift(t);
        int c = 0xFF4DE6FF;
        m.box(-12.2f, y + 0.6f, -5.2f, 12.2f, y + 1, -5, c); m.box(-12.2f, y + 0.6f, 5, 12.2f, y + 1, 5.2f, c);
        for (float tx : new float[]{-8, 8}) {
            m.disc(tx, y + 3.25f, 0, 1.5f, 10, c);
            float flick = 3 + (float) Math.sin(t * 20 + tx) * 0.8f;
            m.cylinder(tx, 0, y + 3.3f, y + 3.3f + flick, 1.4f, 0.2f, 8, 0xCC4DE6FF, 0x004DE6FF, true);
        }
    }

    // ================================================================== Joyride motion: the car stays parked, the engine starts
    /** press the start button, the starter cranks, the engine catches (a rev and a rock), then it idles */
    public static final float START_PRESS = 0.3f, CRANK_FROM = 0.55f, ENGINE_ON = 1.45f;
    public static boolean engineOn(float t) { return t >= ENGINE_ON; }
    /** applied in setupTransforms (y up, rotated by body yaw): the car (and you in it) shakes with the engine */
    public static void motion(PlayerEntityRenderState s, MatrixStack m) {
        float[] info = emoteOf(s);
        if (info == null || Emote.values()[(int) info[0]] != Emote.JOYRIDE) return;
        float t = info[1];
        float y = 0, roll = 0;
        if (t >= CRANK_FROM && t < ENGINE_ON) { // starter motor: a rough shudder
            y = 0.012f * (float) Math.sin(t * 38); roll = 0.006f * (float) Math.sin(t * 31);
        } else if (engineOn(t)) {
            float rev = Math.max(0, 1 - (t - ENGINE_ON) / 0.6f);          // the first rev rocks the car
            y = 0.004f * (float) Math.sin(t * 60) + 0.01f * rev * (float) Math.sin(t * 18);
            roll = 0.003f * (float) Math.sin(t * 47) + 0.03f * rev * (float) Math.sin((t - ENGINE_ON) * 9);
        }
        m.translate(0, y, 0);
        m.multiply(RotationAxis.POSITIVE_Z.rotation(roll));
    }

    // ================================================================== particles & sounds for prop emotes
    public static void tick() {
        var mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        for (PlayerEntity p : mc.world.getPlayers()) {
            float[] info = Emotes.info(p.getUuid());
            if (info == null) continue;
            Emote e = Emote.values()[(int) info[0]];
            if (!e.hasProp()) continue;
            float t = info[1];
            int tick = (int) (t * 20);
            double yawRad = Math.toRadians(p.bodyYaw);
            double fx = -Math.sin(yawRad), fz = Math.cos(yawRad); // forward
            switch (e) {
                case JOYRIDE -> {
                    double x = p.getX(), y = p.getY(), z = p.getZ();
                    int press = (int) (START_PRESS * 20), crank = (int) (CRANK_FROM * 20), on = (int) (ENGINE_ON * 20);
                    if (tick == press) mc.world.playSoundClient(x, y + 1, z, SoundEvents.BLOCK_STONE_BUTTON_CLICK_ON, SoundCategory.PLAYERS, 0.9f, 1.3f, false);
                    if (tick == crank || tick == crank + 7) mc.world.playSoundClient(x, y, z, SoundEvents.BLOCK_GRINDSTONE_USE, SoundCategory.PLAYERS, 0.55f, 0.55f, false); // starter
                    if (tick == on) { // it catches: a deep rev
                        mc.world.playSoundClient(x, y, z, SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.PLAYERS, 0.55f, 1.55f, false);
                        mc.world.playSoundClient(x, y, z, SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 0.35f, 0.5f, false);
                    }
                    if (tick > on && (tick - on) % 36 == 4) mc.world.playSoundClient(x, y, z, SoundEvents.ENTITY_MINECART_RIDING, SoundCategory.PLAYERS, 0.35f, 0.5f, false); // idle rumble
                    // exhaust smoke under the back of the car (x 0 and 12 px to your left, 34 px behind)
                    double lx = Math.cos(yawRad), lz = Math.sin(yawRad);
                    for (double side : new double[]{0, 12 / 16.0}) {
                        double ex = x + lx * side - fx * (34 / 16.0), ez = z + lz * side - fz * (34 / 16.0), ey = y + 0.2;
                        if (tick >= crank && tick < on && tick % 3 == 0) mc.world.addParticleClient(ParticleTypes.SMOKE, ex, ey, ez, -fx * 0.02, 0.01, -fz * 0.02);
                        if (tick >= on && tick < on + 8) mc.world.addParticleClient(ParticleTypes.LARGE_SMOKE, ex, ey, ez, -fx * 0.08, 0.02, -fz * 0.08);
                        else if (engineOn(t) && tick % 9 == 0) mc.world.addParticleClient(ParticleTypes.SMOKE, ex, ey, ez, -fx * 0.03, 0.005, -fz * 0.03);
                    }
                }
                case CAMPFIRE -> {
                    double x = p.getX() + fx * 1.25, z = p.getZ() + fz * 1.25;
                    if (tick % 3 == 0) mc.world.addParticleClient(ParticleTypes.FLAME, x + (Math.random() - 0.5) * 0.3, p.getY() + 0.25, z + (Math.random() - 0.5) * 0.3, 0, 0.02, 0);
                    if (tick % 6 == 0) mc.world.addParticleClient(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, p.getY() + 0.8, z, 0, 0.04, 0);
                    if (tick % 40 == 5) mc.world.playSoundClient(x, p.getY(), z, SoundEvents.BLOCK_CAMPFIRE_CRACKLE, SoundCategory.PLAYERS, 0.8f, 1f, false);
                }
                case DJ -> {
                    if (tick % 5 == 0) mc.world.addParticleClient(ParticleTypes.NOTE, p.getX() + fx * 0.9 + (Math.random() - 0.5), p.getY() + 1.1, p.getZ() + fz * 0.9 + (Math.random() - 0.5), Math.random(), 0, 0);
                    if (tick % 9 == 0) mc.world.playSoundClient(p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_NOTE_BLOCK_BASEDRUM.value(), SoundCategory.PLAYERS, 0.45f, 0.7f, false);
                    if (tick % 9 == 4) mc.world.playSoundClient(p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), SoundCategory.PLAYERS, 0.35f, 1.4f, false);
                }
                case MONEY -> {
                    if (tick % 4 == 0) mc.world.addParticleClient(ParticleTypes.HAPPY_VILLAGER, p.getX() + (Math.random() - 0.5) * 2, p.getY() + 1 + Math.random(), p.getZ() + (Math.random() - 0.5) * 2, 0, 0, 0);
                    if (tick % 9 == 0) mc.world.playSoundClient(p.getX(), p.getY() + 1.5, p.getZ(), SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, 0.7f, 1.3f + (float) Math.random() * 0.3f, false);
                    if (tick % 40 == 2) mc.world.playSoundClient(p.getX(), p.getY() + 1.5, p.getZ(), SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.PLAYERS, 0.6f, 1.6f, false);
                }
                case HOVERBOARD -> {
                    if (tick % 2 == 0) mc.world.addParticleClient(ParticleTypes.END_ROD, p.getX() + (Math.random() - 0.5) * 0.9, p.getY() + 0.05, p.getZ() + (Math.random() - 0.5) * 0.9, 0, -0.02, 0);
                }
                default -> { }
            }
        }
    }

    // ================================================================== "Tung Tung Bonk" death animation buddy
    /**
     * Draws the bat-swinging log buddy next to a dying entity (world space, y up). deathTime 0..20,
     * phi = direction from the victim to the buddy.
     */
    public static void renderBonker(MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState camera, double x, double y, double z,
                                    int light, float d, float phi, float victimHeight, double sqDist) {
        var tex = CosmeticsFeature.white();
        if (tex == null) return;
        float bx = (float) Math.cos(phi) * 1.45f, bz = (float) Math.sin(phi) * 1.45f;
        float rise = Math.min(1, d / 3f);
        rise = 1 - (1 - rise) * (1 - rise);
        matrices.push();
        matrices.translate(x + bx, y - (1 - rise) * 3.1, z + bz);
        // face the victim: local -z points at it
        matrices.multiply(RotationAxis.POSITIVE_Y.rotation((float) Math.atan2(Math.cos(phi), Math.sin(phi))));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotation(-BONKER_TURN)); // turned towards the camera (3/4 view); the bat still aims at the victim
        // body language: idle bob, squash on each tap, twist back for the wind-up, lunge into the BONK
        float bob = (float) Math.sin(d * 0.9f) * 0.02f, twist = 0, lean = 0, squash = 0;
        for (float k : new float[]{4, 7, 10}) squash = Math.max(squash, 1 - Math.abs(d - k) * 2.2f);
        if (d >= 11 && d < 11.7f) twist = (d - 11) / 0.7f * 0.7f;
        else if (d >= 11.7f && d < 12.4f) twist = 0.7f - (d - 11.7f) / 0.7f * 1.5f;
        else if (d >= 12.4f) twist = -0.8f + Math.min(1, (d - 12.4f) / 3) * 0.8f;
        if (d >= 11.8f && d < 15) lean = (float) Math.sin((d - 11.8f) / 3.2f * Math.PI) * 0.28f;
        matrices.translate(0, bob, 0);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotation(twist));
        matrices.multiply(RotationAxis.POSITIVE_X.rotation(-lean));
        matrices.scale(1 + 0.05f * squash, 1 - 0.1f * squash, 1 + 0.05f * squash);
        float swing = d;
        queue.submitCustom(matrices, RenderLayers.entityTranslucent(tex), (en, vc) -> bonker(new CosmeticsFeature.Mesh(en, vc, Math.max(light, 0x00B000B0)), swing, victimHeight));
        queue.submitCustom(matrices, RenderLayers.entityTranslucentEmissive(tex), (en, vc) -> bonkerRing(new CosmeticsFeature.Mesh(en, vc, CosmeticsFeature.FULL_BRIGHT), swing));
        // shouted words
        String word = d >= 4 && d < 5.6f || d >= 7 && d < 8.6f || d >= 10 && d < 11.4f ? "TUNG!" : d >= 12 && d < 16 ? "SAHUR!" : null;
        if (word != null) {
            Text txt = Text.literal(word).formatted(word.equals("TUNG!") ? Formatting.GOLD : Formatting.RED, Formatting.BOLD);
            float pop = (d % 3) * 0.08f;
            queue.submitLabel(matrices, new Vec3d(0, 3.3 + pop, 0), 0, txt, true, 0xF000F0, sqDist, camera);
        }
        matrices.pop();
    }

    /**
     * The bat buddy in pixels (~52 px tall), front = -z, the victim stands 23 px in front: a glossy orange-red
     * figure with a tall rounded body, big heavy-lidded eyes, a smirk, long skinny legs, big feet and a matching bat.
     */
    private static final float BONKER_TURN = 0.6f;
    private static final int TT_DARK = 0xFFB82E14, TT = 0xFFEE4F1E, TT_LIGHT = 0xFFFF8442, TT_SHINE = 0xFFFFC6A0, TT_LINE = 0xFF5E1206;
    private static void bonker(CosmeticsFeature.Mesh m, float d, float victimHeight) {
        boolean shout = d >= 4 && d < 5.6f || d >= 7 && d < 8.6f || d >= 10 && d < 11.4f || d >= 12 && d < 16;
        // big flat feet and long skinny legs with a bit of a knee
        for (float lx : new float[]{-2.4f, 2.4f}) {
            float foot = lx < 0 ? -0.25f : 0.25f;
            m.box(lx - 1.9f, 0, -3.2f, lx + 1.9f, 1.3f, 1.8f, TT_DARK);
            m.tube(List.of(new float[]{lx + foot, 0.65f, -3.2f}, new float[]{lx + foot * 2, 0.7f, -5.6f}), new float[]{1.25f, 1.05f}, 10, TT, TT_LIGHT); // rounded toes
            m.tube(List.of(new float[]{lx, 1.0f, -0.4f}, new float[]{lx * 1.15f, 9, -1.3f}, new float[]{lx * 0.85f, 18, 0}), new float[]{0.85f, 0.75f, 1.0f}, 10, TT_DARK, TT);
            m.ball(lx * 1.15f, 9, -1.3f, 0.95f, TT);                                          // knee
        }
        // the body: a tall rounded pill, darker low, glossy on top
        m.cylinder(0, 0, 17, 28, 4.0f, 5.0f, 18, TT_DARK, TT, true);
        m.cylinder(0, 0, 28, 44, 5.0f, 5.4f, 18, TT, TT_LIGHT, true);
        m.sphere(0, 44, 0, 5.4f, 6.2f, 5.4f, 18, TT_LIGHT, Math.PI);                         // rounded head
        m.sphere(0, 17, 0, 4.0f, 2.0f, 4.0f, 14, TT_DARK, Math.PI);                          // rounded bottom
        m.line(new float[]{-3.2f, 22, -4.0f}, new float[]{-3.9f, 46, -3.8f}, 0.35f, TT_SHINE); // gloss highlight
        m.line(new float[]{-2.2f, 47.5f, -4.4f}, new float[]{-1.0f, 49, -3.6f}, 0.3f, TT_SHINE);
        // big eyes with heavy, half-closed lids (that smug look), raised when he shouts
        float lookY = Math.max(-0.5f, Math.min(0.3f, (victimHeight * 16 - 36) / 24f));
        float lid = shout ? 0.15f : 0.55f;
        for (float ex : new float[]{-2.3f, 2.3f}) {
            vdisc(m, ex, 40.5f, -5.42f, 2.5f, 18, TT_LINE);                                 // outline
            vdisc(m, ex, 40.5f, -5.5f, 2.2f, 18, 0xFFFBF6EE);                               // white
            vdisc(m, ex, 40.1f + lookY, -5.58f, 1.25f, 14, 0xFF3A1E10);                     // iris
            vdisc(m, ex, 40.1f + lookY, -5.64f, 0.7f, 12, 0xFF080404);                      // pupil
            vdisc(m, ex + 0.5f, 40.6f + lookY, -5.7f, 0.28f, 6, 0xFFFFFFFF);                // shine
            lidCap(m, ex, 40.5f, -5.76f, 2.55f, lid, TT);                                   // eyelid over the top
            m.line(new float[]{ex - 2.4f, 40.5f + 2.5f * (1 - 2 * lid), -5.8f}, new float[]{ex + 2.4f, 40.5f + 2.5f * (1 - 2 * lid), -5.8f}, 0.22f, TT_LINE);
        }
        float brow = shout ? 1.2f : 0;
        m.line(new float[]{-4.2f, 43.9f + brow, -5.3f}, new float[]{-0.9f, 44.4f + brow, -5.4f}, 0.32f, TT_DARK);
        m.line(new float[]{0.9f, 44.6f + brow, -5.4f}, new float[]{4.2f, 44.3f + brow, -5.3f}, 0.32f, TT_DARK);
        // a little round nose
        m.ball(0.2f, 36.8f, -5.5f, 1.0f, TT_LIGHT);
        // mouth: a lopsided smirk, wide open when shouting
        if (shout) {
            float[] prev = null;
            for (int i = 0; i <= 8; i++) {
                float t = i / 8f, x = -2.8f + 5.8f * t, y = 33.4f + t * 0.5f - (float) Math.sin(t * Math.PI) * 0.6f;
                float depth = 1.8f + (float) Math.sin(t * Math.PI) * 1.1f;
                if (prev != null) m.quad(new float[]{prev[0], prev[1], -5.45f}, new float[]{x, y, -5.45f}, new float[]{x, y - depth, -5.45f}, new float[]{prev[0], prev[1] - prev[2], -5.45f}, 0xFF3A0804);
                prev = new float[]{x, y, depth};
            }
            m.box(-1.2f, 31.4f, -5.5f, 1.4f, 32.2f, -5.46f, 0xFFE0606A);                    // tongue
        } else {
            float[] prev = null;
            for (int i = 0; i <= 10; i++) {
                float t = i / 10f, x = -2.9f + 6.0f * t, y = 33.2f + t * t * 1.3f - (float) Math.sin(t * Math.PI) * 0.7f;
                float[] p = {x, y, -5.45f};
                if (prev != null) m.line(prev, p, 0.3f, TT_LINE);
                prev = p;
            }
            m.line(new float[]{3.1f, 34.5f, -5.4f}, new float[]{3.4f, 35.2f, -5.35f}, 0.22f, TT_LINE);   // the corner curls up
        }
        // long thin arms: left hangs down, swaying; mitten hands
        float sway = (float) Math.sin(d * 1.1f) * 0.8f;
        float[] lsh = {-5.0f, 35, 0}, lel = {-6.8f, 27, -0.3f + sway * 0.3f}, lha = {-6.6f, 19, -0.8f + sway};
        m.tube(List.of(lsh, lel, lha), new float[]{0.8f, 0.7f, 0.75f}, 10, TT, TT);
        m.ball(lha[0], lha[1] - 0.6f, lha[2], 1.25f, TT_LIGHT);
        // right arm holding the bat (same glossy colour, a big club); turned back so it still points at the victim
        float[] dir0 = batDirection(d, victimHeight);
        float ct = (float) Math.cos(BONKER_TURN), st = (float) Math.sin(BONKER_TURN);
        float[] dir = {dir0[0] * ct + dir0[2] * st, dir0[1], -dir0[0] * st + dir0[2] * ct};
        float[] sh = {5.0f, 35, 0};
        float[] el = {sh[0] + dir[0] * 5f + 1.2f, sh[1] + dir[1] * 5f - 1.5f, sh[2] + dir[2] * 5f};
        float[] hand = {sh[0] + dir[0] * 11f, sh[1] + dir[1] * 11f, sh[2] + dir[2] * 11f};
        m.tube(List.of(sh, el, hand), new float[]{0.8f, 0.7f, 0.75f}, 10, TT, TT);
        m.ball(hand[0], hand[1], hand[2], 1.35f, TT_LIGHT);
        float[] knob = add(hand, dir, -1.6f), g1 = add(hand, dir, 3), t1 = add(hand, dir, 10), b1 = add(hand, dir, 17), tip = add(hand, dir, 24);
        m.tube(List.of(knob, add(hand, dir, -0.8f)), new float[]{1.2f, 1.2f}, 10, TT_DARK, TT_DARK);
        m.tube(List.of(add(hand, dir, -0.8f), g1, t1, b1, add(hand, dir, 22), tip), new float[]{0.7f, 0.85f, 1.4f, 2.2f, 2.3f, 1.4f}, 12, TT, TT_LIGHT);
        float[] s0 = add(add(hand, dir, 8), new float[]{-0.6f, 0.4f, -0.6f}, 1), s1 = add(add(hand, dir, 20), new float[]{-0.6f, 0.4f, -0.6f}, 1.6f);
        m.line(s0, s1, 0.25f, TT_SHINE);                                                     // shine along the bat
    }
    /** the top part of an eye covered by a lid: everything above `cut` (0 = middle, 1 = top) */
    private static void lidCap(CosmeticsFeature.Mesh m, float cx, float cy, float z, float r, float lid, int col) {
        float yCut = cy + r * (1 - 2 * lid);
        int n = 18;
        for (int i = 0; i < n; i++) {
            double a0 = i * Math.PI / n, a1 = (i + 1) * Math.PI / n;
            float x0 = cx + (float) Math.cos(a0) * r, y0 = cy + (float) Math.sin(a0) * r, x1 = cx + (float) Math.cos(a1) * r, y1 = cy + (float) Math.sin(a1) * r;
            if (y0 < yCut && y1 < yCut) continue;
            m.quad(new float[]{x0, Math.max(y0, yCut), z}, new float[]{x1, Math.max(y1, yCut), z}, new float[]{x1, yCut, z}, new float[]{x0, yCut, z}, col);
        }
    }
    /** the glowing magic circle under his feet */
    private static void bonkerRing(CosmeticsFeature.Mesh m, float d) {
        float a = Math.min(1, d / 3f) * (d > 17 ? Math.max(0, (20 - d) / 3f) : 1);
        if (a <= 0) return;
        int c = ((int) (a * 220) << 24) | 0xEAF6FF;
        m.torus(0, 0.25f, 0, 11, 0.22f, 48, 4, 0, c);
        m.torus(0, 0.25f, 0, 8.5f, 0.15f, 40, 4, 0, ((int) (a * 150) << 24) | 0xB8E0FF);
        for (int i = 0; i < 12; i++) { // little runes turning around the circle
            double ang = i * Math.PI / 6 + d * 0.12;
            float x = (float) Math.cos(ang) * 9.8f, zz = (float) Math.sin(ang) * 9.8f;
            m.box(x - 0.35f, 0.2f, zz - 0.35f, x + 0.35f, 0.32f, zz + 0.35f, c);
        }
    }
    private static float[] add(float[] p, float[] d, float k) { return new float[]{p[0] + d[0] * k, p[1] + d[1] * k, p[2] + d[2] * k}; }
    /** a flat disc facing -z (for eyes) */
    private static void vdisc(CosmeticsFeature.Mesh m, float cx, float cy, float z, float r, int n, int col) {
        float[] c = {cx, cy, z};
        for (int i = 0; i < n; i++) {
            double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n;
            m.tri(c, new float[]{cx + (float) Math.cos(a0) * r, cy + (float) Math.sin(a0) * r, z}, new float[]{cx + (float) Math.cos(a1) * r, cy + (float) Math.sin(a1) * r, z}, col);
        }
    }

    /** where the bat points over time: raised, three taps on the head, wind-up, BONK, follow-through */
    private static float[] batDirection(float d, float vh) {
        float[] rest = norm(-0.25f, -0.8f, -0.55f), up = norm(0.15f, 1, 0.35f), tap = norm(-0.2f, (vh * 16 - 33) / 23f + 0.15f, -1), back = norm(0.4f, 0.55f, 1), hit = norm(-0.3f, 0.05f, -1), follow = norm(-1, 0.1f, 0.2f);
        if (d < 2) return rest;          // bat resting on the ground, like he's waiting at your door
        if (d < 3) return lerpDir(rest, up, d - 2);
        for (float k : new float[]{4, 7, 10}) {
            if (d >= k - 1 && d < k) return lerpDir(up, tap, (d - (k - 1)));
            if (d >= k && d < k + 1) return lerpDir(tap, up, (d - k));
        }
        if (d < 10) return up;
        if (d < 11) return up;
        if (d < 11.7f) return lerpDir(up, back, (d - 11) / 0.7f);
        if (d < 12.2f) return lerpDir(back, hit, (d - 11.7f) / 0.5f);
        if (d < 13.5f) return lerpDir(hit, follow, (d - 12.2f) / 1.3f);
        return follow;
    }
    private static float[] norm(float x, float y, float z) { float l = (float) Math.sqrt(x * x + y * y + z * z); return new float[]{x / l, y / l, z / l}; }
    private static float[] lerpDir(float[] a, float[] b, float k) {
        k = Math.max(0, Math.min(1, k)); k = k * k * (3 - 2 * k);
        return norm(a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k, a[2] + (b[2] - a[2]) * k);
    }

    /** victim launch for the death animation: called in setupTransforms (y up, rotated by body yaw) */
    public static void bonkLaunch(MatrixStack m, float d, float height, float bodyYaw, float phi) {
        if (d < 12) { // flinch on each tap
            float k = 0;
            for (float tap : new float[]{4, 7, 10}) k = Math.max(k, 1 - Math.abs(d - tap) * 2.5f);
            if (k > 0) { m.translate(0, height, 0); m.scale(1 + 0.06f * k, 1 - 0.14f * k, 1 + 0.06f * k); m.translate(0, -height, 0); }
            return;
        }
        float u = d - 12, lx = -(float) Math.cos(phi), lz = -(float) Math.sin(phi);
        float dist = u * 0.55f + u * u * 0.07f, up = u * 0.5f - u * u * 0.015f;
        float yawRot = 180 - bodyYaw;
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yawRot));
        m.translate(lx * dist, up, lz * dist);
        m.translate(0, height / 2, 0);
        m.multiply(new Quaternionf().rotateAxis(u * 0.75f, -lz, 0, lx));
        float sc = Math.max(0.35f, 1 - u * 0.06f);
        m.scale(sc, sc, sc);
        m.translate(0, -height / 2, 0);
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yawRot));
    }
    /** world offset of the flying victim (for the particle trail), same maths as bonkLaunch */
    public static double[] bonkOffset(float d, float phi) {
        if (d < 12) return new double[]{0, 0, 0};
        float u = d - 12;
        double dist = u * 0.55 + u * u * 0.07, up = u * 0.5 - u * u * 0.015;
        return new double[]{-Math.cos(phi) * dist, up, -Math.sin(phi) * dist};
    }

    // ================================================================== Hitboxes module (drawn per entity, world space)
    public static void renderHitbox(MatrixStack matrices, OrderedRenderCommandQueue queue, net.minecraft.client.render.entity.state.EntityRenderState s, double x, double y, double z) {
        var mc = MinecraftClient.getInstance();
        if (dev.orbit.client.OrbitClient.modules() == null || !(dev.orbit.client.OrbitClient.modules().get("hitboxes") instanceof dev.orbit.client.module.NewModules.Hitboxes h) || !h.isEnabled()) return;
        if (s.invisible || s.width <= 0 || s.height <= 0) return;
        boolean player = s.entityType == net.minecraft.entity.EntityType.PLAYER, living = s instanceof net.minecraft.client.render.entity.state.LivingEntityRenderState;
        if (player ? !h.players.on() : living ? !h.mobs.on() : !h.other.on()) return;
        if (s instanceof PlayerEntityRenderState ps && mc.player != null && ps.id == mc.player.getId() && mc.options.getPerspective().isFirstPerson()) return;
        var tex = CosmeticsFeature.white();
        if (tex == null) return;
        boolean aimed = false;
        if (h.target.on() && mc.targetedEntity != null) {
            var tp = mc.targetedEntity.getLerpedPos(mc.getRenderTickCounter().getTickProgress(false));
            aimed = mc.targetedEntity.getType() == s.entityType && tp.squaredDistanceTo(s.x, s.y, s.z) < 0.09;
        }
        int col = aimed ? h.targetColor.argb() : h.color.argb();
        float hw = s.width * 8, hh = s.height * 16, eye = s.standingEyeHeight * 16;
        boolean eyes = h.eyes.on() && living, look = h.look.on() && living;
        float yaw = 0, pitch = 0;
        if (s instanceof net.minecraft.client.render.entity.state.LivingEntityRenderState ls) { yaw = ls.bodyYaw + ls.relativeHeadYaw; pitch = ls.pitch; }
        float fy = yaw, fp = pitch;
        matrices.push();
        matrices.translate(x, y, z);
        queue.submitCustom(matrices, RenderLayers.entityTranslucentEmissive(tex), (en, vc) -> {
            var m = new CosmeticsFeature.Mesh(en, vc, CosmeticsFeature.FULL_BRIGHT);
            float t = 0.22f;
            float[][] c = {{-hw, 0, -hw}, {hw, 0, -hw}, {hw, 0, hw}, {-hw, 0, hw}, {-hw, hh, -hw}, {hw, hh, -hw}, {hw, hh, hw}, {-hw, hh, hw}};
            int[][] e = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
            for (int[] k : e) m.line(c[k[0]], c[k[1]], t, col);
            if (eyes) {
                float[][] r = {{-hw, eye, -hw}, {hw, eye, -hw}, {hw, eye, hw}, {-hw, eye, hw}};
                for (int i = 0; i < 4; i++) m.line(r[i], r[(i + 1) % 4], t * 0.8f, 0xFFFF4040);
            }
            if (look) {
                double ry = Math.toRadians(fy), rp = Math.toRadians(fp);
                float dx = (float) (-Math.sin(ry) * Math.cos(rp)), dyy = (float) -Math.sin(rp), dz = (float) (Math.cos(ry) * Math.cos(rp));
                float[] a = {0, eye, 0}, b = {dx * 32, eye + dyy * 32, dz * 32};
                m.line(a, b, t * 0.8f, 0xFF4D7BFF);
            }
        });
        matrices.pop();
    }}
