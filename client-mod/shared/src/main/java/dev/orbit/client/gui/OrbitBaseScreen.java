package dev.orbit.client.gui;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Maps this Minecraft version's input callbacks to simple, version-independent hooks so
 * OrbitScreen / HudEditorScreen can be shared. (1.14 – 1.21.8 signatures.)
 */
public abstract class OrbitBaseScreen extends Screen {
    protected OrbitBaseScreen(Text title) { super(title); }

    protected boolean onClick(double mx, double my, int button) { return false; }
    protected boolean onRelease(double mx, double my, int button) { return false; }
    protected boolean onDrag(double mx, double my, int button) { return false; }
    protected boolean onScroll(double mx, double my, double amount) { return false; }
    protected boolean onKey(int key, int scan, int mods) { return false; }
    protected boolean onChar(char chr) { return false; }

    @Override public boolean mouseClicked(double mx, double my, int button) { return onClick(mx, my, button) || super.mouseClicked(mx, my, button); }
    @Override public boolean mouseReleased(double mx, double my, int button) { return onRelease(mx, my, button) || super.mouseReleased(mx, my, button); }
    @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) { return onDrag(mx, my, button) || super.mouseDragged(mx, my, button, dx, dy); }
    @Override public boolean mouseScrolled(double mx, double my, double h, double v) { return onScroll(mx, my, v) || super.mouseScrolled(mx, my, h, v); }
    @Override public boolean keyPressed(int key, int scan, int mods) { return onKey(key, scan, mods) || super.keyPressed(key, scan, mods); }
    @Override public boolean charTyped(char chr, int mods) { return onChar(chr) || super.charTyped(chr, mods); }
}
