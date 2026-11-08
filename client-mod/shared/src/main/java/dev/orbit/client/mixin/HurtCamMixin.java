package dev.orbit.client.mixin;

import dev.orbit.client.OrbitClient;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No Hurt Cam: skip the screen tilt when damaged. */
@Mixin(GameRenderer.class)
public abstract class HurtCamMixin {
    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true, require = 0)
    private void orbit$noHurtCam(CallbackInfo ci) {
        if (OrbitClient.modules().get("hurtcam").isEnabled()) ci.cancel();
    }
}
