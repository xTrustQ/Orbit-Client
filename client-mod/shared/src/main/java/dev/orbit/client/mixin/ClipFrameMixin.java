package dev.orbit.client.mixin;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Clips: hands every finished frame (world + HUD + menus) to the recorder. Hooked at the end of the game's own
 * render pass — not at "show on screen", which Minecraft skips whenever the window has no visible size.
 */
@Mixin(GameRenderer.class)
public abstract class ClipFrameMixin {
    @Inject(method = "render(Lnet/minecraft/client/render/RenderTickCounter;Z)V", at = @At("RETURN"))
    private void orbit$clipFrame(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        dev.orbit.client.clips.ClipRecorder.onFrame();
    }
}
