package dev.orbit.client.mixin;

import dev.orbit.client.OrbitClient;
import dev.orbit.client.gui.OrbitTitleScreen;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Classic pause menu -> "Orbit menu" button in the corner to switch back to Orbit's pause menu. */
@Mixin(GameMenuScreen.class)
public abstract class GameMenuScreenMixin extends Screen {
    protected GameMenuScreenMixin(Text title) { super(title); }

    @Inject(method = "init", at = @At("TAIL"))
    private void orbit$switchButton(CallbackInfo ci) {
        if (!((GameMenuScreen) (Object) this).shouldShowMenu()) return;
        addDrawableChild(ButtonWidget.builder(Text.literal("✦ Orbit menu"), b -> {
            OrbitClient.modules().ui().spaceMenu.set(true);
            OrbitClient.modules().save();
            client.setScreen(OrbitTitleScreen.pause());
        }).dimensions(width - 104, 6, 98, 20).build());
    }
}
