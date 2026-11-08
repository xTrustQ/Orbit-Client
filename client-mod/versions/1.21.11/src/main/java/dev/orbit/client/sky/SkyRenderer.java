package dev.orbit.client.sky;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.orbit.client.OrbitClient;
import dev.orbit.client.compat.Compat;
import dev.orbit.client.module.FeatureModules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;

/** 1.21.5+ version: bakes a {@link SkyPreset} into 6 textures and draws them with the GPU render-pass API. */
public final class SkyRenderer {
    private static final int RES = 256;
    private static final Identifier[] FACES = new Identifier[6];
    private static SkyPreset baked, baking;
    private static boolean ready;
    private static GpuBuffer cube;

    static { for (int i = 0; i < 6; i++) FACES[i] = Compat.id("sky/face" + i); }

    private static final double[][][] BASIS = {
            {{1, 0, 0}, {0, 0, -1}, {0, -1, 0}},
            {{-1, 0, 0}, {0, 0, 1}, {0, -1, 0}},
            {{0, 1, 0}, {1, 0, 0}, {0, 0, 1}},
            {{0, -1, 0}, {1, 0, 0}, {0, 0, -1}},
            {{0, 0, 1}, {1, 0, 0}, {0, -1, 0}},
            {{0, 0, -1}, {-1, 0, 0}, {0, -1, 0}},
    };

    private static double[] dir(int face, double u, double v) {
        double[][] b = BASIS[face];
        double a = 2 * u - 1, c = 2 * v - 1;
        return new double[]{b[0][0] + a * b[1][0] + c * b[2][0], b[0][1] + a * b[1][1] + c * b[2][1], b[0][2] + a * b[1][2] + c * b[2][2]};
    }

    private static FeatureModules.SkyChanger mod() { return OrbitClient.modules().sky(); }

    public static boolean active() {
        return mod().isEnabled() && ready && baked == mod().current() && MinecraftClient.getInstance().world != null;
    }

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
                tm.registerTexture(FACES[f], Compat.texture("orbit-sky-" + f, img, true));
            }
            baked = preset;
            baking = null;
            ready = true;
            OrbitClient.LOG.info("Sky '{}' ready", preset.title);
        })).exceptionally(t -> { OrbitClient.LOG.error("Sky bake failed", t); baking = null; return null; });
    }

    /** Each face is emitted with both windings so back-face culling can never hide it. */
    private static GpuBuffer cube() {
        if (cube != null) return cube;
        float s = 100f;
        try (BufferAllocator alloc = BufferAllocator.fixedSized(48 * VertexFormats.POSITION_TEXTURE_COLOR.getVertexSize())) {
            BufferBuilder bb = new BufferBuilder(alloc, VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            double[][] uv = {{0, 0}, {0, 1}, {1, 1}, {1, 0}};
            for (int f = 0; f < 6; f++) {
                for (int w = 0; w < 2; w++) {
                    for (int k = 0; k < 4; k++) {
                        double[] c = uv[w == 0 ? k : 3 - k];
                        double[] d = dir(f, c[0], c[1]);
                        bb.vertex((float) (d[0] * s), (float) (d[1] * s), (float) (d[2] * s)).texture((float) c[0], (float) c[1]).color(0xFFFFFFFF);
                    }
                }
            }
            try (BuiltBuffer built = bb.end()) {
                cube = RenderSystem.getDevice().createBuffer(() -> "Orbit sky cube", GpuBuffer.USAGE_VERTEX, built.getBuffer());
            }
        }
        return cube;
    }

    private static float dayFactor() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return 1;
        double d = MathHelper.fractionalPart(mc.world.getTimeOfDay() / 24000.0 - 0.25);
        double e = 0.5 - Math.cos(d * Math.PI) / 2.0;
        float angle = (float) (d * 2.0 + e) / 3.0f;
        return MathHelper.clamp(MathHelper.cos(angle * MathHelper.TAU) * 2f + 0.5f, 0f, 1f);
    }

    public static void render() {
        MinecraftClient mc = MinecraftClient.getInstance();
        float b = mod().brightness.f() / 100f;
        if (mod().dayCycle.on()) b *= 0.28f + 0.72f * dayFactor();
        if (mc.world != null) b *= 1f - mc.world.getRainGradient(1f) * 0.45f;

        var tm = mc.getTextureManager();
        RenderSystem.ShapeIndexBuffer quads = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS);
        GpuBuffer indices = quads.getIndexBuffer(72);
        GpuTextureView color = mc.getFramebuffer().getColorAttachmentView();
        GpuTextureView depth = mc.getFramebuffer().getDepthAttachmentView();
        GpuBufferSlice uniforms = RenderSystem.getDynamicUniforms()
                .write(RenderSystem.getModelViewMatrix(), new Vector4f(b, b, b, 1f), new Vector3f(), new Matrix4f());

        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass(() -> "Orbit sky", color, OptionalInt.empty(), depth, OptionalDouble.empty())) {
            pass.setPipeline(RenderPipelines.POSITION_TEX_COLOR_END_SKY);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", uniforms);
            pass.setVertexBuffer(0, cube());
            pass.setIndexBuffer(indices, quads.getIndexType());
            for (int f = 0; f < 6; f++) {
                AbstractTexture t = tm.getTexture(FACES[f]);
                pass.bindTexture("Sampler0", t.getGlTextureView(), t.getSampler());
                pass.drawIndexed(0, f * 12, 12, 1);
            }
        }
    }

    public static float[] fogTint() {
        int c = mod().current().horizonColor();
        return new float[]{(c >> 16 & 255) / 255f, (c >> 8 & 255) / 255f, (c & 255) / 255f};
    }
}
