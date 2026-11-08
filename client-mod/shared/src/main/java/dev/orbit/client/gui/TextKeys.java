package dev.orbit.client.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/** Copy / cut / paste for Orbit's text boxes (Ctrl+C/X/V, Shift+Insert, Ctrl+Backspace deletes a word). */
public final class TextKeys {
    private TextKeys() {}

    private static boolean down(int key) {
        try { return InputUtil.isKeyPressed(MinecraftClient.getInstance().getWindow(), key); } catch (Throwable t) { return false; }
    }
    public static boolean ctrl(int mods) {
        return (mods & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0
                || down(GLFW.GLFW_KEY_LEFT_CONTROL) || down(GLFW.GLFW_KEY_RIGHT_CONTROL) || down(GLFW.GLFW_KEY_LEFT_SUPER) || down(GLFW.GLFW_KEY_RIGHT_SUPER);
    }
    private static boolean shift(int mods) { return (mods & GLFW.GLFW_MOD_SHIFT) != 0 || down(GLFW.GLFW_KEY_LEFT_SHIFT) || down(GLFW.GLFW_KEY_RIGHT_SHIFT); }

    /** clipboard text on one line, without control characters */
    public static String clipboard() {
        try {
            String s = MinecraftClient.getInstance().keyboard.getClipboard();
            return s == null ? "" : s.replaceAll("[\\r\\n\\t]+", " ").replaceAll("\\p{Cntrl}", "").trim();
        } catch (Throwable t) { return ""; }
    }
    public static void copy(String s) { try { MinecraftClient.getInstance().keyboard.setClipboard(s); } catch (Throwable ignored) { } }

    /**
     * Handles a clipboard/editing shortcut for a text value. Returns the new value, or null when the key
     * isn't one of these shortcuts (the screen handles it as usual).
     */
    public static String edit(String value, int key, int mods, int max) {
        boolean c = ctrl(mods);
        if ((c && key == GLFW.GLFW_KEY_V) || (shift(mods) && key == GLFW.GLFW_KEY_INSERT)) {
            String v = value + clipboard();
            Sounds.click();
            return v.length() > max ? v.substring(0, max) : v;
        }
        if (c && key == GLFW.GLFW_KEY_C) { copy(value); Sounds.click(); return value; }
        if (c && key == GLFW.GLFW_KEY_X) { copy(value); Sounds.click(); return ""; }
        if (c && key == GLFW.GLFW_KEY_A) return value; // nothing to select in these simple boxes, just don't type an "a"
        if (c && key == GLFW.GLFW_KEY_BACKSPACE) {
            String t = value.stripTrailing();
            int i = Math.max(t.lastIndexOf(' '), Math.max(t.lastIndexOf('.'), t.lastIndexOf(':')));
            return i < 0 ? "" : t.substring(0, i + 1);
        }
        return null;
    }
}
