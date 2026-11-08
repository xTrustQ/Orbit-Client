package dev.orbit.client.mixin;

import dev.orbit.client.OrbitClient;
import dev.orbit.client.module.HudModules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public abstract class MouseMixin {
    @Inject(method = "onMouseButton", at = @At("HEAD"))
    private void orbit$cps(long window, int button, int action, int mods, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (action == GLFW.GLFW_PRESS && window == mc.getWindow().getHandle() && mc.currentScreen == null) HudModules.Cps.click(button);
    }

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void orbit$zoomScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (vertical != 0 && OrbitClient.modules().zoom().onScroll(vertical)) ci.cancel();
    }
}
