package dev.orbit.client.mixin;

import dev.orbit.client.cosmetics.OrbitUsers;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Orbit badge in front of Orbit players' names in the Tab list. */
@Mixin(PlayerListHud.class)
public abstract class PlayerListHudMixin {
    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true, require = 0)
    private void orbit$badge(PlayerListEntry entry, CallbackInfoReturnable<Text> cir) {
        if (entry != null && entry.getProfile() != null && OrbitUsers.badgesOn() && OrbitUsers.isOrbit(entry.getProfile().id()))
            cir.setReturnValue(OrbitUsers.withBadge(cir.getReturnValue()));
    }
}
