package dev.orbit.client.mixin;

import dev.orbit.client.OrbitClient;
import dev.orbit.client.sky.SkyRenderer;
import net.minecraft.client.render.SkyRendering;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Swaps the overworld sky dome for the Orbit cube map (require = 0 so other sky mods never crash us). */
@Mixin(SkyRendering.class)
public abstract class SkyRenderingMixin {
    @Inject(method = "renderSky", at = @At("HEAD"), cancellable = true, require = 0)
    private void orbit$sky(float red, float green, float blue, CallbackInfo ci) {
        if (SkyRenderer.active()) { SkyRenderer.render(); ci.cancel(); }
    }

    @Inject(method = "renderGlowingSky", at = @At("HEAD"), cancellable = true, require = 0)
    private void orbit$sunsetGlow(CallbackInfo ci) {
        if (SkyRenderer.active()) ci.cancel();
    }

    @Inject(method = "renderCelestialBodies", at = @At("HEAD"), cancellable = true, require = 0)
    private void orbit$celestial(CallbackInfo ci) {
        if (SkyRenderer.active() && !OrbitClient.modules().sky().celestial.on()) ci.cancel();
    }

    @Inject(method = "renderSkyDark", at = @At("HEAD"), cancellable = true, require = 0)
    private void orbit$dark(CallbackInfo ci) {
        if (SkyRenderer.active()) ci.cancel();
    }
}
