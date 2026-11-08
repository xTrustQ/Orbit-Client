package dev.orbit.client.mixin;

import dev.orbit.client.OrbitClient;
import dev.orbit.client.module.MotionBlur;
import dev.orbit.client.render.MotionBlurRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Runs motion blur right before the HUD is drawn, so the HUD itself never smears. */
@Mixin(InGameHud.class)
public abstract class MotionBlurHudMixin {
    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void orbit$motionBlur(DrawContext c, RenderTickCounter tick, CallbackInfo ci) {
        var m = (MotionBlur) OrbitClient.modules().get("motionblur");
        if (m.isEnabled() && net.minecraft.client.MinecraftClient.getInstance().world != null) MotionBlurRenderer.apply(c, m.amount());
    }
}
