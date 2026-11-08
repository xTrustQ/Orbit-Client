package dev.orbit.client.mixin;

import dev.orbit.client.OrbitClient;
import dev.orbit.client.gui.OrbitTitleScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Classic menu → "Orbit menu" button in the corner to switch back. */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    protected TitleScreenMixin(Text title) { super(title); }

    @Inject(method = "init", at = @At("TAIL"))
    private void orbit$switchButton(CallbackInfo ci) {
        addDrawableChild(ButtonWidget.builder(Text.literal("✦ Orbit menu"), b -> {
            OrbitClient.modules().ui().spaceMenu.set(true);
            OrbitClient.modules().save();
            client.setScreen(new OrbitTitleScreen());
        }).dimensions(width - 104, 6, 98, 20).build());
    }
}
