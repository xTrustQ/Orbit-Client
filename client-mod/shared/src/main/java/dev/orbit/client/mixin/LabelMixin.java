package dev.orbit.client.mixin;

import dev.orbit.client.OrbitClient;
import dev.orbit.client.module.NewModules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Own Nametag (1.21.2+ signature): show your own name in third person. */
@Mixin(LivingEntityRenderer.class)
public abstract class LabelMixin {
    @Inject(method = "hasLabel(Lnet/minecraft/entity/LivingEntity;D)Z", at = @At("RETURN"), cancellable = true, require = 0)
    private void orbit$ownLabel(LivingEntity entity, double distance, CallbackInfoReturnable<Boolean> cir) {
        if (entity == MinecraftClient.getInstance().player && ((NewModules.OwnNametag) OrbitClient.modules().get("nametag")).show()) cir.setReturnValue(true);
    }
}
