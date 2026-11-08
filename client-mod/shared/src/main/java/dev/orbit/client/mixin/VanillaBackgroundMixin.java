package dev.orbit.client.mixin;

import dev.orbit.client.OrbitClient;
import dev.orbit.client.gui.OrbitBaseScreen;
import dev.orbit.client.gui.SpaceBackground;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Minecraft's own menus (options, video, controls…) show Orbit's menu background instead of the panorama. */
@Mixin(Screen.class)
public abstract class VanillaBackgroundMixin {
    private static boolean orbit$styled() {
        try { return OrbitClient.modules() != null && OrbitClient.modules().ui().spaceMenu.on(); } catch (Throwable t) { return false; }
    }

    @Inject(method = "renderPanoramaBackground", at = @At("HEAD"), cancellable = true)
    private void orbit$space(DrawContext c, float delta, CallbackInfo ci) {
        Screen self = (Screen) (Object) this;
        if (self instanceof OrbitBaseScreen || !orbit$styled()) return;
        var mc = MinecraftClient.getInstance();
        double sx = mc.mouse.getX() * self.width / Math.max(1, mc.getWindow().getWidth()), sy = mc.mouse.getY() * self.height / Math.max(1, mc.getWindow().getHeight());
        SpaceBackground.draw(c, self.width, self.height, (int) sx, (int) sy, false);
        ci.cancel();
    }

    @Inject(method = "renderInGameBackground", at = @At("HEAD"), cancellable = true)
    private void orbit$inGame(DrawContext c, CallbackInfo ci) {
        Screen self = (Screen) (Object) this;
        if (!orbit$styled()) return;
        c.fillGradient(0, 0, self.width, self.height, 0xA0080514, 0xD0080514);
        ci.cancel();
    }
}
