package dev.orbit.client.mixin;

import dev.orbit.client.OrbitClient;
import dev.orbit.client.module.NewModules;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Weather Changer: the client world reports the weather you picked (the server keeps its own). */
@Mixin(World.class)
public abstract class WeatherMixin {
    private NewModules.WeatherChanger orbit$weather() {
        if (!((Object) this instanceof ClientWorld) || OrbitClient.modules() == null) return null;
        return OrbitClient.modules().get("weather") instanceof NewModules.WeatherChanger w && w.isEnabled() ? w : null;
    }

    @Inject(method = "getRainGradient", at = @At("HEAD"), cancellable = true)
    private void orbit$rain(float tickProgress, CallbackInfoReturnable<Float> cir) {
        var w = orbit$weather();
        if (w != null) cir.setReturnValue(w.rain());
    }

    @Inject(method = "getThunderGradient", at = @At("HEAD"), cancellable = true)
    private void orbit$thunder(float tickProgress, CallbackInfoReturnable<Float> cir) {
        var w = orbit$weather();
        if (w != null) cir.setReturnValue(w.thunder());
    }
}
