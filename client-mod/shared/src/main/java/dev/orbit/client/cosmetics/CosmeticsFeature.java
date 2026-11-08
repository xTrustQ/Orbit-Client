package dev.orbit.client.cosmetics;

import dev.orbit.client.OrbitClient;
import dev.orbit.client.gui.Tex;
import dev.orbit.client.module.NewModules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Orbit cosmetics — hats, wings and auras — built as shaded 3D meshes in code (no model files).
 * Drawn on your own player in-game and on the Locker's preview players.
 *
 * Units are pixels (1/16 block). Model space: -Y is up, +Z is the player's back.
 * Hats are drawn after the head transform (head cube: x,z in [-4,4], y in [-8,0]),
 * wings after the body transform (body: x [-4,4], y [0,12], z [-2,2]),
 * auras in plain model space (neck at y=0, feet at y=24).
 */
public final class CosmeticsFeature extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {
    private static Identifier white;
    /** render-state id used by the title-screen PlayerPreview (tiles use ids below it) */
    public static final int PREVIEW_ID = -77_777;
    /** what the Locker is previewing (null = use your equipped cosmetic) */
    public static volatile String previewHat, previewWings, previewAura;
    /** Locker tiles: render-state id -> {hat, wings, aura} shown on that tile's little player */
    public static final java.util.Map<Integer, String[]> OVERRIDES = new java.util.concurrent.ConcurrentHashMap<>();
    static final int FULL_BRIGHT = 0xF000F0;

    public CosmeticsFeature(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> ctx) { super(ctx); }

    static Identifier white() {
        if (white == null) {
            try {
                var img = new java.awt.image.BufferedImage(4, 4, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) img.setRGB(x, y, 0xFFFFFFFF);
                var out = new java.io.ByteArrayOutputStream();
                javax.imageio.ImageIO.write(img, "png", out);
                white = Tex.fromPng("cosmetics-white", out.toByteArray(), false);
            } catch (Exception e) { OrbitClient.LOG.warn("Cosmetics texture failed", e); }
        }
        return white;
    }

    @Override
    public void render(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, PlayerEntityRenderState state, float limbAngle, float yaw) {
        var mc = MinecraftClient.getInstance();
        boolean preview = state.id <= PREVIEW_ID;
        if (state.invisible || OrbitClient.modules() == null) return;
        if (!(OrbitClient.modules().get("cosmetics") instanceof NewModules.Cosmetics cos)) return;
        boolean self = !preview && mc.player != null && state.id == mc.player.getId();
        CosmeticsApi.Remote remote = null;
        if (!preview && !self) { // another player: show what they wear in Orbit (from the cosmetics API)
            if (!cos.others.on() || mc.world == null || !(mc.world.getEntityById(state.id) instanceof net.minecraft.entity.player.PlayerEntity p)) return;
            remote = CosmeticsApi.of(p.getUuid());
            if (remote == null) return;
        } else if (!cos.isEnabled() && !preview) return;
        Identifier tex = white();
        if (tex == null) return;
        String[] ov = remote != null ? new String[]{remote.hat(), remote.wings(), remote.aura()} : OVERRIDES.get(state.id);
        String hat = pick(ov, 0, preview, previewHat, cos.isEnabled() ? cos.hat.get() : "None");
        String wings = pick(ov, 1, preview, previewWings, cos.isEnabled() ? cos.wings.get() : "None");
        String aura = pick(ov, 2, preview, previewAura, cos.isEnabled() ? cos.aura.get() : "None");
        float t = (System.currentTimeMillis() % 3_600_000L) / 1000f;
        var lit = RenderLayers.entityTranslucent(tex);
        var glow = RenderLayers.entityTranslucentEmissive(tex);

        if (!hat.equals("None")) {
            matrices.push();
            getContextModel().head.applyTransform(matrices);
            queue.submitCustom(matrices, lit, (e, vc) -> Hats.lit(new Mesh(e, vc, light), hat, t));
            queue.submitCustom(matrices, glow, (e, vc) -> Hats.glow(new Mesh(e, vc, FULL_BRIGHT), hat, t));
            matrices.pop();
        }
        if (!wings.equals("None")) {
            matrices.push();
            getContextModel().body.applyTransform(matrices);
            boolean gliding = state.isGliding; // (glidingTicks includes the partial tick, so it is never 0)
            // flap with movement: still when standing, flapping when walking, fast when sprinting
            float[] mv = MOTION.computeIfAbsent(state.id, k -> new float[]{0, 0, t});
            float dt = Math.max(0, Math.min(0.1f, t - mv[2]));
            float target = preview ? 0.6f : Math.min(1f, state.limbSwingAmplitude * 1.6f);
            mv[1] += (target - mv[1]) * Math.min(1f, dt * 5f);
            float speed = (wings.equals("Butterfly") ? 1.5f : 1f) * (1.5f + mv[1] * 4.5f);
            mv[0] = (mv[0] + dt * speed) % 6283.185f;
            mv[2] = t;
            float phase = mv[0], energy = mv[1];
            if (MOTION.size() > 256) MOTION.clear();
            queue.submitCustom(matrices, lit, (e, vc) -> Wings.draw(new Mesh(e, vc, light), wings, phase, energy, gliding));
            matrices.pop();
        }
        if (!aura.equals("None")) {
            queue.submitCustom(matrices, glow, (e, vc) -> Auras.draw(new Mesh(e, vc, FULL_BRIGHT), aura, t));
        }
    }

    /** per player: wing phase, smoothed movement (0 = standing, 1 = sprinting), last time */
    private static final java.util.Map<Integer, float[]> MOTION = new java.util.HashMap<>();

    private static String pick(String[] ov, int i, boolean preview, String previewValue, String equipped) {
        if (ov != null && ov.length > i && ov[i] != null) return ov[i];
        if (ov != null) return "None";
        if (preview && previewValue != null) return previewValue;
        return equipped;
    }

    // =====================================================================================================
    // Hats (head space)
    // =====================================================================================================
    private static final class Hats {
        static void lit(Mesh m, String hat, float t) {
            switch (hat) {
                case "Crown" -> crown(m);
                case "Top Hat" -> topHat(m);
                case "Devil Horns" -> { horn(m, 1); horn(m, -1); }
                case "Cat Ears" -> { ear(m, 1); ear(m, -1); }
                case "Witch Hat" -> witch(m, t);
                default -> {}
            }
        }
        static void glow(Mesh m, String hat, float t) {
            switch (hat) {
                case "Halo" -> halo(m, t);
                case "Crown" -> crownGems(m, t);
                case "Witch Hat" -> { float a = t * 1.6f; m.octa(Math.cos(a) * 6.5f, -15 + Math.sin(t * 2) * 1.2f, Math.sin(a) * 6.5f, 0.55f, 0.55f, 0.55f, 0xF0FFE27A); }
                default -> {}
            }
        }

        static void halo(Mesh m, float t) {
            float y = -11.6f + (float) Math.sin(t * 2.2) * 0.45f, spin = t * 0.6f;
            m.torus(0, y, 0, 4.2f, 0.42f, 40, 8, spin, 0xFFFFF1B8);
            m.torus(0, y, 0, 4.2f, 1.25f, 40, 8, spin, 0x2EFFD86B); // soft bloom shell
            for (int i = 0; i < 3; i++) {
                double a = t * 1.3 + i * Math.PI * 2 / 3;
                float s = 0.35f + 0.15f * (float) Math.sin(t * 4 + i);
                m.octa(Math.cos(a) * 5.4, y - 0.4 + Math.sin(t * 3 + i) * 0.5, Math.sin(a) * 5.4, s, s * 1.6f, s, 0xF0FFFFFF);
            }
        }

        static void crown(Mesh m) {
            int n = 16;
            m.cylinder(0, 0, -7.9f, -10.1f, 4.55f, 4.95f, n, 0xFFD9A62A, 0xFFFFDA6A, true);  // band (open)
            m.cylinder(0, 0, -7.9f, -10.1f, 4.2f, 4.6f, n, 0xFF5E0C16, 0xFF7A1220, true);   // velvet inside
            m.disc(0, -8.3f, 0, 4.3f, n, 0xFF6B0F1A);                                          // velvet cap
            m.ring(0, -10.1f, 0, 4.4f, 5.05f, n, 0xFFFFE58A);                                  // rim
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI * 2 / 8;
                float cx = (float) Math.cos(a) * 4.75f, cz = (float) Math.sin(a) * 4.75f;
                List<float[]> path = List.of(new float[]{cx, -10f, cz}, new float[]{cx * 1.02f, -11.6f, cz * 1.02f}, new float[]{cx * 1.03f, -12.9f, cz * 1.03f});
                m.tube(path, new float[]{1.05f, 0.55f, 0.08f}, 6, 0xFFE9B93A, 0xFFFFEFA8);
                m.octa(cx * 1.03f, -13.3f, cz * 1.03f, 0.42f, 0.42f, 0.42f, 0xFFFFF6D0);       // pearl on each point
            }
        }
        static void crownGems(Mesh m, float t) {
            int[] cols = {0xFFFF3B5C, 0xFF3BA8FF, 0xFF3BFF9A, 0xFFB86BFF};
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI * 2 / 8 + Math.PI / 8;
                float pulse = 0.55f + 0.08f * (float) Math.sin(t * 3 + i);
                m.octa(Math.cos(a) * 5.0, -9.0, Math.sin(a) * 5.0, pulse, pulse * 1.15f, pulse, cols[i % 4]);
            }
        }

        static void topHat(Mesh m) {
            m.ring(0, -8.05f, 0, 3.9f, 7.0f, 28, 0xFF17171C);                                  // brim
            m.cylinder(0, 0, -8.05f, -8.75f, 7.0f, 7.25f, 28, 0xFF101014, 0xFF26262E, true);    // brim edge curl
            m.cylinder(0, 0, -8.1f, -9.7f, 4.0f, 4.05f, 28, 0xFF8E0F24, 0xFFC0213C, true);      // satin band
            m.cylinder(0, 0, -9.7f, -16.4f, 4.0f, 4.35f, 28, 0xFF18181E, 0xFF2C2C36, true);     // crown, slight flare
            m.disc(0, -16.4f, 0, 4.35f, 28, 0xFF22222A);
            m.box(-1.2f, -9.6f, -4.15f, 1.2f, -8.2f, -4.05f, 0xFFF2C94C);                       // buckle
            m.box(-0.6f, -9.2f, -4.2f, 0.6f, -8.6f, -4.12f, 0xFF8E0F24);
        }

        static void horn(Mesh m, int side) {
            List<float[]> path = List.of(new float[]{side * 2.6f, -7.6f, -0.8f}, new float[]{side * 3.4f, -9.6f, -1.2f},
                    new float[]{side * 4.3f, -11.5f, -2.0f}, new float[]{side * 4.6f, -13.1f, -3.2f}, new float[]{side * 4.1f, -14.2f, -4.6f});
            m.tube(path, new float[]{1.25f, 1.0f, 0.72f, 0.42f, 0.06f}, 10, 0xFFC2142E, 0xFF1A0306);
        }

        static void ear(Mesh m, int side) {
            float x = side * 2.8f, y = -7.9f, d = 0.35f;
            float[] a = {x - side * 1.9f, y, -d}, b = {x + side * 1.6f, y, -d}, c = {x + side * 0.7f, y - 4.0f, -d};
            float[] a2 = {a[0], y, d}, b2 = {b[0], y, d}, c2 = {c[0], c[1], d};
            int fur = 0xFF2B2B33;
            m.tri(a, b, c, fur); m.tri(a2, b2, c2, fur);
            m.quad(a, b, b2, a2, fur); m.quad(b, c, c2, b2, fur); m.quad(c, a, a2, c2, fur);
            m.tri(new float[]{x - side * 1.1f, y - 0.3f, -d - 0.05f}, new float[]{x + side * 1.0f, y - 0.3f, -d - 0.05f}, new float[]{x + side * 0.55f, y - 3.0f, -d - 0.05f}, 0xFFFFA8C2);
            m.octa(c[0], c[1] + 0.3f, -d - 0.1f, 0.35f, 0.5f, 0.3f, 0xFFF4F4F8);              // tuft
        }

        static void witch(Mesh m, float t) {
            m.ring(0, -8.0f, 0, 3.8f, 8.2f, 28, 0xFF2A1442);
            m.cylinder(0, 0, -8.0f, -8.5f, 8.2f, 8.35f, 28, 0xFF1E0E31, 0xFF35195A, true);
            float sway = (float) Math.sin(t * 1.4) * 0.4f;
            List<float[]> cone = List.of(new float[]{0, -8.1f, 0}, new float[]{0.2f, -11.5f, 0.4f}, new float[]{0.6f + sway, -14.8f, 1.6f},
                    new float[]{1.8f + sway, -17.2f, 3.4f}, new float[]{3.0f + sway, -18.3f, 5.0f});
            m.tube(cone, new float[]{4.0f, 3.0f, 1.8f, 0.8f, 0.1f}, 18, 0xFF3B1D5C, 0xFF5A2D8A);
            m.cylinder(0, 0, -8.1f, -9.4f, 4.02f, 3.75f, 24, 0xFFE0A93A, 0xFFF5D06A, true);    // gold band
        }
    }

    // =====================================================================================================
    // Wings (body space). Built in a flat "wing plane" (u = outwards, v = downwards) then swung back.
    // =====================================================================================================
    private static final class Wings {
        /** overall size (1 = the old, too-big wings) */
        private static final float S = 0.5f;

        static void draw(Mesh m, String type, float phase, float energy, boolean gliding) {
            // standing: folded back and barely breathing; moving: real wing beats that get bigger and faster
            // big, easy-to-see strokes: tips sweep from high above the shoulders to well below them,
            // and the wings fold back on the up-stroke and open wide on the down-stroke
            // calm, graceful strokes: a gentle sway when walking, a little stronger when sprinting
            double amp = 4 + energy * (type.equals("Butterfly") ? 24 : 18);
            double beat = gliding ? 4 : 6 + energy * 6 + Math.sin(phase) * amp;
            double swing = gliding ? 10 : 44 - energy * 12 + Math.cos(phase) * (3 + energy * 9);
            for (int side = -1; side <= 1; side += 2) {
                Frame f = new Frame(side, swing, beat);
                switch (type) {
                    case "Demon" -> bat(m, f, 0xE6741A2C, 0xD03C0A16, 0xFF2A0A12, false);
                    case "Dragon" -> bat(m, f, 0xE63E8A52, 0xD0183E26, 0xFF14281B, true);
                    case "Butterfly" -> butterfly(m, f);
                    default -> angel(m, f);
                }
            }
        }

        /** maps the flat wing plane (u = outwards, v = downwards, in pixels) onto the back */
        private record Frame(int side, double swing, double beat) {
            float[] p(float u, float v) {
                u *= S; v *= S;
                double b = Math.toRadians(beat), s = Math.toRadians(swing);
                float reach = u * (float) Math.cos(b), rise = u * (float) Math.sin(b);
                float x = side * (1.4f + reach * (float) Math.cos(s));
                float z = 2.3f + reach * (float) Math.sin(s) + 0.02f * reach * reach;
                return new float[]{x, 2.2f + v - rise, z};
            }
            float[] p(float u, float v, float layer) { float[] q = p(u, v); q[2] += layer; return q; }
        }

        // ------------------------------------------------------------------ angel: base + two rows of rounded feathers
        static void angel(Mesh m, Frame f) {
            int white = 0xFFFDFDFF, soft = 0xFFEAF0FF, tip = 0xFFD3E0FA;
            // solid base (scapulars) so there are no gaps between feathers
            float[][] base = {{0, -1}, {5, -4.5f}, {11, -6}, {16, -5}, {19, -2.5f}, {15, 2}, {9, 4}, {3, 4.5f}, {0, 3}};
            float[] c0 = f.p(6, 0, -0.15f);
            for (int i = 0; i < base.length; i++) {
                float[] a = base[i], b = base[(i + 1) % base.length];
                m.triC(c0, f.p(a[0], a[1], -0.15f), f.p(b[0], b[1], -0.15f), white, white, soft);
            }
            // secondaries along the arm, primaries fanning out at the tip (drawn back to front)
            for (int i = 7; i >= 0; i--) {
                float k = i / 7f;
                feather(m, f, 1.5f + k * 11, 2 + k * 1.5f, 7.5f + k * 2.5f, 2.6f, 92 - k * 8, 0.0f, soft, tip);
            }
            for (int i = 6; i >= 0; i--) {
                float k = i / 6f;
                feather(m, f, 13 + k * 6.5f, 0.5f - k * 2.5f, 10 + k * 5, 2.8f, 78 - k * 52, 0.1f, white, tip);
            }
            // small coverts over the joint for a soft, layered look
            for (int i = 0; i < 6; i++) feather(m, f, 3 + i * 2.7f, -2.5f - (float) Math.sin(i * 0.6) * 1.2f, 4.0f, 2.4f, 96, -0.25f, white, soft);
        }
        /** a rounded feather starting at (u,v), pointing at `deg` (0 = straight out, 90 = straight down) */
        private static void feather(Mesh m, Frame f, float u, float v, float len, float w, float deg, float layer, int c0, int c1) {
            double a = Math.toRadians(deg);
            float du = (float) Math.cos(a), dv = (float) Math.sin(a), pu = -dv, pv = du;
            // outline: base, shoulders, rounded tip
            float[][] o = {
                    {u - pu * w * 0.3f, v - pv * w * 0.3f},
                    {u + du * len * 0.35f - pu * w * 0.5f, v + dv * len * 0.35f - pv * w * 0.5f},
                    {u + du * len * 0.8f - pu * w * 0.42f, v + dv * len * 0.8f - pv * w * 0.42f},
                    {u + du * len, v + dv * len},
                    {u + du * len * 0.8f + pu * w * 0.42f, v + dv * len * 0.8f + pv * w * 0.42f},
                    {u + du * len * 0.35f + pu * w * 0.5f, v + dv * len * 0.35f + pv * w * 0.5f},
                    {u + pu * w * 0.3f, v + pv * w * 0.3f}};
            float[] center = f.p(u + du * len * 0.4f, v + dv * len * 0.4f, layer);
            int mid = dev.orbit.client.gui.Render2D.lerpColor(c0, c1, 0.45f);
            for (int i = 0; i < o.length; i++) {
                float[] a2 = o[i], b2 = o[(i + 1) % o.length];
                float fa = i >= 2 && i <= 4 ? 1 : 0.2f, fb = (i + 1) % o.length >= 2 && (i + 1) % o.length <= 4 ? 1 : 0.2f;
                m.triC(center, f.p(a2[0], a2[1], layer), f.p(b2[0], b2[1], layer), mid,
                        dev.orbit.client.gui.Render2D.lerpColor(c0, c1, fa), dev.orbit.client.gui.Render2D.lerpColor(c0, c1, fb));
            }
        }

        // ------------------------------------------------------------------ bat / dragon
        static void bat(Mesh m, Frame f, int membrane, int edge, int boneCol, boolean dragon) {
            float k = dragon ? 1.1f : 1f;
            float[] S0 = {0, 0}, E = {6 * k, -3.5f * k}, Wr = {11 * k, -7 * k};
            float[][] tips = {{22 * k, -4.5f * k}, {21 * k, 5 * k}, {15 * k, 11.5f * k}, {7 * k, 13 * k}};
            float[] B = {1, 9.5f * k};
            for (int i = 0; i < tips.length - 1; i++) { // membrane panels with a sagging edge
                float[] a = tips[i], b = tips[i + 1];
                float[] mid = {(a[0] + b[0]) / 2 * 0.8f + Wr[0] * 0.2f, (a[1] + b[1]) / 2 * 0.8f + Wr[1] * 0.2f};
                memb(m, f, Wr, a, mid, membrane, edge);
                memb(m, f, Wr, mid, b, membrane, edge);
            }
            float[] mid4 = {(tips[3][0] + B[0]) / 2 * 0.85f + E[0] * 0.15f, (tips[3][1] + B[1]) / 2 * 0.85f + E[1] * 0.15f};
            memb(m, f, Wr, tips[3], E, membrane, edge);
            memb(m, f, E, tips[3], mid4, membrane, edge);
            memb(m, f, E, mid4, B, membrane, edge);
            memb(m, f, S0, E, B, membrane, membrane);
            float r = 0.55f / S; // bones stay readable after scaling
            m.tube(List.of(f.p(S0[0], S0[1]), f.p(E[0], E[1]), f.p(Wr[0], Wr[1])), new float[]{r * 0.5f, r * 0.42f, r * 0.36f}, 6, boneCol, boneCol);
            for (float[] tp : tips) m.tube(List.of(f.p(Wr[0], Wr[1]), f.p(tp[0], tp[1])), new float[]{r * 0.32f, 0.06f}, 5, boneCol, boneCol);
            float[] claw = f.p(Wr[0], Wr[1] - 1.2f);
            m.octa(claw[0], claw[1], claw[2], 0.25f, 0.5f, 0.25f, 0xFFE8E0D0);
            if (dragon) for (int i = 1; i <= 4; i++) {
                float q = i / 5f;
                float[] p = f.p(S0[0] + (Wr[0] - S0[0]) * q, S0[1] + (Wr[1] - S0[1]) * q - 1.2f);
                m.octa(p[0], p[1], p[2] - 0.1f, 0.22f, 0.55f, 0.22f, 0xFFA8DCA2);
            }
        }
        private static void memb(Mesh m, Frame f, float[] a, float[] b, float[] c, int cIn, int cOut) {
            m.triC(f.p(a[0], a[1]), f.p(b[0], b[1]), f.p(c[0], c[1]), cIn, cOut, cOut);
        }

        // ------------------------------------------------------------------ butterfly
        static void butterfly(Mesh m, Frame f) {
            float[][] upper = {{14, -11}, {17, -8}, {17.5f, -4}, {15, -1}, {10, 0.5f}, {3, 0.5f}, {2, -4}, {6, -9}, {10, -11.5f}};
            float[][] lower = {{3, 1.5f}, {9, 2}, {13, 5}, {13, 9.5f}, {10, 13}, {6, 13.5f}, {3, 10}, {1.5f, 5}};
            lobe(m, f, upper, new float[]{4, -2}, 0xF2EBA8FF, 0xF27A3CFF, 0xFF1C1030);
            lobe(m, f, lower, new float[]{3, 3}, 0xF2FF9BD6, 0xF25CC8FF, 0xFF1C1030);
            spot(m, f, 12.5f, -6.5f, 1.6f, 0xF8FFFFFF);
            spot(m, f, 13.3f, -6.8f, 0.7f, 0xF81C1030);
            spot(m, f, 8.5f, 8.5f, 1.3f, 0xF8FFE38A);
        }
        private static void lobe(Mesh m, Frame f, float[][] outline, float[] center, int cIn, int cOut, int rim) {
            float[] cc = f.p(center[0], center[1]);
            for (int i = 0; i < outline.length; i++) {
                float[] a = outline[i], b = outline[(i + 1) % outline.length];
                m.triC(cc, f.p(a[0], a[1]), f.p(b[0], b[1]), cIn, cOut, cOut);
                float[] a2 = {center[0] + (a[0] - center[0]) * 1.08f, center[1] + (a[1] - center[1]) * 1.08f};
                float[] b2 = {center[0] + (b[0] - center[0]) * 1.08f, center[1] + (b[1] - center[1]) * 1.08f};
                m.quad(f.p(a[0], a[1]), f.p(a2[0], a2[1]), f.p(b2[0], b2[1]), f.p(b[0], b[1]), rim);
            }
        }
        private static void spot(Mesh m, Frame f, float u, float v, float r, int col) {
            float[] c = f.p(u, v, -0.05f);
            for (int i = 0; i < 10; i++) {
                double a0 = i * Math.PI * 2 / 10, a1 = (i + 1) * Math.PI * 2 / 10;
                m.tri(c, f.p(u + (float) Math.cos(a0) * r, v + (float) Math.sin(a0) * r, -0.05f), f.p(u + (float) Math.cos(a1) * r, v + (float) Math.sin(a1) * r, -0.05f), col);
            }
        }
    }

    // =====================================================================================================
    // Auras (model space, glowing)
    // =====================================================================================================
    private static final class Auras {
        static void draw(Mesh m, String type, float t) {
            switch (type) {
                case "Flames" -> flames(m, t);
                case "Hearts" -> hearts(m, t);
                case "Enchanted" -> enchanted(m, t);
                case "Snow" -> snow(m, t);
                case "Souls" -> souls(m, t);
                default -> stardust(m, t);
            }
        }

        static void stardust(Mesh m, float t) {
            float[] ys = {1, 10, 19}, rs = {10.5f, 12, 9.5f};
            for (int ring = 0; ring < 3; ring++) {
                float dir = ring % 2 == 0 ? 1 : -1;
                for (int i = 0; i < 10; i++) {
                    double a = i * Math.PI * 2 / 10 + t * 0.7 * dir + ring;
                    float bob = (float) Math.sin(t * 2 + i + ring) * 1.2f;
                    float s = 0.35f + 0.22f * (float) Math.abs(Math.sin(t * 3 + i * 1.7 + ring));
                    float x = (float) Math.cos(a) * rs[ring], z = (float) Math.sin(a) * rs[ring], y = ys[ring] + bob;
                    int c = (i + ring) % 3 == 0 ? 0xFFFFF0B0 : 0xFFFFFFFF;
                    m.octa(x, y, z, s, s * 1.4f, s, c);
                    m.octa(x, y, z, s * 2.6f, s * 2.6f, s * 2.6f, 0x24FFFFFF);
                }
            }
        }

        static void flames(Mesh m, float t) {
            for (int i = 0; i < 14; i++) {
                double a = i * Math.PI * 2 / 14 + t * 0.3;
                float r = 8.5f + (float) Math.sin(t * 2 + i) * 0.8f;
                float h = 3.5f + 3.5f * (float) Math.abs(Math.sin(t * 5.5 + i * 1.3));
                float x = (float) Math.cos(a) * r, z = (float) Math.sin(a) * r;
                m.octa(x, 23.6f - h / 2, z, 1.0f, h / 2, 1.0f, 0xB0FF5A12);
                m.octa(x, 23.8f - h / 3, z, 0.55f, h / 3, 0.55f, 0xE0FFD14A);
            }
            for (int i = 0; i < 12; i++) { // embers rising
                float p = (t * 0.45f + i / 12f) % 1f;
                double a = i * 2.4 + t * 0.5;
                float r = 6 + (i % 4) * 1.5f, y = 24 - p * 28, s = 0.25f + 0.15f * (1 - p);
                m.octa(Math.cos(a) * r, y, Math.sin(a) * r, s, s, s, withA(0xFFFF8A2A, (float) Math.sin(p * Math.PI)));
            }
        }

        static void hearts(Mesh m, float t) {
            for (int i = 0; i < 7; i++) {
                float p = (t * 0.22f + i / 7f) % 1f;
                double a = i * Math.PI * 2 / 7 + t * 0.6;
                float r = 9.5f, y = 24 - p * 32, s = 0.13f + 0.04f * (float) Math.sin(t * 4 + i);
                float x = (float) Math.cos(a) * r, z = (float) Math.sin(a) * r;
                float tx = (float) -Math.sin(a), tz = (float) Math.cos(a);
                float alpha = (float) Math.sin(p * Math.PI);
                heart(m, x, y, z, tx, tz, s, withA(i % 2 == 0 ? 0xFFFF3D6E : 0xFFFF8FB3, alpha));
            }
        }
        /** a flat 3D-ish heart in the plane facing away from the player */
        private static void heart(Mesh m, float cx, float cy, float cz, float tx, float tz, float s, int col) {
            int n = 24;
            float[] center = {cx, cy, cz};
            float[] prev = null;
            for (int i = 0; i <= n; i++) {
                double th = i * Math.PI * 2 / n;
                float hx = (float) (16 * Math.pow(Math.sin(th), 3)), hy = (float) (13 * Math.cos(th) - 5 * Math.cos(2 * th) - 2 * Math.cos(3 * th) - Math.cos(4 * th));
                float[] p = {cx + tx * hx * s, cy - hy * s, cz + tz * hx * s};
                if (prev != null) m.tri(center, prev, p, col);
                prev = p;
            }
        }

        static void enchanted(Mesh m, float t) {
            for (int i = 0; i < 18; i++) {
                float p = (t * 0.12f + i / 18f) % 1f;
                double a = p * Math.PI * 4 + i * 0.9;
                float r = 10 - p * 3, y = 24 - p * 30;
                float x = (float) Math.cos(a) * r, z = (float) Math.sin(a) * r;
                float spin = t * 2 + i, s = 0.75f;
                float c1 = (float) Math.cos(spin) * s, s1 = (float) Math.sin(spin) * s;
                float tx = (float) -Math.sin(a), tz = (float) Math.cos(a);
                int col = withA(i % 2 == 0 ? 0xFFB98CFF : 0xFF7FE0FF, (float) Math.sin(p * Math.PI));
                // a little spinning rune square
                m.quad(new float[]{x + tx * c1, y + s1, z + tz * c1}, new float[]{x - tx * s1, y + c1, z - tz * s1},
                        new float[]{x - tx * c1, y - s1, z - tz * c1}, new float[]{x + tx * s1, y - c1, z + tz * s1}, col);
            }
        }

        static void snow(Mesh m, float t) {
            for (int i = 0; i < 16; i++) {
                float p = (t * 0.1f + i / 16f) % 1f;
                double a = i * 2.3 + t * 0.35;
                float r = 7.5f + (i % 3) * 2, y = -8 + p * 32;
                float x = (float) Math.cos(a) * r + (float) Math.sin(t + i) * 0.8f, z = (float) Math.sin(a) * r;
                float tx = (float) -Math.sin(a), tz = (float) Math.cos(a);
                int col = withA(0xFFF2F8FF, (float) Math.sin(p * Math.PI));
                float rot = t * 0.8f + i;
                for (int k = 0; k < 3; k++) { // six arms = three crossing bars
                    double b = rot + k * Math.PI / 3;
                    float dx = (float) Math.cos(b) * 1.3f, dy = (float) Math.sin(b) * 1.3f;
                    m.line(new float[]{x - tx * dx, y - dy, z - tz * dx}, new float[]{x + tx * dx, y + dy, z + tz * dx}, 0.18f, col);
                }
            }
        }

        static void souls(Mesh m, float t) {
            for (int w = 0; w < 4; w++) {
                for (int k = 0; k < 8; k++) {
                    float tt = t - k * 0.07f;
                    double a = tt * 1.1 + w * Math.PI / 2;
                    float x = (float) Math.cos(a) * 11, z = (float) Math.sin(a) * 11, y = 9 + (float) Math.sin(tt * 1.7 + w) * 6;
                    float s = 0.75f * (1 - k / 9f);
                    m.octa(x, y, z, s, s, s, withA(k == 0 ? 0xFFDFFBFF : 0xFF6FF3FF, 1 - k / 8.5f));
                }
            }
        }

        private static int withA(int argb, float a) { return ((int) (Math.max(0, Math.min(1, a)) * (argb >>> 24)) << 24) | (argb & 0xFFFFFF); }
    }

    // =====================================================================================================
    // Mesh writer with per-face normals (so Minecraft's lighting shades the shapes)
    // =====================================================================================================
    static final class Mesh {
        final MatrixStack.Entry e; final VertexConsumer vc; final int light;
        Mesh(MatrixStack.Entry e, VertexConsumer vc, int light) { this.e = e; this.vc = vc; this.light = light; }

        private void v(float[] p, int c, float[] n) {
            vc.vertex(e, p[0] / 16f, p[1] / 16f, p[2] / 16f).color(c).texture(0.5f, 0.5f).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(e, n[0], n[1], n[2]);
        }
        private static float[] normal(float[] a, float[] b, float[] c) {
            float ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2], wx = c[0] - a[0], wy = c[1] - a[1], wz = c[2] - a[2];
            float nx = uy * wz - uz * wy, ny = uz * wx - ux * wz, nz = ux * wy - uy * wx;
            float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            return l < 1e-6 ? new float[]{0, -1, 0} : new float[]{nx / l, ny / l, nz / l};
        }
        /** quad with a colour per corner, drawn from both sides */
        void quadC(float[] a, float[] b, float[] c, float[] d, int ca, int cb, int cc, int cd) {
            float[] n = normal(a, b, c), r = {-n[0], -n[1], -n[2]};
            v(a, ca, n); v(b, cb, n); v(c, cc, n); v(d, cd, n);
            v(d, cd, r); v(c, cc, r); v(b, cb, r); v(a, ca, r);
        }
        void quad(float[] a, float[] b, float[] c, float[] d, int col) { quadC(a, b, c, d, col, col, col, col); }
        void triC(float[] a, float[] b, float[] c, int ca, int cb, int cc) { quadC(a, b, c, c, ca, cb, cc, cc); }
        void tri(float[] a, float[] b, float[] c, int col) { triC(a, b, c, col, col, col); }

        void octa(double x, double y, double z, float sx, float sy, float sz, int col) {
            float fx = (float) x, fy = (float) y, fz = (float) z;
            float[] top = {fx, fy - sy, fz}, bot = {fx, fy + sy, fz};
            float[][] eq = {{fx + sx, fy, fz}, {fx, fy, fz + sz}, {fx - sx, fy, fz}, {fx, fy, fz - sz}};
            for (int i = 0; i < 4; i++) { tri(top, eq[i], eq[(i + 1) % 4], col); tri(bot, eq[(i + 1) % 4], eq[i], col); }
        }
        void box(float x0, float y0, float z0, float x1, float y1, float z1, int col) {
            float[][] p = {{x0, y0, z0}, {x1, y0, z0}, {x1, y1, z0}, {x0, y1, z0}, {x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}};
            int[][] f = {{0, 1, 2, 3}, {5, 4, 7, 6}, {4, 0, 3, 7}, {1, 5, 6, 2}, {4, 5, 1, 0}, {3, 2, 6, 7}};
            for (int[] q : f) quad(p[q[0]], p[q[1]], p[q[2]], p[q[3]], col);
        }
        /** flat horizontal ring (disc when inner = 0) */
        void ring(float cx, float y, float cz, float inner, float outer, int n, int col) {
            for (int i = 0; i < n; i++) {
                double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n;
                float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
                quad(new float[]{cx + c0 * inner, y, cz + s0 * inner}, new float[]{cx + c0 * outer, y, cz + s0 * outer},
                        new float[]{cx + c1 * outer, y, cz + s1 * outer}, new float[]{cx + c1 * inner, y, cz + s1 * inner}, col);
            }
        }
        void disc(float cx, float y, float cz, float r, int n, int col) { ring(cx, y, cz, 0, r, n, col); }
        /** vertical (possibly tapered) tube wall from y0/r0 to y1/r1, coloured bottom→top */
        void cylinder(float cx, float cz, float y0, float y1, float r0, float r1, int n, int c0, int c1, boolean open) {
            for (int i = 0; i < n; i++) {
                double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n;
                float co0 = (float) Math.cos(a0), si0 = (float) Math.sin(a0), co1 = (float) Math.cos(a1), si1 = (float) Math.sin(a1);
                quadC(new float[]{cx + co0 * r0, y0, cz + si0 * r0}, new float[]{cx + co1 * r0, y0, cz + si1 * r0},
                        new float[]{cx + co1 * r1, y1, cz + si1 * r1}, new float[]{cx + co0 * r1, y1, cz + si0 * r1}, c0, c0, c1, c1);
            }
            if (!open) { disc(cx, y0, cz, r0, n, c0); disc(cx, y1, cz, r1, n, c1); }
        }
        /** horizontal torus (axis = Y) */
        void torus(float cx, float cy, float cz, float R, float r, int nu, int nv, float spin, int col) {
            for (int i = 0; i < nu; i++) for (int j = 0; j < nv; j++) {
                float[][] q = new float[4][];
                for (int k = 0; k < 4; k++) {
                    int ii = i + (k == 1 || k == 2 ? 1 : 0), jj = j + (k >= 2 ? 1 : 0);
                    double u = ii * Math.PI * 2 / nu + spin, w = jj * Math.PI * 2 / nv;
                    float rr = R + r * (float) Math.cos(w);
                    q[k] = new float[]{cx + rr * (float) Math.cos(u), cy + r * (float) Math.sin(w), cz + rr * (float) Math.sin(u)};
                }
                quad(q[0], q[1], q[2], q[3], col);
            }
        }
        /** tube swept along a path with a radius per point; colour fades start→end */
        void tube(List<float[]> path, float[] radii, int n, int c0, int c1) {
            List<float[][]> rings = new ArrayList<>();
            for (int i = 0; i < path.size(); i++) {
                float[] p = path.get(i), a = path.get(Math.max(0, i - 1)), b = path.get(Math.min(path.size() - 1, i + 1));
                float tx = b[0] - a[0], ty = b[1] - a[1], tz = b[2] - a[2];
                float tl = (float) Math.sqrt(tx * tx + ty * ty + tz * tz); tx /= tl; ty /= tl; tz /= tl;
                float ux = 0, uy = -1, uz = 0;
                if (Math.abs(ty) > 0.9f) { ux = 1; uy = 0; }
                float nx = ty * uz - tz * uy, ny = tz * ux - tx * uz, nz = tx * uy - ty * ux;
                float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz); nx /= nl; ny /= nl; nz /= nl;
                float bx = ty * nz - tz * ny, by = tz * nx - tx * nz, bz = tx * ny - ty * nx;
                float[][] ring = new float[n][];
                for (int k = 0; k < n; k++) {
                    double a2 = k * Math.PI * 2 / n;
                    float c = (float) Math.cos(a2) * radii[i], s = (float) Math.sin(a2) * radii[i];
                    ring[k] = new float[]{p[0] + nx * c + bx * s, p[1] + ny * c + by * s, p[2] + nz * c + bz * s};
                }
                rings.add(ring);
            }
            for (int i = 0; i < rings.size() - 1; i++) {
                int ca = dev.orbit.client.gui.Render2D.lerpColor(c0, c1, i / (float) (rings.size() - 1)), cb = dev.orbit.client.gui.Render2D.lerpColor(c0, c1, (i + 1) / (float) (rings.size() - 1));
                float[][] r0 = rings.get(i), r1 = rings.get(i + 1);
                for (int k = 0; k < n; k++) quadC(r0[k], r0[(k + 1) % n], r1[(k + 1) % n], r1[k], ca, ca, cb, cb);
            }
        }
        /** a thin bar between two points (for veins, vanes, snowflake arms) */
        void line(float[] a, float[] b, float w, int col) {
            float dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
            // offset perpendicular in two directions so it reads from any angle
            float[] o1 = {0, w, 0}, o2 = {dz == 0 && dx == 0 ? w : -dz, 0, dx};
            float l2 = (float) Math.sqrt(o2[0] * o2[0] + o2[2] * o2[2]);
            if (l2 > 0) { o2[0] = o2[0] / l2 * w; o2[2] = o2[2] / l2 * w; }
            quad(new float[]{a[0] - o1[0], a[1] - o1[1], a[2] - o1[2]}, new float[]{b[0] - o1[0], b[1] - o1[1], b[2] - o1[2]},
                    new float[]{b[0] + o1[0], b[1] + o1[1], b[2] + o1[2]}, new float[]{a[0] + o1[0], a[1] + o1[1], a[2] + o1[2]}, col);
            quad(new float[]{a[0] - o2[0], a[1], a[2] - o2[2]}, new float[]{b[0] - o2[0], b[1], b[2] - o2[2]},
                    new float[]{b[0] + o2[0], b[1], b[2] + o2[2]}, new float[]{a[0] + o2[0], a[1], a[2] + o2[2]}, col);
        }
    }
}
