package dev.orbit.client.mixin;

import dev.orbit.client.emote.Emote;
import dev.orbit.client.emote.Emotes;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Emotes (1.21.2+ render states): pose the player model after vanilla animation. Sleeves/hat are child parts here. */
@Mixin(PlayerEntityModel.class)
public abstract class PlayerModelMixin {
    @Inject(method = "setAngles(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;)V", at = @At("TAIL"), require = 0)
    private void orbit$emote(PlayerEntityRenderState state, CallbackInfo ci) {
        BipedEntityModel<?> m = (BipedEntityModel<?>) (Object) this;
        var parts = new Emote.Parts(m.head, m.body, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg);
        if (state.id <= dev.orbit.client.cosmetics.CosmeticsFeature.PREVIEW_ID) { Emotes.posePreview(state.id, parts); return; } // emote wheel / locker previews
        var world = MinecraftClient.getInstance().world;
        if (world == null) return;
        Emotes.pose(world.getEntityById(state.id), parts);
    }
}
