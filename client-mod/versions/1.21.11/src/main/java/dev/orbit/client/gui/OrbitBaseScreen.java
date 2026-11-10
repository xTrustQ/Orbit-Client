package dev.orbit.client.gui;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

/**
 * 1.21.9+ input events (Click / KeyInput / CharInput records) mapped to the shared hooks.
 * Orbit menus have their own size ("Menu size" in Settings): with a big Minecraft GUI scale (4 / Auto on a 1080p
 * screen) they are drawn as if the GUI scale were 3, so they don't look cramped. ScreenScaleMixin gives the screen
 * the bigger canvas and scales the drawing; the mouse is mapped back here.
 */
public abstract class OrbitBaseScreen extends Screen {
    protected OrbitBaseScreen(Text title) { super(title); }

    /** drawing scale for Orbit menus (1 = Minecraft's GUI scale, < 1 = smaller) */
    public static float uiScale() {
        var mc = net.minecraft.client.MinecraftClient.getInstance();
        double s = mc.getWindow().getScaleFactor();
        int target = 3;
        try { target = dev.orbit.client.OrbitClient.modules().ui().menuScale(); } catch (Throwable ignored) { }
        if (target <= 0 || s <= target) return 1f;
        return (float) (target / s);
    }
    private static double k() { return uiScale(); }
    private static Click scaled(Click c) { double k = k(); return k == 1 ? c : new Click(c.x() / k, c.y() / k, c.buttonInfo()); }

    protected boolean onClick(double mx, double my, int button) { return false; }
    protected boolean onRelease(double mx, double my, int button) { return false; }
    protected boolean onDrag(double mx, double my, int button) { return false; }
    protected boolean onScroll(double mx, double my, double amount) { return false; }
    protected boolean onKey(int key, int scan, int mods) { return false; }
    protected boolean onChar(char chr) { return false; }

    @Override public boolean mouseClicked(Click click, boolean doubled) { Click c = scaled(click); return onClick(c.x(), c.y(), c.button()) || super.mouseClicked(c, doubled); }
    @Override public boolean mouseReleased(Click click) { Click c = scaled(click); return onRelease(c.x(), c.y(), c.button()) || super.mouseReleased(c); }
    @Override public boolean mouseDragged(Click click, double dx, double dy) { Click c = scaled(click); double k = k(); return onDrag(c.x(), c.y(), c.button()) || super.mouseDragged(c, dx / k, dy / k); }
    @Override public boolean mouseScrolled(double mx, double my, double h, double v) { double k = k(); return onScroll(mx / k, my / k, v) || super.mouseScrolled(mx / k, my / k, h, v); }
    @Override public void mouseMoved(double mx, double my) { double k = k(); super.mouseMoved(mx / k, my / k); }
    @Override public boolean keyPressed(KeyInput input) { return onKey(input.key(), input.scancode(), input.modifiers()) || super.keyPressed(input); }
    @Override public boolean charTyped(CharInput input) {
        String s = input.asString();
        return (s.length() == 1 && onChar(s.charAt(0))) || super.charTyped(input);
    }
}
