package dev.orbit.client.mixin;

import dev.orbit.client.itemscale.ItemScaleItems;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.state.ArmedEntityRenderState;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Item Scale: items in hands seen in third person (yours and/or other players' and mobs'). */
@Mixin(HeldItemFeatureRenderer.class)
public abstract class HeldItemFeatureScaleMixin {
    /** Shield Status: only your own shield is tinted (the game only knows your cooldown) */
    @Inject(method = "renderItem", at = @At("HEAD"))
    private void orbit$whoseShield(ArmedEntityRenderState state, ItemRenderState itemState, ItemStack stack, Arm arm, MatrixStack matrices, OrderedRenderCommandQueue queue, int light, CallbackInfo ci) {
        var mc = MinecraftClient.getInstance();
        dev.orbit.client.module.NewModules.ShieldStatus.holder = state instanceof net.minecraft.client.render.entity.state.PlayerEntityRenderState p ? p.id : Integer.MAX_VALUE;
    }
    @Inject(method = "renderItem", at = @At("RETURN"))
    private void orbit$whoseShieldEnd(ArmedEntityRenderState state, ItemRenderState itemState, ItemStack stack, Arm arm, MatrixStack matrices, OrderedRenderCommandQueue queue, int light, CallbackInfo ci) {
        dev.orbit.client.module.NewModules.ShieldStatus.holder = -1;
    }

    @Inject(method = "renderItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/item/ItemRenderState;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;III)V"))
    private void orbit$scaleThird(ArmedEntityRenderState state, ItemRenderState itemState, ItemStack stack, Arm arm, MatrixStack matrices, OrderedRenderCommandQueue queue, int light, CallbackInfo ci) {
        var mc = MinecraftClient.getInstance();
        boolean mine = mc.player != null && state instanceof net.minecraft.client.render.entity.state.PlayerEntityRenderState p && p.id == mc.player.getId();
        if (!ItemScaleItems.thirdFor(mine)) return;
        float k = ItemScaleItems.factor(stack, ItemScaleItems.Where.THIRD_PERSON);
        if (k != 1f) matrices.scale(k, k, k); // vanilla pops the matrix right after drawing
    }
}
