package dev.orbit.client.mixin;

import dev.orbit.client.OrbitClient;
import dev.orbit.client.module.NewModules;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Time Changer: a client-side time of day, only you see it. */
@Mixin(ClientWorld.Properties.class)
public abstract class ClientWorldPropertiesMixin {
    @Inject(method = "getTimeOfDay", at = @At("RETURN"), cancellable = true, require = 0)
    private void orbit$time(CallbackInfoReturnable<Long> cir) {
        var m = (NewModules.TimeChanger) OrbitClient.modules().get("time");
        if (m.isEnabled()) cir.setReturnValue(m.time());
    }
}
