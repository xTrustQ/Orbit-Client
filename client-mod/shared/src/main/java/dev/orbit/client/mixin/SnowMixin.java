package dev.orbit.client.mixin;

import dev.orbit.client.OrbitClient;
import dev.orbit.client.module.NewModules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Weather Changer "Snow": everything you see falling is snow. Only on the render thread, never on the server. */
@Mixin(Biome.class)
public abstract class SnowMixin {
    @Inject(method = "getPrecipitation", at = @At("HEAD"), cancellable = true)
    private void orbit$snow(BlockPos pos, int seaLevel, CallbackInfoReturnable<Biome.Precipitation> cir) {
        if (OrbitClient.modules() == null) return;
        var mc = MinecraftClient.getInstance();
        if (mc == null || !mc.isOnThread()) return;
        if (OrbitClient.modules().get("weather") instanceof NewModules.WeatherChanger w && w.isEnabled() && w.snow()) cir.setReturnValue(Biome.Precipitation.SNOW);
    }
}
