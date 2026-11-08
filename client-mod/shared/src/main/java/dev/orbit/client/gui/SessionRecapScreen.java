package dev.orbit.client.gui;

import dev.orbit.client.pvp.Combat;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

import static dev.orbit.client.gui.Render2D.*;

/** Session Recap: a summary card of the world/server you just left. */
public final class SessionRecapScreen extends OrbitBaseScreen {
    private final Screen parent;
    private final Combat.Snapshot s;
    private final long openedAt = System.currentTimeMillis();

    public SessionRecapScreen(Screen parent, Combat.Snapshot s) { super(Text.literal("Session recap")); this.parent = parent; this.s = s; }
    @Override public void close() { client.setScreen(parent); }
    @Override public void renderBackground(DrawContext c, int mx, int my, float d) { SpaceBackground.draw(c, width, height, mx, my, false); c.fill(0, 0, width, height, 0x60050310); }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        super.render(c, mx, my, delta);
        float in = easeOut((System.currentTimeMillis() - openedAt) / 450f);
        int pw = Math.min(360, width - 30), px = (width - pw) / 2, ph = 196, py = Math.round((height - ph) / 2f + (1 - in) * 14);
        round(c, px - 1, py - 1, pw + 2, ph + 2, 16, withAlpha(accent(), (int) (110 * in)));
        round(c, px, py, pw, ph, 15, withAlpha(0x0E0B1C, (int) (240 * in)));
        logo(c, px + 22, py + 21, 6f);
        bold(c, "Session recap", px + 36, py + 12, alpha(0xFFFFFFFF, in), false);
        long sec = s.millis() / 1000;
        String time = sec >= 3600 ? (sec / 3600) + "h " + (sec / 60 % 60) + "m" : (sec / 60) + "m " + (sec % 60) + "s";
        text(c, fit(s.place() + "  ·  played " + time, pw - 50, false), px + 36, py + 24, alpha(MUTED, in), false);
        int total = s.hits() + s.misses(), acc = total == 0 ? 0 : Math.round(s.hits() * 100f / total);
        float kd = s.deaths() == 0 ? s.kills() : s.kills() / (float) s.deaths();
        String[][] tiles = {
                {String.valueOf(s.kills()), "Kills"}, {String.valueOf(s.deaths()), "Deaths"}, {String.format(Locale.ROOT, "%.2f", kd), "K/D"},
                {String.valueOf(s.hits()), "Hits"}, {acc + "%", "Accuracy"}, {String.valueOf(s.bestCombo()), "Best combo"},
                {String.format(Locale.ROOT, "%.0f", s.dealt() / 2), "Hearts dealt"}, {String.format(Locale.ROOT, "%.0f", s.taken() / 2), "Hearts taken"},
                {s.distance() >= 1000 ? String.format(Locale.ROOT, "%.1fkm", s.distance() / 1000) : Math.round(s.distance()) + "m", "Walked"}};
        int cols = 3, tw = (pw - 28 - 12) / cols, th = 38;
        for (int i = 0; i < tiles.length; i++) {
            float ap = easeOut((System.currentTimeMillis() - openedAt - 150 - i * 50) / 350f);
            int x = px + 14 + (i % cols) * (tw + 6), y = py + 42 + (i / cols) * (th + 6) + Math.round((1 - ap) * 8);
            round(c, x, y, tw, th, 9, withAlpha(0xFFFFFF, (int) (20 * ap)));
            float k = Render2D.pixelScale(1.5f);
            dev.orbit.client.compat.Compat.push(c);
            dev.orbit.client.compat.Compat.translate(c, x + tw / 2f - boldWidth(tiles[i][0]) * k / 2f, y + 6);
            dev.orbit.client.compat.Compat.scale(c, k);
            bold(c, tiles[i][0], 0, 0, alpha(i < 3 ? lerpColor(0xFFFFFFFF, accent(), 0.4f) : 0xFFFFFFFF, ap), false);
            dev.orbit.client.compat.Compat.pop(c);
            centered(c, tiles[i][1], x + tw / 2f, y + 24, alpha(MUTED, ap), false);
        }
        String w = "W-taps " + s.wtaps() + "  ·  misses " + s.misses();
        centered(c, w, width / 2f, py + ph - 38, alpha(FAINT, in), false);
        boolean h = mx >= width / 2 - 50 && mx < width / 2 + 50 && my >= py + ph - 26 && my < py + ph - 8;
        SpaceBackground.button(c, width / 2 - 50, py + ph - 26, 100, 18, "Continue", mx, my, h ? 1 : 0, true);
    }
    @Override protected boolean onClick(double mx, double my, int button) { Sounds.click(); close(); return true; }
    @Override protected boolean onKey(int key, int scan, int mods) { if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER) { close(); return true; } return false; }
}
