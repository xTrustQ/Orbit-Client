package dev.orbit.client.mixin;

import dev.orbit.client.OrbitClient;
import dev.orbit.client.sky.SkyRenderer;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.world.ClientWorld;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Tints distance fog toward the custom sky's horizon so terrain blends into it. */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
    @Inject(method = "getFogColor", at = @At("RETURN"), require = 0)
    private void orbit$tint(Camera camera, float tickProgress, ClientWorld world, int viewDistance, float skyDarkness, CallbackInfoReturnable<Vector4f> cir) {
        if (!SkyRenderer.active() || !OrbitClient.modules().sky().tintFog.on()) return;
        if (camera.getSubmersionType() != CameraSubmersionType.NONE) return;
        Vector4f v = cir.getReturnValue();
        float[] t = SkyRenderer.fogTint();
        float lum = Math.min(1, Math.max(0.15f, (v.x + v.y + v.z) / 3f / 0.6f));
        float k = 0.7f;
        v.set(v.x + (t[0] * lum - v.x) * k, v.y + (t[1] * lum - v.y) * k, v.z + (t[2] * lum - v.z) * k, v.w);
    }
}
