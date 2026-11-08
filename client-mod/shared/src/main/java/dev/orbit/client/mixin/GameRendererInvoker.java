package dev.orbit.client.mixin;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The real field of view this frame (sprinting, speed, zoom…) so world markers sit exactly on target. */
@Mixin(GameRenderer.class)
public interface GameRendererInvoker {
    @Invoker("getFov") float orbit$getFov(Camera camera, float tickProgress, boolean changingFov);
}
