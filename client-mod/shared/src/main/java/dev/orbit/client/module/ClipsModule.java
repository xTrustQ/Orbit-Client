package dev.orbit.client.module;

import dev.orbit.client.OrbitClient;
import dev.orbit.client.module.setting.Setting;

/** Clips: press F8 to save the last seconds of gameplay as a video (see ClipRecorder). */
public final class ClipsModule extends Module {
    public final Setting.Key key = add(new Setting.Key("key", "Save clip", () -> OrbitClient.clipKey));
    public final Setting.Slider length = add(new Setting.Slider("length", "Clip length", 30, 10, 60, 5, " s"));
    public final Setting.Mode rate = add(new Setting.Mode("fps", "Frame rate", "30 fps", "20 fps", "30 fps", "60 fps"));
    public final Setting.Mode quality = add(new Setting.Mode("quality", "Quality", "720p", "540p", "720p", "1080p"));

    public ClipsModule() { super("clips", "Clips", "Press F8 to save the last 30 seconds as a video (uses some FPS while on)", Category.PLAYER, false); }

    public int fps() { return rate.is("20 fps") ? 20 : rate.is("60 fps") ? 60 : 30; }
    public int height() { return quality.is("540p") ? 540 : quality.is("1080p") ? 1080 : 720; }
    public int seconds() { return (int) Math.round(length.get()); }
}
