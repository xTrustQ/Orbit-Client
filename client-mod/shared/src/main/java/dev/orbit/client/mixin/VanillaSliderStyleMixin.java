package dev.orbit.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.orbit.client.OrbitClient;
import dev.orbit.client.gui.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Minecraft's sliders with Orbit's look: a rounded track that fills with your accent colour and a round handle. */
@Mixin(SliderWidget.class)
public abstract class VanillaSliderStyleMixin {
    @Shadow protected double value;

    private static boolean orbit$styled() {
        try { return OrbitClient.modules() != null && OrbitClient.modules().ui().spaceMenu.on(); } catch (Throwable t) { return false; }
    }

    @WrapOperation(method = "renderWidget", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIIII)V"))
    private void orbit$track(DrawContext c, RenderPipeline p, Identifier tex, int x, int y, int w, int h, int col, Operation<Void> original) {
        if (!orbit$styled()) { original.call(c, p, tex, x, y, w, h, col); return; }
        ClickableWidget self = (ClickableWidget) (Object) this;
        boolean sel = self.isSelected();
        Render2D.round(c, x, y, w, h, Math.min(10, h / 2), sel ? 0x44FFFFFF : 0x30FFFFFF);
        float fill = (float) (value * (w - 8)) + 4;
        Render2D.round(c, x, y, Math.max(h, fill + 4), h, Math.min(10, h / 2), Render2D.withAlpha(Render2D.accent(), sel ? 170 : 120));
    }

    @WrapOperation(method = "renderWidget", at = @At(value = "INVOKE", ordinal = 1,
            target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIIII)V"))
    private void orbit$handle(DrawContext c, RenderPipeline p, Identifier tex, int x, int y, int w, int h, int col, Operation<Void> original) {
        if (!orbit$styled()) { original.call(c, p, tex, x, y, w, h, col); return; }
        Render2D.round(c, x + 1, y + 2, w - 2, h - 4, 3, 0xF2FFFFFF);
    }
}
