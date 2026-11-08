package dev.orbit.client.module;

import dev.orbit.client.OrbitClient;
import dev.orbit.client.emote.EmoteSync;
import dev.orbit.client.emote.Emotes;
import dev.orbit.client.gui.EmoteWheelScreen;
import dev.orbit.client.module.setting.Setting;

/** Emote wheel (hold B by default) + syncing emotes with other Orbit players. */
public final class EmotesModule extends Module {
    final Setting.Key key = add(new Setting.Key("key", "Emote wheel key", () -> OrbitClient.emoteKey));
    public final Setting.Bool camera = add(new Setting.Bool("camera", "Turn the camera to see yourself", true));
    public final Setting.Bool sync = add(new Setting.Bool("sync", "Show to other Orbit players", true));

    public EmotesModule() { super("emotes", "Emotes", "Hold the key for the emote wheel — dance, wave, floss…", Category.PLAYER, true); }

    @Override public void tick() {
        Emotes.tick();
        while (OrbitClient.emoteKey.wasPressed()) if (mc.currentScreen == null && mc.player != null) mc.setScreen(new EmoteWheelScreen());
    }
}
