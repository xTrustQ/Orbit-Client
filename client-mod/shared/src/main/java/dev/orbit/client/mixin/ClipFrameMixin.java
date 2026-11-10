package dev.orbit.client.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Clips: hands every finished frame (world + HUD + menus) to the recorder right before it's shown. */
@Mixin(MinecraftClient.class)
public abstract class ClipFrameMixin {
    @Inject(method = "render(Z)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/Framebuffer;blitToScreen()V"))
    private void orbit$clipFrame(boolean tick, CallbackInfo ci) {
        dev.orbit.client.clips.ClipRecorder.onFrame();
    }
}
