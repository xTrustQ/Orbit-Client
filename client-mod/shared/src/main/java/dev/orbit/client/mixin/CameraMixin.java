package dev.orbit.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.orbit.client.OrbitClient;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Freelook: wherever the camera reads the player's yaw/pitch while updating, it gets the free
 * look angles instead. Targets the getter calls (not a single setRotation ordinal), so it works
 * for normal play and minecarts on every version.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @ModifyExpressionValue(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getYaw(F)F"), require = 0)
    private float orbit$freelookYaw(float yaw) {
        var f = OrbitClient.modules().freelook();
        return f.isEnabled() && f.active() ? f.yaw : yaw;
    }

    @ModifyExpressionValue(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getPitch(F)F"), require = 0)
    private float orbit$freelookPitch(float pitch) {
        var f = OrbitClient.modules().freelook();
        return f.isEnabled() && f.active() ? f.pitch : pitch;
    }
}
