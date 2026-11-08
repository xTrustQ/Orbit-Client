package dev.orbit.client.mixin;

import dev.orbit.client.OrbitClient;
import dev.orbit.client.cosmetics.CapeManager;
import dev.orbit.client.cosmetics.NameSkins;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 1. Skins by username: players without a real skin get the skin of the account with their name.
 * 2. Puts the launcher-selected Orbit cape on your own player (client-side only).
 */
@Mixin(AbstractClientPlayerEntity.class)
public abstract class AbstractClientPlayerEntityMixin {
    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void orbit$skin(CallbackInfoReturnable<SkinTextures> cir) {
        SkinTextures s = cir.getReturnValue();
        var profile = ((PlayerEntity) (Object) this).getGameProfile();
        var mod = OrbitClient.modules() == null ? null : OrbitClient.modules().get("nameskins");
        if (mod != null && mod.isEnabled() && profile != null && profile.properties().get("textures").isEmpty()) {
            SkinTextures named = NameSkins.get(profile.name());
            if (named != null) s = new SkinTextures(named.body(), s.cape() != null ? s.cape() : named.cape(), s.elytra() != null ? s.elytra() : named.elytra(), named.model(), s.secure());
        }
        if ((Object) this == MinecraftClient.getInstance().player) {
            Identifier cape = CapeManager.cape();
            if (cape != null) {
                AssetInfo.TextureAssetInfo asset = new AssetInfo.TextureAssetInfo(cape, cape);
                s = new SkinTextures(s.body(), asset, CapeManager.elytra() ? asset : s.elytra(), s.model(), s.secure());
            }
        } else if (OrbitClient.modules() != null && OrbitClient.modules().get("cosmetics") instanceof dev.orbit.client.module.NewModules.Cosmetics cos && cos.others.on()) {
            // other Orbit players: their Orbit cape, shared through the cosmetics API
            var remote = dev.orbit.client.cosmetics.CosmeticsApi.of(((PlayerEntity) (Object) this).getUuid());
            Identifier cape = remote == null ? null : dev.orbit.client.cosmetics.CosmeticsApi.cape(remote.cape());
            if (cape != null) {
                AssetInfo.TextureAssetInfo asset = new AssetInfo.TextureAssetInfo(cape, cape);
                s = new SkinTextures(s.body(), asset, asset, s.model(), s.secure());
            }
        }
        cir.setReturnValue(s);
    }
}
