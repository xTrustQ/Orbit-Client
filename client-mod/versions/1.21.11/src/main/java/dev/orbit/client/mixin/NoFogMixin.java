package dev.orbit.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.orbit.client.OrbitClient;
import dev.orbit.client.module.NewModules;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.nio.ByteBuffer;

/**
 * No Fog (1.21.6+): every frame's fog distances (environment fog — the thick Nether/End/biome fog —,
 * render-distance fog, sky and cloud fog) are pushed far past anything you can see.
 */
@Mixin(FogRenderer.class)
public abstract class NoFogMixin {
    private static boolean orbit$active() {
        if (OrbitClient.modules() == null) return false;
        var m = (NewModules.NoFog) OrbitClient.modules().get("nofog");
        if (m == null || !m.isEnabled()) return false;
        var mc = MinecraftClient.getInstance();
        if (mc == null || mc.gameRenderer == null || mc.world == null) return false;
        var cam = mc.gameRenderer.getCamera();
        if (cam == null) return true;
        var type = cam.getSubmersionType();
        return type == CameraSubmersionType.NONE || type == CameraSubmersionType.ATMOSPHERIC || m.liquids.on();
    }

    @WrapOperation(method = "applyFog(Lnet/minecraft/client/render/Camera;ILnet/minecraft/client/render/RenderTickCounter;FLnet/minecraft/client/world/ClientWorld;)Lorg/joml/Vector4f;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/fog/FogRenderer;applyFog(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V"))
    private void orbit$noFog(FogRenderer self, ByteBuffer buffer, int pos, Vector4f color, float envStart, float envEnd,
                             float distStart, float distEnd, float skyEnd, float cloudEnd, Operation<Void> original) {
        if (orbit$active()) {
            float far = 1_000_000f;
            envStart = far; envEnd = far + 1; distStart = far; distEnd = far + 1;
            skyEnd = Math.max(skyEnd, far); cloudEnd = Math.max(cloudEnd, far);
        }
        original.call(self, buffer, pos, color, envStart, envEnd, distStart, distEnd, skyEnd, cloudEnd);
    }
}
