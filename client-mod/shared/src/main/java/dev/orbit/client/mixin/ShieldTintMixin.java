package dev.orbit.client.mixin;

import dev.orbit.client.module.NewModules;
import net.minecraft.client.render.item.model.special.ShieldModelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Shield Status (like the "Shield Statuses" mod): your shield's handle, rim and back are tinted green while it
 * works and red while an axe has disabled it, fading back to green as the cooldown runs out.
 */
@Mixin(ShieldModelRenderer.class)
public abstract class ShieldTintMixin {
    @ModifyArg(method = "render", index = 8, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;submitModelPart(Lnet/minecraft/client/model/ModelPart;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/RenderLayer;IILnet/minecraft/client/texture/Sprite;ZZILnet/minecraft/client/render/command/ModelCommandRenderer$CrumblingOverlayCommand;I)V"))
    private int orbit$tint(int tint) {
        int c = NewModules.ShieldStatus.tint();
        return c == 0 ? tint : c;
    }
}
