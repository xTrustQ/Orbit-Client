package dev.orbit.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.orbit.client.itemscale.ItemScaleItems;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

/** Item Scale: the item in your hand in first person. */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemScaleMixin {
    @WrapMethod(method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V")
    private void orbit$scaleHeld(LivingEntity entity, ItemStack stack, ItemDisplayContext mode, MatrixStack matrices, OrderedRenderCommandQueue queue, int light, Operation<Void> original) {
        float k = mode.isFirstPerson() && entity == MinecraftClient.getInstance().player ? ItemScaleItems.factor(stack, ItemScaleItems.Where.FIRST_PERSON) : 1f;
        if (k == 1f) { original.call(entity, stack, mode, matrices, queue, light); return; }
        matrices.push();
        matrices.scale(k, k, k);
        original.call(entity, stack, mode, matrices, queue, light);
        matrices.pop();
    }
}
