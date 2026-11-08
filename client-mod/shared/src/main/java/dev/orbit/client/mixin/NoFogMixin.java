package dev.orbit.client.mixin;

import dev.orbit.client.OrbitClient;
import dev.orbit.client.module.NewModules;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Fog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** No Fog for 1.21.2 – 1.21.4: push the fog start/end past the render distance. */
@Mixin(BackgroundRenderer.class)
public abstract class NoFogMixin {
    @Inject(method = "applyFog", at = @At("RETURN"), cancellable = true, require = 0)
    private static void orbit$noFog(Camera camera, BackgroundRenderer.FogType type, org.joml.Vector4f color, float viewDistance, boolean thickFog, float tickDelta, CallbackInfoReturnable<Fog> cir) {
        var m = (NewModules.NoFog) OrbitClient.modules().get("nofog");
        if (!m.isEnabled()) return;
        if (camera.getSubmersionType() != CameraSubmersionType.NONE && !m.liquids.on()) return;
        Fog f = cir.getReturnValue();
        cir.setReturnValue(new Fog(1_000_000f, 1_000_001f, f.shape(), f.red(), f.green(), f.blue(), f.alpha()));
    }
}
