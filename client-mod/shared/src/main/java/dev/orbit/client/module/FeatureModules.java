package dev.orbit.client.module;

import dev.orbit.client.OrbitClient;
import dev.orbit.client.cosmetics.CapeManager;
import dev.orbit.client.mixin.SimpleOptionAccessor;
import dev.orbit.client.module.setting.Setting;
import dev.orbit.client.sky.SkyPreset;
import dev.orbit.client.sky.SkyRenderer;

import java.util.Arrays;

/** Non-HUD modules. */
public final class FeatureModules {
    private FeatureModules() {}

    // ================================================================= Zoom
    public static final class Zoom extends Module {
        final Setting.Key key = add(new Setting.Key("key", "Zoom key", () -> OrbitClient.zoomKey));
        final Setting.Slider factor = add(new Setting.Slider("factor", "Zoom amount", 4, 1.5, 20, 0.5, "x"));
        final Setting.Bool smooth = add(new Setting.Bool("smooth", "Smooth animation", true));
        final Setting.Bool scroll = add(new Setting.Bool("scroll", "Scroll to adjust", true));
        final Setting.Bool cinematic = add(new Setting.Bool("cinematic", "Cinematic camera", true));
        private float current = 1, target = 1, scrollMul = 1;
        private long last = System.nanoTime();
        private boolean wasZooming, prevSmooth;

        public Zoom() { super("zoom", "Zoom", "Hold to zoom, scroll to adjust", Category.PLAYER, true); }

        public boolean zooming() { return isEnabled() && mc.currentScreen == null && OrbitClient.zoomKey.isPressed(); }

        /** Called every frame from the FOV mixin. Returns the divisor for the FOV. */
        public float fovDivisor() {
            long now = System.nanoTime(); float dt = Math.min(0.1f, (now - last) / 1e9f); last = now;
            boolean z = zooming();
            if (z != wasZooming) {
                if (cinematic.on()) { if (z) { prevSmooth = mc.options.smoothCameraEnabled; mc.options.smoothCameraEnabled = true; } else mc.options.smoothCameraEnabled = prevSmooth; }
                if (!z) scrollMul = 1;
                wasZooming = z;
            }
            target = z ? factor.f() * scrollMul : 1;
            current = smooth.on() ? dev.orbit.client.gui.Render2D.approach(current, target, 14, dt) : target;
            return Math.max(1, current);
        }

        /** Returns true when the scroll was consumed. */
        public boolean onScroll(double amount) {
            if (!zooming() || !scroll.on()) return false;
            scrollMul = (float) Math.max(0.25, Math.min(6, scrollMul * (amount > 0 ? 1.15 : 1 / 1.15)));
            return true;
        }
    }

    // ================================================================= Fullbright
    public static final class Fullbright extends Module {
        private Double saved;
        public Fullbright() { super("fullbright", "Fullbright", "See clearly in the dark", Category.RENDER, false); }
        @SuppressWarnings("unchecked")
        private void setGamma(double v) { ((SimpleOptionAccessor) (Object) mc.options.getGamma()).orbit$setValue(v); }
        @Override public void tick() {
            if (mc.options == null) return;
            if (isEnabled() && mc.options.getGamma().getValue() < 15) { if (saved == null) saved = mc.options.getGamma().getValue(); setGamma(16.0); }
        }
        @Override protected void onDisable() { if (mc.options != null) setGamma(saved != null && saved <= 1 ? saved : 0.5); saved = null; }
    }

    // ================================================================= Toggle sprint
    public static final class ToggleSprint extends Module {
        public ToggleSprint() { super("togglesprint", "Toggle Sprint", "Always sprint while moving forward", Category.PLAYER, false); }
        @Override public void tick() {
            if (mc.player == null || mc.currentScreen != null) return;
            if (mc.options.forwardKey.isPressed() && !mc.player.isSneaking() && !mc.player.horizontalCollision && mc.player.getHungerManager().getFoodLevel() > 6)
                mc.player.setSprinting(true);
        }
    }

    // ================================================================= Sky changer
    public static final class SkyChanger extends Module {
        public final Setting.Mode preset = add(new Setting.Mode("preset", "Sky", SkyPreset.AURORA.title,
                Arrays.stream(SkyPreset.values()).map(p -> p.title).toArray(String[]::new)));
        public final Setting.Bool celestial = add(new Setting.Bool("celestial", "Show sun & moon", false));
        public final Setting.Bool dayCycle = add(new Setting.Bool("dayCycle", "Darken at night", true));
        public final Setting.Bool tintFog = add(new Setting.Bool("tintFog", "Match fog to sky", true));
        public final Setting.Slider brightness = add(new Setting.Slider("brightness", "Brightness", 100, 30, 150, 5, "%"));
        public SkyChanger() { super("sky", "Sky Changer", "Replace the overworld sky with a preset", Category.RENDER, false); }
        public SkyPreset current() { return SkyPreset.byTitle(preset.get()); }
        @Override public void tick() { if (isEnabled()) SkyRenderer.ensure(current()); }
    }

    // ================================================================= Capes
    public static final class Capes extends Module {
        public final Setting.Bool elytra = add(new Setting.Bool("elytra", "Use on elytra", true));
        public Capes() { super("capes", "Orbit Capes", "Shows the cape picked in the launcher", Category.RENDER, true); }
        @Override public void tick() { if (isEnabled()) CapeManager.tick(); }
    }

    // ================================================================= Interface
    public static final class Interface extends Module {
        public static final String[] ACCENTS = {"Nebula", "Deep Space", "Supernova", "Aurora", "Mars", "Eclipse"};
        private static final int[] ACCENT_RGB = {0xB18CFF, 0x4CC9FF, 0xFFB347, 0x34F5A5, 0xFF6B4A, 0xF2F2F2};
        private static final int[] ACCENT_2 = {0xFF6FD8, 0x6D7DFF, 0xFF5E3A, 0x22D3EE, 0xFFA94D, 0x9CA3AF};
        public final Setting.Key menuKey = add(new Setting.Key("menuKey", "Open this menu", () -> OrbitClient.menuKey));
        public final Setting.Mode theme = add(new Setting.Mode("accent", "Theme", "Nebula", ACCENTS));
        /** how big Orbit's own menus are, independent of Minecraft's GUI scale (never bigger than it) */
        public final Setting.Mode menuSize = add(new Setting.Mode("menuSize", "Orbit menu size", "Normal (like GUI 3)", "Small (like GUI 2)", "Normal (like GUI 3)", "Large (like GUI 4)", "Same as Minecraft"));
        public int menuScale() { return menuSize.is("Small (like GUI 2)") ? 2 : menuSize.is("Large (like GUI 4)") ? 4 : menuSize.is("Same as Minecraft") ? 0 : 3; }
        public final Setting.Bool spaceMenu = add(new Setting.Bool("spaceMenu", "Orbit main menu", true));
        public final Setting.Mode menuSky = add(new Setting.Mode("menuSky", "Main menu background", "Nebula", dev.orbit.client.gui.SpaceBackground.NAMES));
        public final Setting.Bool menuPlanet = add(new Setting.Bool("menuPlanet", "Planet in the main menu", true));
        public final Setting.Bool menuHills = add(new Setting.Bool("menuHills", "Hills at the bottom of menus", true));
        public final Setting.Bool menuSpin = add(new Setting.Bool("menuSpin", "Your player slowly turns in the menu", true));
        public final Setting.Bool menuParticles = add(new Setting.Bool("menuParticles", "Floating particles in the menu", true));
        public final Setting.Bool hideInF3 = add(new Setting.Bool("hideF3", "Hide HUD while F3 is open", true));
        public final Setting.Bool blur = add(new Setting.Bool("blur", "Blur behind menu", true));
        public final Setting.Bool sounds = add(new Setting.Bool("sounds", "Menu sound effects", true));
        public final Setting.Bool smoothFont = add(new Setting.Bool("smoothFont", "Smooth font (off = Minecraft font)", false));
        public final Setting.Bool friendToasts = add(new Setting.Bool("friendToasts", "Pop-up when a friend comes online or invites you", true));
        public final Setting.Bool shareServer = add(new Setting.Bool("shareServer", "Show friends which server I'm on", true));
        public final Setting.Bool publicProfile = add(new Setting.Bool("publicProfile", "Public player profile on orbit-client.com (play time, streaks & leaderboards)", true));
        public final Setting.Bool settingsSync = add(new Setting.Bool("settingsSync", "Save my Orbit settings to my account (Settings sync)", true));
        public final Setting.Action syncUpload = add(new Setting.Action("syncUpload", "Save settings to my account now", "Upload", dev.orbit.client.sync.SettingsSync::uploadNow));
        public final Setting.Action syncRestore = add(new Setting.Action("syncRestore", "Replace this PC's settings with my account's", "Restore", dev.orbit.client.sync.SettingsSync::restoreNow));
        public Interface() { super("interface", "Interface", "Theme, main menu & HUD behaviour", Category.CLIENT, true, false); }
        private int idx() { return Math.max(0, theme.options.indexOf(theme.get())); }
        public int accent() { return 0xFF000000 | ACCENT_RGB[idx()]; }
        public static int accentOf(int i) { return ACCENT_RGB[Math.max(0, Math.min(ACCENT_RGB.length - 1, i))]; }
        public int accent2() { return 0xFF000000 | ACCENT_2[idx()]; }
    }
}
