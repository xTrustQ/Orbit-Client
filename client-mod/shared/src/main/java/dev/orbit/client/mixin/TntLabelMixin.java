package dev.orbit.client.mixin;

import dev.orbit.client.OrbitClient;
import dev.orbit.client.module.NewModules;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.TntEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** TNT Timer: lit TNT gets a name tag with the seconds left on its fuse. */
@Mixin(EntityRenderer.class)
public abstract class TntLabelMixin {
    private static NewModules.TntTimer orbit$timer() {
        var m = OrbitClient.modules() == null ? null : OrbitClient.modules().get("tnttimer");
        return m instanceof NewModules.TntTimer t && t.isEnabled() ? t : null;
    }

    @Inject(method = "hasLabel", at = @At("HEAD"), cancellable = true, require = 0)
    private void orbit$tntHasLabel(Entity entity, double distance, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof TntEntity && orbit$timer() != null) cir.setReturnValue(true);
    }

    @Inject(method = "getDisplayName", at = @At("HEAD"), cancellable = true, require = 0)
    private void orbit$tntName(Entity entity, CallbackInfoReturnable<Text> cir) {
        var t = orbit$timer();
        if (entity instanceof TntEntity tnt && t != null) cir.setReturnValue(t.label(tnt));
    }

    /** Orbit badge in front of the name tags of Orbit players. */
    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true, require = 0)
    private void orbit$badge(Entity entity, CallbackInfoReturnable<Text> cir) {
        if (entity instanceof net.minecraft.entity.player.PlayerEntity p && cir.getReturnValue() != null
                && dev.orbit.client.cosmetics.OrbitUsers.badgesOn() && dev.orbit.client.cosmetics.OrbitUsers.isOrbit(p.getUuid()))
            cir.setReturnValue(dev.orbit.client.cosmetics.OrbitUsers.withBadge(cir.getReturnValue()));
    }
}
