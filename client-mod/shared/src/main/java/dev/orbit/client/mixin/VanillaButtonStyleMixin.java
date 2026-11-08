package dev.orbit.client.mixin;

import dev.orbit.client.OrbitClient;
import dev.orbit.client.gui.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.PressableWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Minecraft's own menus keep their layout, but buttons get Orbit's smooth rounded look (when the Orbit menu style is on). */
@Mixin(PressableWidget.class)
public abstract class VanillaButtonStyleMixin {
    @Inject(method = "drawButton", at = @At("HEAD"), cancellable = true)
    private void orbit$button(DrawContext c, CallbackInfo ci) {
        if (!orbit$styled()) return;
        ClickableWidget w = (ClickableWidget) (Object) this;
        boolean on = w.active, sel = w.isSelected();
        int bg = !on ? 0x22FFFFFF : sel ? Render2D.withAlpha(Render2D.accent(), 200) : 0x38FFFFFF;
        Render2D.round(c, w.getX(), w.getY(), w.getWidth(), w.getHeight(), Math.min(10, w.getHeight() / 2), bg);
        if (on && !sel) Render2D.round(c, w.getX() + 1, w.getY() + 1, w.getWidth() - 2, 1, 1, 0x18FFFFFF); // soft top highlight
        ci.cancel();
    }

    private static boolean orbit$styled() {
        try { return OrbitClient.modules() != null && OrbitClient.modules().ui().spaceMenu.on(); } catch (Throwable t) { return false; }
    }
}
