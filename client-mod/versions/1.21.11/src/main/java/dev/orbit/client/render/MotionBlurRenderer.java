package dev.orbit.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.orbit.client.OrbitClient;
import dev.orbit.client.compat.Compat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

/**
 * Motion blur for 1.21.6+ (GPU device API, deferred GUI): copy the world frame on the GPU,
 * then draw it over the next frame at `strength` opacity.
 */
public final class MotionBlurRenderer {
    private static final Identifier ID = Compat.id("motion_blur");
    private static NativeImageBackedTexture texture;
    private static int w, h;
    private static boolean failed;

    private MotionBlurRenderer() {}

    public static void apply(DrawContext c, float strength) {
        if (failed) return;
        try {
            Framebuffer main = MinecraftClient.getInstance().getFramebuffer();
            int fw = main.textureWidth, fh = main.textureHeight;
            if (texture == null || fw != w || fh != h) {
                w = fw; h = fh;
                texture = new NativeImageBackedTexture("orbit-motion-blur", fw, fh, false);
                MinecraftClient.getInstance().getTextureManager().registerTexture(ID, texture);
            } else {
                int sw = c.getScaledWindowWidth(), sh = c.getScaledWindowHeight();
                // framebuffer rows are bottom-up: flip vertically while drawing
                c.getMatrices().pushMatrix();
                c.getMatrices().translate(0, sh);
                c.getMatrices().scale(1f, -1f);
                c.drawTexture(RenderPipelines.GUI_TEXTURED, ID, 0, 0, 0f, 0f, sw, sh, sw, sh, ((int) (strength * 255) << 24) | 0xFFFFFF);
                c.getMatrices().popMatrix();
            }
            RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(main.getColorAttachment(), texture.getGlTexture(), 0, 0, 0, 0, 0, fw, fh);
        } catch (Throwable t) {
            failed = true;
            OrbitClient.LOG.warn("Motion blur disabled: {}", t.toString());
        }
    }
}
