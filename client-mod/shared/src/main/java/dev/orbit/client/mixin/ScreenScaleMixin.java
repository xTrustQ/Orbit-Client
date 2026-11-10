package dev.orbit.client.mixin;

import dev.orbit.client.gui.OrbitBaseScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Orbit menus at their own size: an Orbit screen gets a bigger canvas (width / height divided by the menu scale)
 * and everything it draws is scaled down to fit, so at GUI scale 4 it looks like GUI scale 3.
 */
@Mixin(Screen.class)
public abstract class ScreenScaleMixin {
    private boolean orbit$orbit() { return (Object) this instanceof OrbitBaseScreen; }

    @ModifyVariable(method = "init(II)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int orbit$width(int w) { return orbit$orbit() ? (int) Math.ceil(w / OrbitBaseScreen.uiScale()) : w; }
    @ModifyVariable(method = "init(II)V", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private int orbit$height(int h) { return orbit$orbit() ? (int) Math.ceil(h / OrbitBaseScreen.uiScale()) : h; }

    @Inject(method = "renderWithTooltip", at = @At("HEAD"))
    private void orbit$push(DrawContext c, int mx, int my, float delta, CallbackInfo ci) {
        if (!orbit$orbit()) return;
        float k = OrbitBaseScreen.uiScale();
        c.getMatrices().pushMatrix();
        c.getMatrices().scale(k, k);
    }
    @ModifyVariable(method = "renderWithTooltip", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int orbit$mx(int mx) { return orbit$orbit() ? Math.round(mx / OrbitBaseScreen.uiScale()) : mx; }
    @ModifyVariable(method = "renderWithTooltip", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private int orbit$my(int my) { return orbit$orbit() ? Math.round(my / OrbitBaseScreen.uiScale()) : my; }
    @Inject(method = "renderWithTooltip", at = @At("RETURN"))
    private void orbit$pop(DrawContext c, int mx, int my, float delta, CallbackInfo ci) {
        if (orbit$orbit()) c.getMatrices().popMatrix();
    }
}
