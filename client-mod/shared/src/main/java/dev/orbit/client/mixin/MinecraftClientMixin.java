package dev.orbit.client.mixin;

import dev.orbit.client.OrbitClient;
import dev.orbit.client.gui.OrbitTitleScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Shows the Orbit space menu wherever Minecraft would open its title screen. */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true, require = 0)
    private Screen orbit$titleScreen(Screen screen) {
        if (OrbitClient.modules() == null) return screen;
        // Closing a screen from the main menu (e.g. Esc in Orbit Mods) means "go back to the title screen";
        // vanilla would build its own TitleScreen further down, so catch it here.
        MinecraftClient mc = (MinecraftClient) (Object) this;
        if (screen == null && mc.world == null && mc.player == null && mc.getOverlay() == null && OrbitClient.modules().ui().spaceMenu.on())
            return new OrbitTitleScreen();
        // Leaving a server (and its "Back to server list") opens vanilla's server list — use Orbit's instead.
        if (screen instanceof net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen && OrbitClient.modules().ui().spaceMenu.on())
            return new dev.orbit.client.gui.OrbitServersScreen(new OrbitTitleScreen());
        // Esc in a world or on a server: Orbit's pause menu (Locker, Friends, Screenshots without leaving)
        if (screen instanceof net.minecraft.client.gui.screen.GameMenuScreen gm && gm.shouldShowMenu() && OrbitClient.modules().ui().spaceMenu.on()) {
            if (OrbitTitleScreen.allowVanillaPause) { OrbitTitleScreen.allowVanillaPause = false; return screen; }
            return OrbitTitleScreen.pause();
        }
        if (!(screen instanceof TitleScreen)) return screen;
        if (OrbitTitleScreen.allowVanillaOnce) { OrbitTitleScreen.allowVanillaOnce = false; return screen; }
        return OrbitClient.modules().ui().spaceMenu.on() ? new OrbitTitleScreen() : screen;
    }
}
