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
    private static void sportWheel(CosmeticsFeature.Mesh m, float cx, float cy, float cz, float r, float w, float spin, boolean left) {
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
        for (int k = 0; k < 5; k++) { // spokes
            double a = spin + k * Math.PI * 2 / 5;
            m.line(new float[]{rimX, cy, cz}, new float[]{rimX, cy + (float) Math.sin(a) * rr, cz + (float) Math.cos(a) * rr}, 0.55f, CHROME);
        }
        for (int i = 0; i < n; i++) { // rim lip
            double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n;
            m.line(new float[]{rimX, cy + (float) Math.sin(a0) * rr, cz + (float) Math.cos(a0) * rr}, new float[]{rimX, cy + (float) Math.sin(a1) * rr, cz + (float) Math.cos(a1) * rr}, 0.3f, CHROME);
        }
        m.box(rimX - 0.2f, cy - 0.7f, cz - 0.7f, rimX + 0.2f, cy + 0.7f, cz + 0.7f, 0xFF9AA0AC); // hub
    }

    private static void carLit(CosmeticsFeature.Mesh m, float t) {
        // driver at x = 0; body centre x = 6, 32 px wide, 70 px long (nose z = -44, tail z = 26)
        float[][] body = {
                {-44, 6, 12.5f, 18.2f, 21.2f},
                {-40, 6, 15f, 16.6f, 21.6f},
                {-30, 6, 16f, 15.2f, 21.8f},
                {-18, 6, 16.2f, 14.2f, 21.8f},
                {-11, 6, 16.4f, 13.8f, 21.8f},
                {0, 6, 16.6f, 13.6f, 21.8f},
                {11, 6, 16.6f, 13.4f, 21.8f},
                {19, 6, 16.4f, 13.2f, 21.6f},
                {24, 6, 15.4f, 13.8f, 21.2f},
                {27, 6, 13.5f, 15.4f, 20.6f}};
        hull(m, body, -11, 11);
        float spin = carSpin(t);
        for (float wz : new float[]{-29, 14}) { sportWheel(m, -11.2f, 18.6f, wz, 5.6f, 3.8f, spin, true); sportWheel(m, 23.2f, 18.6f, wz, 5.6f, 3.8f, spin, false); }
        // wheel arches (dark lips around the wheels)
        for (float wz : new float[]{-29, 14}) for (float ax : new float[]{-10.1f, 22.1f}) {
            for (int i = 0; i <= 8; i++) {
                double a0 = Math.PI + i * Math.PI / 9, a1 = Math.PI + (i + 1) * Math.PI / 9;
                m.line(new float[]{ax, 18.6f + (float) Math.sin(a0) * 6.6f, wz + (float) Math.cos(a0) * 6.6f}, new float[]{ax, 18.6f + (float) Math.sin(a1) * 6.6f, wz + (float) Math.cos(a1) * 6.6f}, 0.45f, TRIM);
            }
        }
        // cockpit: floor, seats with headrests, dashboard, steering wheel
        m.box(-8, 19.4f, -11, 20, 19.9f, 11, 0xFF1D1D23);
        for (float sx : new float[]{0, 12}) {
            m.box(sx - 4.6f, 16.6f, -5, sx + 4.6f, 18.6f, 4, 0xFF5A2E22);        // cushion (cognac leather)
            m.box(sx - 4.6f, 8.4f, 3.4f, sx + 4.6f, 18.6f, 6.4f, 0xFF5A2E22);    // back
            m.box(sx - 4.6f, 9, 3.2f, sx + 4.6f, 10, 3.5f, 0xFF1E5BD8);          // M stripes: light blue, dark blue, red
            m.box(sx - 4.6f, 10, 3.2f, sx + 4.6f, 10.6f, 3.5f, 0xFF16206B);
            m.box(sx - 4.6f, 10.6f, 3.2f, sx + 4.6f, 11.2f, 3.5f, 0xFFE0242F);
            m.box(sx - 2.6f, 5.6f, 4.2f, sx + 2.6f, 8.4f, 6.2f, 0xFF5A2E22);    // headrest
        }
        m.box(3.2f, 12.9f, -10.6f, 4.4f, 13.05f, -9.6f, 0xFF2A2A30);            // engine start button (right of the wheel)
        m.box(-8, 13.2f, -12, 20, 15.6f, -7.5f, 0xFF202027);                    // dashboard
        m.box(-1.2f, 13, -10.8f, 13.2f, 13.3f, -7.7f, 0xFF34343E);               // dash trim
        float steer = 0; // parked
        float[] u = {(float) Math.cos(steer), (float) Math.sin(steer) * 0.5f, 0}, v = {0, 0.75f, 0.66f};
        ringIn(m, 0, 11.4f, -8.2f, u, v, 3.3f, 0.5f, 14, 0xFF141417);
        m.line(new float[]{0, 11.4f, -8.2f}, new float[]{0, 14, -11}, 0.5f, 0xFF141417);
        // windshield: glass + frame
        float[] w0 = {-8.4f, 13.6f, -11.2f}, w1 = {20.4f, 13.6f, -11.2f}, w2 = {19.6f, 6.4f, -6.2f}, w3 = {-7.6f, 6.4f, -6.2f};
        m.quad(w0, w1, w2, w3, GLASS);
        m.line(w0, w3, 0.55f, TRIM); m.line(w1, w2, 0.55f, TRIM); m.line(w3, w2, 0.55f, TRIM);
        // roll hoops behind the seats
        for (float sx : new float[]{0, 12}) { m.line(new float[]{sx - 3.5f, 13.4f, 8.5f}, new float[]{sx - 2.5f, 6.5f, 9}, 0.5f, CHROME); m.line(new float[]{sx + 3.5f, 13.4f, 8.5f}, new float[]{sx + 2.5f, 6.5f, 9}, 0.5f, CHROME); m.line(new float[]{sx - 2.5f, 6.5f, 9}, new float[]{sx + 2.5f, 6.5f, 9}, 0.5f, CHROME); }
        // front: the twin kidney grille (chrome surround, black slats), big lower intake, splitter
        for (float kx : new float[]{3.3f, 8.7f}) { // the nose face runs from y 18.2 (top) to 21.2
            m.box(kx - 2.3f, 17.7f, -44.75f, kx + 2.3f, 20.4f, -43.6f, CHROME);
            m.box(kx - 1.9f, 18.0f, -44.85f, kx + 1.9f, 20.1f, -44.7f, 0xFF08080A);
            for (int i = 0; i < 5; i++) m.box(kx - 1.75f + i * 0.85f, 18.05f, -44.95f, kx - 1.45f + i * 0.85f, 20.05f, -44.8f, 0xFF3A3A42);
        }
        m.box(-5, 20.5f, -44.25f, 17, 21.1f, -43.9f, TRIM);                    // lower intake
        for (int i = 0; i < 6; i++) if (i != 2 && i != 3) m.box(-4.5f + i * 3.6f, 18.4f, -44.4f, -3.9f + i * 3.6f, 20.3f, -44.2f, 0xFF2C2C33);
        m.box(-7, 21.2f, -45.2f, 19, 21.7f, -41, TRIM);
        // round badge at the front of the bonnet (plain colours — no real logo)
        m.box(5.0f, 16.35f, -40.6f, 7.0f, 16.5f, -38.8f, CHROME);
        m.box(5.3f, 16.25f, -40.3f, 6.0f, 16.38f, -39.7f, 0xFF2E86E6); m.box(6.0f, 16.25f, -39.7f, 6.7f, 16.38f, -39.1f, 0xFF2E86E6);
        m.box(6.0f, 16.25f, -40.3f, 6.7f, 16.38f, -39.7f, 0xFFF2F2F2); m.box(5.3f, 16.25f, -39.7f, 6.0f, 16.38f, -39.1f, 0xFFF2F2F2);
        // hood vents + centre crease
        for (float hx : new float[]{1.5f, 10.5f}) m.box(hx - 1.6f, 14.6f, -32, hx + 1.6f, 14.75f, -24, TRIM);
        m.line(new float[]{6, 14.3f, -40}, new float[]{6, 13.85f, -12}, 0.18f, PAINT_TOP);
        // side: door line, side intake, mirrors
        for (float sx : new float[]{-10.05f, 22.05f}) {
            m.line(new float[]{sx, 14.4f, -10}, new float[]{sx, 20.4f, -10}, 0.15f, PAINT_LOW);
            m.line(new float[]{sx, 14.2f, 9}, new float[]{sx, 20.4f, 9}, 0.15f, PAINT_LOW);
            m.box(sx - 0.15f, 16.8f, 10, sx + 0.15f, 19.2f, 13, TRIM);
        }
        m.box(-12.4f, 11.6f, -9.6f, -10, 13.2f, -7.4f, PAINT); m.box(22, 11.6f, -9.6f, 24.4f, 13.2f, -7.4f, PAINT);
        // rear: ducktail spoiler, diffuser with fins, quad exhausts
        m.quad(new float[]{-7, 13.2f, 23.5f}, new float[]{19, 13.2f, 23.5f}, new float[]{19, 12.2f, 27.4f}, new float[]{-7, 12.2f, 27.4f}, PAINT_TOP);
        m.box(-5, 19.8f, 26.4f, 17, 21.2f, 27.6f, TRIM);
        for (int i = 0; i < 5; i++) m.box(-3 + i * 4.5f, 20.2f, 27.6f, -2.6f + i * 4.5f, 21.2f, 29, 0xFF26262C);
        for (float ex : new float[]{-3.4f, -0.6f, 12.6f, 15.4f}) m.cylinder(ex, 27.8f, 19.1f, 19.1f + 0.01f, 1.05f, 1.05f, 8, CHROME, CHROME, false);
        for (float ex : new float[]{-3.4f, -0.6f, 12.6f, 15.4f}) m.box(ex - 1, 18.3f, 26.8f, ex + 1, 20f, 28.6f, CHROME);
    }
    private static void carGlow(CosmeticsFeature.Mesh m, float t) {
        float press = t >= START_PRESS && t < ENGINE_ON ? 1 : 0;
        m.box(3.35f, 12.85f, -10.45f, 4.25f, 12.9f, -9.75f, press > 0 || engineOn(t) ? 0xFFFF3B3B : 0x60FF3B3B); // start button lights up
        if (t < START_PRESS) return; // everything else is dark until you press start
        // the lights wake up: dashboard first, headlights when the engine catches
        float wake = Math.min(1, (t - START_PRESS) / 0.5f);
        int dashA = (int) (255 * wake) << 24;
        m.box(-3, 13.05f, -10.9f, 3, 13.15f, -8.6f, dashA | 0x59D2FF);
        m.box(7, 13.05f, -10.9f, 12, 13.15f, -8.6f, dashA | 0xFF9A3C);
        if (t < CRANK_FROM) return;
        // "angel eye" headlights: two glowing rings per side, plus the light inside
        float on = engineOn(t) ? 1 : 0.35f + 0.25f * (float) Math.abs(Math.sin(t * 30)); // flicker while cranking
        int ring = ((int) (255 * on) << 24) | 0xEAF4FF;
        float[] u = {1, 0, 0}, v = {0, -0.37f, 0.93f}; // lying on the sloped front of the bonnet
        for (float rx : new float[]{-5.9f, -2.6f, 14.6f, 17.9f}) {
            ringIn(m, rx, 17.35f, -42.5f, u, v, 1.25f, 0.22f, 12, ring);
            m.box(rx - 0.45f, 17.2f, -42.8f, rx + 0.45f, 17.45f, -42.2f, ring);
        }
        if (!engineOn(t)) return;
        // L-shaped LED tail lights
        for (float sx : new float[]{-1, 1}) {
            float x0 = sx < 0 ? -8 : 13, x1 = sx < 0 ? -1 : 20;
            m.box(x0, 14.8f, 27.05f, x1, 15.6f, 27.35f, 0xFFFF2236);
            float xv = sx < 0 ? -8 : 19.2f;
            m.box(xv, 14.8f, 26.4f, xv + 0.8f, 17.2f, 27.35f, 0xFFFF2236);
        }
        // exhaust flames now and then (pops on the throttle)
        if ((t % 2.6f) > 2.2f) for (float ex : new float[]{-3.4f, -0.6f, 12.6f, 15.4f}) {
            float len = 3 + (float) Math.sin(t * 40 + ex) * 1.2f;
            m.tri(new float[]{ex - 0.8f, 19.1f, 28.7f}, new float[]{ex + 0.8f, 19.1f, 28.7f}, new float[]{ex, 19.1f, 28.7f + len}, 0xFFFF8A2B);
            m.tri(new float[]{ex, 18.3f, 28.7f}, new float[]{ex, 19.9f, 28.7f}, new float[]{ex, 19.1f, 28.7f + len}, 0xFF5BB8FF);
        }
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
                    boolean backfire = engineOn(t) && (int) ((t % 2.6f) * 20) == 44;
                    if (backfire) mc.world.playSoundClient(x, y, z, SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS, 0.3f, 1.6f, false);
                    // exhaust smoke from the two pipe pairs at the back of the car (x 0 and 14 px to your left, 29 px behind)
                    double lx = Math.cos(yawRad), lz = Math.sin(yawRad);
                    for (double side : new double[]{-2 / 16.0, 14 / 16.0}) {
                        double ex = x + lx * side - fx * (29 / 16.0), ez = z + lz * side - fz * (29 / 16.0), ey = y + 0.3;
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
        queue.submitCustom(matrices, RenderLayers.entityTranslucent(tex), (en, vc) -> bonker(new CosmeticsFeature.Mesh(en, vc, light), swing, victimHeight));
        // shouted words
        String word = d >= 4 && d < 5.6f || d >= 7 && d < 8.6f || d >= 10 && d < 11.4f ? "TUNG!" : d >= 12 && d < 16 ? "SAHUR!" : null;
        if (word != null) {
            Text txt = Text.literal(word).formatted(word.equals("TUNG!") ? Formatting.GOLD : Formatting.RED, Formatting.BOLD);
            float pop = (d % 3) * 0.08f;
            queue.submitLabel(matrices, new Vec3d(0, 3.3 + pop, 0), 0, txt, true, 0xF000F0, sqDist, camera);
        }
        matrices.pop();
    }

    /** the log buddy in pixels: tall and slim (~48 px), front = -z, the victim stands 23 px in front */
    private static void bonker(CosmeticsFeature.Mesh m, float d, float victimHeight) {
        int woodLight = 0xFFE2A866, wood = 0xFFCF8E4E, woodDark = 0xFFA8693A, line = 0xFF5A3218;
        boolean shout = d >= 4 && d < 5.6f || d >= 7 && d < 8.6f || d >= 10 && d < 11.4f || d >= 12 && d < 16;
        // skinny legs with little feet
        for (float lx : new float[]{-2.2f, 2.2f}) {
            m.box(lx - 1.4f, 0, -2.6f, lx + 1.4f, 1.1f, 1.2f, woodDark);
            m.tube(List.of(new float[]{lx, 0.8f, 0}, new float[]{lx * 1.05f, 7.5f, -0.2f}, new float[]{lx * 0.9f, 15, 0}), new float[]{0.95f, 0.85f, 1.05f}, 8, woodDark, wood);
        }
        // the trunk: tall, smooth, slightly wider at the top, with a rounded head
        m.cylinder(0, 0, 14, 30, 4.6f, 5.0f, 14, woodDark, wood, true);
        m.cylinder(0, 0, 30, 44, 5.0f, 5.2f, 14, wood, woodLight, true);
        m.cylinder(0, 0, 44, 47, 5.2f, 4.2f, 14, woodLight, woodLight, true);
        m.cylinder(0, 0, 47, 48.4f, 4.2f, 2.4f, 14, woodLight, woodLight, false);
        m.disc(0, 14, 0, 4.6f, 14, woodDark);
        for (int i = 0; i < 6; i++) { // faint wood grain on the back and sides
            double a = Math.PI * 0.15 + i * Math.PI * 0.14;
            float gx = (float) Math.cos(a) * 5.05f, gz = (float) Math.sin(a) * 5.05f;
            m.line(new float[]{gx, 17 + i % 3, gz}, new float[]{gx * 0.99f, 40 - (i % 2) * 4, gz * 0.99f}, 0.12f, woodDark);
        }
        // big round staring eyes
        float lookY = Math.max(-0.5f, Math.min(0.3f, (victimHeight * 16 - 36) / 24f));
        for (float ex : new float[]{-2.2f, 2.2f}) {
            vdisc(m, ex, 38.5f, -5.15f, 2.15f, 14, 0xFF3A2010);           // dark rim
            vdisc(m, ex, 38.5f, -5.22f, 1.85f, 14, 0xFFF6F0E6);           // white
            vdisc(m, ex, 38.4f + lookY, -5.3f, 1.05f, 12, 0xFF4A2A14);    // brown iris
            vdisc(m, ex, 38.4f + lookY, -5.36f, 0.6f, 10, 0xFF0B0604);     // pupil
            vdisc(m, ex + 0.45f, 38.9f + lookY, -5.4f, 0.22f, 6, 0xFFFFFFFF);
        }
        // raised brows (higher still when shouting)
        float brow = shout ? 0.8f : 0;
        m.line(new float[]{-3.8f, 41.4f + brow, -5.1f}, new float[]{-0.9f, 41.8f + brow, -5.1f}, 0.3f, line);
        m.line(new float[]{0.9f, 41.8f + brow, -5.1f}, new float[]{3.8f, 41.4f + brow, -5.1f}, 0.3f, line);
        // nose: a small carved bump
        m.box(-0.6f, 34.6f, -5.7f, 0.6f, 36.2f, -4.8f, wood);
        m.line(new float[]{-0.7f, 34.5f, -5.75f}, new float[]{0.7f, 34.5f, -5.75f}, 0.12f, line);
        // mouth: a wide carved grin, opening when he shouts
        if (shout) {
            float[] prev = null;
            for (int i = 0; i <= 8; i++) {
                float t = i / 8f, x = -3 + 6 * t, y = 31.6f - (float) Math.sin(t * Math.PI) * 1.2f;
                if (prev != null) m.quad(new float[]{prev[0], prev[1], -5.12f}, new float[]{x, y, -5.12f}, new float[]{x, y - 1.6f - (float) Math.sin(t * Math.PI) * 0.8f, -5.12f}, new float[]{prev[0], prev[1] - 1.6f - (float) Math.sin((t - 0.125f) * Math.PI) * 0.8f, -5.12f}, 0xFF2A0E06);
                prev = new float[]{x, y};
            }
        } else {
            float[] prev = null;
            for (int i = 0; i <= 10; i++) {
                float t = i / 10f, x = -3.2f + 6.4f * t, y = 31.8f - (float) Math.sin(t * Math.PI) * 1.4f;
                float[] p = {x, y, -5.12f};
                if (prev != null) m.line(prev, p, 0.28f, line);
                prev = p;
            }
            m.line(new float[]{-3.4f, 32.2f, -5.1f}, new float[]{-3.0f, 31.6f, -5.1f}, 0.2f, line); // smile dimples
            m.line(new float[]{3.4f, 32.2f, -5.1f}, new float[]{3.0f, 31.6f, -5.1f}, 0.2f, line);
        }
        // long thin arms: left hangs down to the knee, swaying
        float sway = (float) Math.sin(d * 1.1f) * 0.8f;
        float[] lsh = {-4.8f, 33, 0}, lel = {-6.4f, 25.5f, -0.3f + sway * 0.3f}, lha = {-6.2f, 17.5f, -0.8f + sway};
        m.tube(List.of(lsh, lel, lha), new float[]{0.8f, 0.7f, 0.75f}, 8, wood, wood);
        m.box(lha[0] - 0.9f, lha[1] - 1.6f, lha[2] - 0.8f, lha[0] + 0.9f, lha[1] + 0.2f, lha[2] + 0.8f, woodDark);
        // right arm holding the bat
        float[] dir = batDirection(d, victimHeight);
        float[] sh = {4.8f, 33, 0};
        float[] el = {sh[0] + dir[0] * 5f + 1.2f, sh[1] + dir[1] * 5f - 1.5f, sh[2] + dir[2] * 5f};
        float[] hand = {sh[0] + dir[0] * 11f, sh[1] + dir[1] * 11f, sh[2] + dir[2] * 11f};
        m.tube(List.of(sh, el, hand), new float[]{0.8f, 0.7f, 0.75f}, 8, wood, wood);
        m.box(hand[0] - 1, hand[1] - 1, hand[2] - 1, hand[0] + 1, hand[1] + 1, hand[2] + 1, woodDark);
        // the bat: light wood, knob, slim handle, long barrel
        float[] knob = add(hand, dir, -1.4f), g1 = add(hand, dir, 3), t1 = add(hand, dir, 10), b1 = add(hand, dir, 17), tip = add(hand, dir, 23);
        m.tube(List.of(knob, add(hand, dir, -0.8f)), new float[]{1.1f, 1.1f}, 8, 0xFFB98A55, 0xFFB98A55);
        m.tube(List.of(add(hand, dir, -0.8f), g1, t1, b1, add(hand, dir, 21.5f), tip), new float[]{0.65f, 0.75f, 1.1f, 1.75f, 1.8f, 1.1f}, 10, 0xFFC99A64, 0xFFE8C48C);
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
