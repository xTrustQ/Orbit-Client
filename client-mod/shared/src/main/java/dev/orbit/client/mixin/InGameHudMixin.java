package dev.orbit.client.mixin;

import dev.orbit.client.OrbitClient;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hides vanilla's top-right effect icons while the Orbit Potion Effects element is on (no duplicates). */
@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    @Inject(method = "renderStatusEffectOverlay", at = @At("HEAD"), cancellable = true, require = 0)
    private void orbit$hideVanillaEffects(CallbackInfo ci) {
        if (OrbitClient.modules().get("potions").isEnabled()) ci.cancel();
    }

    /** Custom crosshair replaces the vanilla one (Orbit draws it in the HUD pass). */
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true, require = 0)
    private void orbit$crosshair(CallbackInfo ci) {
        if (OrbitClient.modules().crosshair().replacesVanilla()) ci.cancel();
    }
}
