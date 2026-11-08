package dev.orbit.client.sky;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.orbit.client.OrbitClient;
import dev.orbit.client.module.FeatureModules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.concurrent.CompletableFuture;

/** Bakes a {@link SkyPreset} into a 6-face cube map and draws it in place of the vanilla sky. */
public final class SkyRenderer {
    private static final int RES = 256;
    private static final Identifier[] FACES = new Identifier[6];
    private static SkyPreset baked, baking;
    private static boolean ready;

    static { for (int i = 0; i < 6; i++) FACES[i] = dev.orbit.client.compat.Compat.id("sky/face" + i); }

    // face basis: centre, u-axis, v-axis (v grows downward in texture space)
    private static final double[][][] BASIS = {
            {{1, 0, 0}, {0, 0, -1}, {0, -1, 0}},   // +X
            {{-1, 0, 0}, {0, 0, 1}, {0, -1, 0}},   // -X
            {{0, 1, 0}, {1, 0, 0}, {0, 0, 1}},     // +Y (up)
            {{0, -1, 0}, {1, 0, 0}, {0, 0, -1}},   // -Y
            {{0, 0, 1}, {1, 0, 0}, {0, -1, 0}},    // +Z
            {{0, 0, -1}, {-1, 0, 0}, {0, -1, 0}},  // -Z
    };

    private static double[] dir(int face, double u, double v) {
        double[][] b = BASIS[face];
        double a = 2 * u - 1, c = 2 * v - 1;
        return new double[]{b[0][0] + a * b[1][0] + c * b[2][0], b[0][1] + a * b[1][1] + c * b[2][1], b[0][2] + a * b[1][2] + c * b[2][2]};
    }

    private static FeatureModules.SkyChanger mod() { return OrbitClient.modules().sky(); }

    /** True when our sky should replace vanilla this frame. */
    public static boolean active() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mod().isEnabled() && ready && baked == mod().current() && mc.world != null;
    }

    /** Called each tick: (re)bakes textures off-thread whenever the preset changes. */
    public static void ensure(SkyPreset preset) {
        if (preset == baked || preset == baking) return;
        baking = preset;
        CompletableFuture.supplyAsync(() -> {
            int[][] px = new int[6][RES * RES];
            for (int f = 0; f < 6; f++)
                for (int y = 0; y < RES; y++)
                    for (int x = 0; x < RES; x++) {
                        double[] d = dir(f, (x + 0.5) / RES, (y + 0.5) / RES);
                        double l = Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
                        px[f][y * RES + x] = preset.color(d[0] / l, d[1] / l, d[2] / l);
                    }
            return px;
        }).thenAccept(px -> MinecraftClient.getInstance().execute(() -> {
            if (baking != preset) return;
            var tm = MinecraftClient.getInstance().getTextureManager();
            for (int f = 0; f < 6; f++) {
                NativeImage img = new NativeImage(RES, RES, false);
                for (int i = 0; i < px[f].length; i++) img.setColorArgb(i % RES, i / RES, px[f][i]);
                NativeImageBackedTexture tex = new NativeImageBackedTexture(img);
                tex.setFilter(true, false);
                tex.setClamp(true); // no bleeding across cube edges
                tm.registerTexture(FACES[f], tex); // replaces + closes the previous texture
            }
            baked = preset;
            baking = null;
            ready = true;
            OrbitClient.LOG.info("Sky '{}' ready", preset.title);
        })).exceptionally(t -> { OrbitClient.LOG.error("Sky bake failed", t); baking = null; return null; });
    }

    /** Draws the cube map. Model-view already holds the camera rotation when vanilla's sky pass runs. */
    public static void render() {
        MinecraftClient mc = MinecraftClient.getInstance();
        float b = mod().brightness.f() / 100f;
        if (mod().dayCycle.on() && mc.world != null) {
            float angle = mc.world.getSkyAngle(mc.getRenderTickCounter().getTickDelta(false));
            float day = MathHelper.clamp(MathHelper.cos(angle * MathHelper.TAU) * 2f + 0.5f, 0f, 1f);
            b *= 0.28f + 0.72f * day;
        }
        float rain = mc.world != null ? 1f - mc.world.getRainGradient(1f) * 0.45f : 1f;
        b *= rain;

        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderColor(b, b, b, 1f);
        float s = 100f;
        for (int f = 0; f < 6; f++) {
            RenderSystem.setShaderTexture(0, FACES[f]);
            BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            double[][] uv = {{0, 0}, {0, 1}, {1, 1}, {1, 0}};
            for (double[] c : uv) {
                double[] d = dir(f, c[0], c[1]);
                bb.vertex((float) (d[0] * s), (float) (d[1] * s), (float) (d[2] * s)).texture((float) c[0], (float) c[1]).color(0xFFFFFFFF);
            }
            BufferRenderer.drawWithGlobalProgram(bb.end());
        }
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
    }

    /** Fog tint for the horizon, already scaled by the day-cycle brightness. */
    public static float[] fogTint() {
        int c = mod().current().horizonColor();
        return new float[]{(c >> 16 & 255) / 255f, (c >> 8 & 255) / 255f, (c & 255) / 255f};
    }
}
