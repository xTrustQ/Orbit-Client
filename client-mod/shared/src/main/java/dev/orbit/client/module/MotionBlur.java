package dev.orbit.client.module;

import dev.orbit.client.module.setting.Setting;

/** Blends the previous frame over the current one for smooth camera trails. */
public final class MotionBlur extends Module {
    public final Setting.Slider strength = add(new Setting.Slider("strength", "Strength", 50, 10, 90, 5, "%"));
    public MotionBlur() { super("motionblur", "Motion Blur", "Smooth trails when you turn your camera", Category.RENDER, false); }
    public float amount() { return strength.f() / 100f; }
}
