package dev.orbit.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Fight Statistics: a swing that hits nothing counts as a miss. */
@Mixin(MinecraftClient.class)
public abstract class AttackMissMixin {
    @Inject(method = "doAttack", at = @At("HEAD"))
    private void orbit$miss(CallbackInfoReturnable<Boolean> cir) {
        MinecraftClient mc = (MinecraftClient) (Object) this;
        if (mc.player != null && mc.currentScreen == null && (mc.crosshairTarget == null || mc.crosshairTarget.getType() == HitResult.Type.MISS)) dev.orbit.client.pvp.Combat.onMiss();
    }
}
