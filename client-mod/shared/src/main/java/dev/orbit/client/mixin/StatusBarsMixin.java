package dev.orbit.client.mixin;

import dev.orbit.client.OrbitClient;
import dev.orbit.client.compat.Compat;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Saturation mod: the exhaustion bar goes under the vanilla hunger icons, the saturation outline
 * and food preview go right on top of them. Flushing in between keeps that order (Minecraft
 * otherwise batches plain fills and icon textures separately).
 */
@Mixin(InGameHud.class)
public abstract class StatusBarsMixin {
    @Inject(method = "renderStatusBars", at = @At("HEAD"), require = 0)
    private void orbit$underHunger(DrawContext c, CallbackInfo ci) {
        var s = OrbitClient.modules().saturation();
        if (s.renderUnderlay(c, c.getScaledWindowWidth(), c.getScaledWindowHeight())) Compat.flush(c);
    }

    @Inject(method = "renderStatusBars", at = @At("TAIL"), require = 0)
    private void orbit$overHunger(DrawContext c, CallbackInfo ci) {
        Compat.flush(c);
        OrbitClient.modules().saturation().renderOverlay(c, c.getScaledWindowWidth(), c.getScaledWindowHeight());
    }
}
