package dev.orbit.client.gui;

import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.util.function.Predicate;

import static dev.orbit.client.gui.Render2D.*;

/**
 * A real text box for Orbit screens: moving cursor, selecting with the mouse (drag / double-click) or
 * Shift + arrows, Ctrl+A select all, Ctrl+C / X / V, Home / End, Delete, Ctrl+Backspace.
 */
public final class TextField {
    public String text = "";
    public int cursor, anchor;            // selection = between anchor and cursor
    public final int max;
    private final Predicate<Character> allowed;
    private float scrollX;
    private long lastClick;
    private boolean dragging;
    // last drawn geometry (for mouse hits)
    private float bx, by, bw, bh;

    public TextField(int max) { this(max, c -> c >= ' '); }
    public TextField(int max, Predicate<Character> allowed) { this.max = max; this.allowed = allowed; }

    public void set(String s) { text = s == null ? "" : s.length() > max ? s.substring(0, max) : s; cursor = anchor = text.length(); }
    public boolean hasSelection() { return cursor != anchor; }
    private int selStart() { return Math.min(cursor, anchor); }
    private int selEnd() { return Math.max(cursor, anchor); }
    public String selected() { return text.substring(selStart(), selEnd()); }
    public void selectAll() { anchor = 0; cursor = text.length(); }

    private void insert(String s) {
        StringBuilder clean = new StringBuilder();
        for (char ch : s.toCharArray()) if (allowed.test(ch)) clean.append(ch);
        String before = text.substring(0, selStart()), after = text.substring(selEnd());
        String add = clean.toString();
        int room = max - before.length() - after.length();
        if (add.length() > room) add = add.substring(0, Math.max(0, room));
        text = before + add + after;
        cursor = anchor = before.length() + add.length();
    }
    private void deleteSelection() { insert(""); }

    private int wordLeft(int i) {
        while (i > 0 && text.charAt(i - 1) == ' ') i--;
        while (i > 0 && Character.isLetterOrDigit(text.charAt(i - 1))) i--;
        return i == cursor && i > 0 ? i - 1 : i;
    }
    private int wordRight(int i) {
        int n = text.length();
        while (i < n && Character.isLetterOrDigit(text.charAt(i))) i++;
        while (i < n && text.charAt(i) == ' ') i++;
        return i == cursor && i < n ? i + 1 : i;
    }

    /** handles a key; true if it was a text-editing key */
    public boolean key(int key, int mods) {
        boolean ctrl = TextKeys.ctrl(mods), shift = (mods & GLFW.GLFW_MOD_SHIFT) != 0;
        switch (key) {
            case GLFW.GLFW_KEY_A -> { if (ctrl) { selectAll(); return true; } return false; }
            case GLFW.GLFW_KEY_C -> { if (ctrl) { TextKeys.copy(hasSelection() ? selected() : text); return true; } return false; }
            case GLFW.GLFW_KEY_X -> { if (ctrl) { TextKeys.copy(hasSelection() ? selected() : text); if (hasSelection()) deleteSelection(); else set(""); return true; } return false; }
            case GLFW.GLFW_KEY_V -> { if (ctrl) { insert(TextKeys.clipboard()); return true; } return false; }
            case GLFW.GLFW_KEY_INSERT -> { if (shift) { insert(TextKeys.clipboard()); return true; } return false; }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (hasSelection()) deleteSelection();
                else if (cursor > 0) { anchor = ctrl ? wordLeft(cursor) : cursor - 1; deleteSelection(); }
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (hasSelection()) deleteSelection();
                else if (cursor < text.length()) { anchor = ctrl ? wordRight(cursor) : cursor + 1; deleteSelection(); }
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                if (hasSelection() && !shift) cursor = anchor = selStart();
                else { cursor = ctrl ? wordLeft(cursor) : Math.max(0, cursor - 1); if (!shift) anchor = cursor; }
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                if (hasSelection() && !shift) cursor = anchor = selEnd();
                else { cursor = ctrl ? wordRight(cursor) : Math.min(text.length(), cursor + 1); if (!shift) anchor = cursor; }
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> { cursor = 0; if (!shift) anchor = 0; return true; }
            case GLFW.GLFW_KEY_END -> { cursor = text.length(); if (!shift) anchor = cursor; return true; }
            default -> { return false; }
        }
    }
    public boolean chr(char c) {
        if (TextKeys.ctrl(0) || c < ' ' || !allowed.test(c)) return TextKeys.ctrl(0);
        insert(String.valueOf(c));
        return true;
    }

    private int indexAt(double mx) {
        double local = mx - (bx + 6) + scrollX;
        int best = 0; double bestD = Double.MAX_VALUE;
        for (int i = 0; i <= text.length(); i++) {
            double d = Math.abs(width(text.substring(0, i)) - local);
            if (d < bestD) { bestD = d; best = i; }
        }
        return best;
    }
    public boolean contains(double mx, double my) { return mx >= bx && my >= by && mx < bx + bw && my < by + bh; }
    /** mouse down inside the box: place the cursor, shift extends, double-click selects all */
    public void click(double mx, int mods) {
        long now = System.currentTimeMillis();
        int i = indexAt(mx);
        if (now - lastClick < 300) { selectAll(); dragging = false; }
        else { cursor = i; if ((mods & GLFW.GLFW_MOD_SHIFT) == 0) anchor = i; dragging = true; }
        lastClick = now;
    }
    public void drag(double mx) { if (dragging) cursor = indexAt(mx); }
    public void release() { dragging = false; }

    /** draws the box; returns nothing — call with the box rectangle each frame */
    public void render(DrawContext c, float x, float y, float w, float h, boolean focused, String placeholder, int bg) {
        bx = x; by = y; bw = w; bh = h;
        round(c, x, y, w, h, Math.round(h / 2), bg);
        float tx = x + 6, ty = y + (h - 8) / 2f;
        float inner = w - 12;
        float curX = width(text.substring(0, Math.min(cursor, text.length())));
        if (curX - scrollX > inner) scrollX = curX - inner;
        if (curX - scrollX < 0) scrollX = curX;
        if (width(text) - scrollX < inner) scrollX = Math.max(0, width(text) - inner);
        c.enableScissor(Math.round(x + 4), Math.round(y), Math.round(x + w - 4), Math.round(y + h));
        if (text.isEmpty() && !focused) text(c, placeholder, tx, ty, FAINT, false);
        if (focused && hasSelection()) {
            float s0 = width(text.substring(0, selStart())) - scrollX, s1 = width(text.substring(0, selEnd())) - scrollX;
            rect(c, tx + s0, ty - 1, s1 - s0, 10, withAlpha(accent(), 150));
        }
        text(c, text, tx - scrollX, ty, TEXT, false);
        if (focused && (System.currentTimeMillis() / 500) % 2 == 0) rect(c, tx + curX - scrollX, ty - 1, 1, 10, 0xFFFFFFFF);
        c.disableScissor();
    }
}
