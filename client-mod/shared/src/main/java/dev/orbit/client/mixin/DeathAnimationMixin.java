package dev.orbit.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.orbit.client.death.DeathAnimations;
import dev.orbit.client.troll.TrollState;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Death Animations: replace the "fall over" of a dying entity with Orbit's own body motion. */
@Mixin(LivingEntityRenderer.class)
public abstract class DeathAnimationMixin {
    @ModifyExpressionValue(method = "setupTransforms", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/LivingEntityRenderer;getLyingPositionRotationDegrees()F", ordinal = 0))
    private float orbit$noFall(float degrees, @com.llamalad7.mixinextras.sugar.Local(argsOnly = true) LivingEntityRenderState state) {
        return DeathAnimations.replacesFall(((TrollState) state).orbit$deathStyle()) ? 0 : degrees;
    }

    @Inject(method = "setupTransforms", at = @At("TAIL"))
    private void orbit$deathMotion(LivingEntityRenderState state, MatrixStack matrices, float bodyYaw, float baseHeight, CallbackInfo ci) {
        int style = ((TrollState) state).orbit$deathStyle();
        if (style >= 0 && state.deathTime > 0) DeathAnimations.transform(style, matrices, state.deathTime, state.height, bodyYaw, ((TrollState) state).orbit$deathPhi());
        else if (state instanceof net.minecraft.client.render.entity.state.PlayerEntityRenderState ps) dev.orbit.client.cosmetics.Props.motion(ps, matrices); // Joyride drives a loop
    }
}
