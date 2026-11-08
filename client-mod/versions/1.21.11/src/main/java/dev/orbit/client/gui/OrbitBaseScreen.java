package dev.orbit.client.gui;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

/** 1.21.9+ input events (Click / KeyInput / CharInput records) mapped to the shared hooks. */
public abstract class OrbitBaseScreen extends Screen {
    protected OrbitBaseScreen(Text title) { super(title); }

    protected boolean onClick(double mx, double my, int button) { return false; }
    protected boolean onRelease(double mx, double my, int button) { return false; }
    protected boolean onDrag(double mx, double my, int button) { return false; }
    protected boolean onScroll(double mx, double my, double amount) { return false; }
    protected boolean onKey(int key, int scan, int mods) { return false; }
    protected boolean onChar(char chr) { return false; }

    @Override public boolean mouseClicked(Click click, boolean doubled) { return onClick(click.x(), click.y(), click.button()) || super.mouseClicked(click, doubled); }
    @Override public boolean mouseReleased(Click click) { return onRelease(click.x(), click.y(), click.button()) || super.mouseReleased(click); }
    @Override public boolean mouseDragged(Click click, double dx, double dy) { return onDrag(click.x(), click.y(), click.button()) || super.mouseDragged(click, dx, dy); }
    @Override public boolean mouseScrolled(double mx, double my, double h, double v) { return onScroll(mx, my, v) || super.mouseScrolled(mx, my, h, v); }
    @Override public boolean keyPressed(KeyInput input) { return onKey(input.key(), input.scancode(), input.modifiers()) || super.keyPressed(input); }
    @Override public boolean charTyped(CharInput input) {
        String s = input.asString();
        return (s.length() == 1 && onChar(s.charAt(0))) || super.charTyped(input);
    }
}
