package dev.orbit.client.mixin;

import dev.orbit.client.OrbitClient;
import dev.orbit.client.module.NewModules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hide Armor: skips drawing armor on yourself and/or other players (client-side only). */
@Mixin(ArmorFeatureRenderer.class)
public abstract class HideArmorMixin {
    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/client/render/entity/state/BipedEntityRenderState;FF)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void orbit$hide(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, BipedEntityRenderState state, float limbAngle, float yaw, CallbackInfo ci) {
        if (!(state instanceof PlayerEntityRenderState p) || OrbitClient.modules() == null) return;
        if (!(OrbitClient.modules().get("hidearmor") instanceof NewModules.HideArmor m) || !m.isEnabled()) return;
        var me = MinecraftClient.getInstance().player;
        boolean self = me != null && p.id == me.getId();
        if (self ? m.self.on() : m.others.on()) ci.cancel();
    }
}
