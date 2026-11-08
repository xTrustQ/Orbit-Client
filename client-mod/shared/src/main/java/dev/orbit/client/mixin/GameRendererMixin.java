package dev.orbit.client.mixin;

import dev.orbit.client.OrbitClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void orbit$zoom(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Float> cir) {
        if (!changingFov) return; // hand rendering uses the un-zoomed FOV
        float div = OrbitClient.modules().zoom().fovDivisor();
        if (div > 1.001f) cir.setReturnValue(cir.getReturnValue() / div);
    }
}
