package dev.orbit.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import dev.orbit.client.OrbitClient;
import dev.orbit.client.compat.Compat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * Motion blur for 1.21.2 – 1.21.4 (OpenGL). Before the HUD draws: lay the last frame over the world
 * at `strength` opacity, then copy the result as the next "last frame" (so trails accumulate).
 */
public final class MotionBlurRenderer {
    private static final Identifier ID = Compat.id("motion_blur");
    private static NativeImageBackedTexture texture;
    private static int fbo = -1, w, h;
    private static boolean failed;

    private MotionBlurRenderer() {}

    public static void apply(DrawContext c, float strength) {
        if (failed) return;
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            Framebuffer main = mc.getFramebuffer();
            int fw = main.textureWidth, fh = main.textureHeight;
            if (texture == null || fw != w || fh != h) { create(fw, fh); copy(main); return; }
            int sw = c.getScaledWindowWidth(), sh = c.getScaledWindowHeight();
            c.drawTexture(RenderLayer::getGuiTextured, ID, 0, 0, 0f, 0f, sw, sh, sw, sh, ((int) (strength * 255) << 24) | 0xFFFFFF);
            c.draw();
            copy(main);
        } catch (Throwable t) {
            failed = true;
            OrbitClient.LOG.warn("Motion blur disabled: {}", t.toString());
        }
    }

    private static void create(int fw, int fh) {
        if (fbo != -1) GL30.glDeleteFramebuffers(fbo);
        w = fw; h = fh;
        texture = new NativeImageBackedTexture(fw, fh, false);
        MinecraftClient.getInstance().getTextureManager().registerTexture(ID, texture);
        fbo = GL30.glGenFramebuffers();
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture.getGlId(), 0);
    }

    /** Main framebuffer → our texture, flipped so GUI sampling (top-down) shows it upright. */
    private static void copy(Framebuffer main) {
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.fbo);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, fbo);
        GL30.glBlitFramebuffer(0, 0, w, h, 0, h, w, 0, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        main.beginWrite(false);
    }
}
