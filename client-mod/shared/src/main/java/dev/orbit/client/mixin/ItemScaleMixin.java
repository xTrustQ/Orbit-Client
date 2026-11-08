package dev.orbit.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.orbit.client.itemscale.ItemScaleItems;
import dev.orbit.client.itemscale.ScaledState;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Item Scale: dropped items bigger/smaller (per item), optionally without spinning or bobbing. */
@Mixin(ItemEntityRenderer.class)
public abstract class ItemScaleMixin {
    @Inject(method = "updateRenderState(Lnet/minecraft/entity/ItemEntity;Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;F)V", at = @At("TAIL"))
    private void orbit$pickScale(ItemEntity entity, ItemEntityRenderState state, float tickDelta, CallbackInfo ci) {
        ((ScaledState) state).orbit$setScale(ItemScaleItems.factor(entity.getStack(), ItemScaleItems.Where.DROPPED));
        ((ScaledState) state).orbit$setOnGround(entity.isOnGround());
    }

    @WrapMethod(method = "render(Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V")
    private void orbit$scale(ItemEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState camera, Operation<Void> original) {
        float k = ((ScaledState) state).orbit$scale();
        if (k == 1f || k <= 0) { original.call(state, matrices, queue, camera); return; }
        matrices.push();
        matrices.scale(k, k, k);
        original.call(state, matrices, queue, camera);
        matrices.pop();
    }

    /** no spinning: every item keeps its own fixed angle */
    @ModifyExpressionValue(method = "render(Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/ItemEntity;getRotation(FF)F"))
    private float orbit$spin(float rotation, @Local(argsOnly = true) ItemEntityRenderState state) {
        if (orbit$physics() != null) return ((ScaledState) state).orbit$onGround() ? state.uniqueOffset * 3.7f : rotation * 1.8f; // lies still / spins while flying
        return ItemScaleItems.spin() ? rotation : state.uniqueOffset;
    }

    /** no bobbing: items lie on the ground */
    @ModifyExpressionValue(method = "render(Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/MathHelper;sin(D)F"))
    private float orbit$bob(float sin) {
        return ItemScaleItems.bob() && orbit$physics() == null ? sin : -1f;
    }

    private static dev.orbit.client.module.NewModules.ItemPhysics orbit$physics() {
        return dev.orbit.client.OrbitClient.modules() != null && dev.orbit.client.OrbitClient.modules().get("itemphysics") instanceof dev.orbit.client.module.NewModules.ItemPhysics p && p.isEnabled() ? p : null;
    }

    /** Item Physics: flat items (not blocks) lie down on the ground; in the air they tumble */
    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method = "render(Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/ItemEntityRenderer;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/client/render/entity/state/ItemStackEntityRenderState;Lnet/minecraft/util/math/random/Random;Lnet/minecraft/util/math/Box;)V"))
    private void orbit$layFlat(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, net.minecraft.client.render.entity.state.ItemStackEntityRenderState s,
                               net.minecraft.util.math.random.Random random, net.minecraft.util.math.Box box, com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
        var phys = orbit$physics();
        if (phys == null) { original.call(matrices, queue, light, s, random, box); return; }
        boolean ground = s instanceof ScaledState ss && ss.orbit$onGround();
        boolean flat = box.getLengthZ() <= 0.0625 + 1e-3;
        matrices.push();
        float lift = (float) (-box.minY + 0.0625);
        if (ground && flat) {
            matrices.translate(0, -lift + (float) box.getLengthZ() * 0.5f + 0.01f, 0);
            matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(90));
        } else if (ground) {
            matrices.translate(0, -0.0625f, 0); // blocks sit on the ground instead of hovering
        } else if (phys.tumble.on()) {
            float h = (float) (box.minY + box.maxY) / 2;
            matrices.translate(0, h, 0);
            matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(s.age * 22));
            matrices.translate(0, -h, 0);
        }
        original.call(matrices, queue, light, s, random, box);
        matrices.pop();
    }
}
