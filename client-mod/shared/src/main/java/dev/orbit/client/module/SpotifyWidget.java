package dev.orbit.client.module;

import dev.orbit.client.compat.Compat;
import dev.orbit.client.gui.Render2D;
import dev.orbit.client.module.setting.Setting;
import dev.orbit.client.spotify.SpotifyLyrics;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.util.Identifier;

/**
 * "Now playing" card: album cover, title/artist (any script — the Orbit font falls back to
 * Minecraft's for Cyrillic etc.), progress and controls. Open chat (T) to click
 * previous / play-pause / next.
 */
public final class SpotifyWidget extends HudModule {
    private static final int W = 196, H = 74, COVER = 58, PAD = 8, BAR_Y = 55;
    final Setting.Bool cover = add(new Setting.Bool("cover", "Album cover", true));
    final Setting.Bool tint = add(new Setting.Bool("tint", "Tint with cover colours", true));
    final Setting.Bool controls = add(new Setting.Bool("controls", "Show controls", true));
    final Setting.Bool hideWhenPaused = add(new Setting.Bool("hidePaused", "Hide when nothing plays", true));

    // where the card was last drawn (for clicks while chat is open)
    private int lastX, lastY;
    private float lastScale = 1;
    private long pressedAt;
    private int pressed = -1;

    public SpotifyWidget() {
        super("spotify", "Spotify", "Now playing — open chat to pause or skip", false, -4, -4);
        background.set(false);
        Compat.onChatClick(this::chatClick);
    }

    @Override protected boolean outerBackground() { return false; }
    @Override protected int pad() { return 0; }
    @Override public int contentWidth(boolean p) { return W; }
    @Override public int contentHeight(boolean p) { return H; }
    @Override public boolean hasContent() {
        SpotifyLyrics.Snapshot s = SpotifyLyrics.get();
        return !s.title().isEmpty() && (s.playing() || !hideWhenPaused.on() || chatOpen());
    }
    private boolean chatOpen() { return mc.currentScreen instanceof ChatScreen; }

    @Override
    public void render(DrawContext c, int sw, int sh, boolean preview) {
        lastX = screenX(sw, preview);
        lastY = screenY(sh, preview);
        lastScale = scaleF();
        super.render(c, sw, sh, preview);
    }


    @Override
    protected void renderContent(DrawContext c, boolean preview) {
        SpotifyLyrics.Snapshot s = SpotifyLyrics.get();
        boolean real = !s.title().isEmpty();
        String title = real ? s.title() : "Звёздная пыль", artist = real ? s.artist() : "Orbit Radio";
        String album = real ? SpotifyLyrics.album() : "Connect Spotify in the launcher";
        long pos = real ? s.position() : 77_000, dur = real ? s.durationMs() : 382_000;
        boolean playing = !real || s.playing();
        boolean chat = chatOpen() || preview;

        // glassy card tinted with the album colours
        int artCol = real ? SpotifyLyrics.artColor() : 0xFF6A4FC8;
        int base = tint.on() ? Render2D.lerpColor(0xFF221B28, artCol, 0.5f) : 0xFF221B28;
        int card = Render2D.withAlpha(base, 205);
        Render2D.round(c, 0, 0, W, H, 8, card);
        c.fillGradient(0, 8, W, H - 8, 0x14FFFFFF, 0x00000000);

        int tx = 10;
        if (cover.on()) {
            Identifier art = SpotifyLyrics.art();
            if (real && art != null) {
                Compat.blit(c, art, PAD, PAD, COVER, COVER, 64, 64);
                Render2D.cutCorners(c, PAD, PAD, COVER, COVER, 4, card);
            } else {
                Render2D.round(c, PAD, PAD, COVER, COVER, 4, Render2D.withAlpha(artCol, 170));
                Render2D.icon(c, "play", PAD + COVER / 2f - 8, PAD + COVER / 2f - 8, 16, 0xDDFFFFFF);
            }
            tx = PAD + COVER + 8;
        }
        int right = W - 8, tw = right - tx;

        // NOW PLAYING (small, letter-spaced) + signal bars
        spaced(c, "NOW PLAYING", tx, 9, 0xB0FFFFFF);
        long t = System.currentTimeMillis();
        for (int i = 0; i < 4; i++) {
            float h = 2 + i * 1.6f;
            if (playing) h *= 0.75f + 0.25f * (float) Math.abs(Math.sin(t / 260.0 + i));
            Render2D.rect(c, right - 13 + i * 3.5f, 13 - h, 2, h, 0xE6FFFFFF);
        }

        Render2D.bold(c, fit(title, tw, true), tx, 20, 0xFFFFFFFF, true);
        Render2D.text(c, fit(artist, tw, false), tx, 31, 0xFFE8E2F0, true);
        if (!album.isEmpty() && !album.equals(title)) Render2D.text(c, fit(album, tw, false), tx, 42, 0x8CFFFFFF, false);

        // progress bar with knob, times underneath
        int by = BAR_Y;
        float f = dur > 0 ? Math.min(1f, pos / (float) dur) : 0;
        Render2D.rect(c, tx, by, tw, 2, 0x40FFFFFF);
        Render2D.rect(c, tx, by, tw * f, 2, 0xFFFFFFFF);
        Render2D.disc(c, tx + tw * f, by + 1, 3, 0xFFFFFFFF);
        small(c, time(pos), tx, by + 6, 0xB0FFFFFF);
        String b = time(dur);
        small(c, b, right - smallWidth(b), by + 6, 0xB0FFFFFF);

        // controls only while chat is open (they're clickable then)
        if (controls.on()) { // always shown; clickable while chat (T) is open
            int cx = tx + tw / 2, cy = by + 9;
            boolean click = System.currentTimeMillis() - pressedAt < 160;
            Render2D.icon(c, "prev", cx - 19, cy - 3.5f, 7, click && pressed == 0 ? 0xFFFFFFFF : 0xCCFFFFFF);
            Render2D.disc(c, cx, cy, 5.5f, click && pressed == 1 ? 0xCCFFFFFF : 0xFFFFFFFF);
            Render2D.icon(c, playing ? "pause" : "play", cx - 3, cy - 3, 6, Render2D.withAlpha(base, 255));
            Render2D.icon(c, "next", cx + 12, cy - 3.5f, 7, click && pressed == 2 ? 0xFFFFFFFF : 0xCCFFFFFF);
        }
        String err = SpotifyLyrics.controlMessage();
        if (err != null) {
            Render2D.round(c, 4, H - 15, W - 8, 11, 5, 0xF0201018);
            small(c, fitSmall(err, W - 16), 8, H - 12, 0xFFFFB0B0);
        }
    }

    /** Small letter-spaced caps like "N O W  P L A Y I N G". */
    private static void spaced(DrawContext c, String s, float x, float y, int col) {
        float k = Render2D.pixelScale(SMALL), cx = x;
        Compat.push(c); Compat.translate(c, x, y); Compat.scale(c, k);
        float px = 0;
        for (int i = 0; i < s.length(); i++) {
            String ch = String.valueOf(s.charAt(i));
            Render2D.text(c, ch, px, 0, col, false);
            px += Render2D.width(ch) + 1.5f;
        }
        Compat.pop(c);
    }


    // ------------------------------------------------------------------ clicks while chat is open
    private boolean chatClick(double mx, double my, int button) {
        if (!isEnabled() || !controls.on() || button != 0 || !(mc.currentScreen instanceof ChatScreen) || !hasContent()) return false;
        double lx = (mx - lastX) / lastScale, ly = (my - lastY) / lastScale;
        if (lx < 0 || ly < 0 || lx > W || ly > H) return false;
        int tx = cover.on() ? PAD + COVER + 8 : 10, tw = W - 8 - tx, cx = tx + tw / 2, cy = BAR_Y + 9;
        int hit = -1;
        if (ly >= cy - 8 && ly <= cy + 8) {
            if (Math.abs(lx - (cx - 15)) <= 6) hit = 0;
            else if (Math.abs(lx - cx) <= 8) hit = 1;
            else if (Math.abs(lx - (cx + 15)) <= 6) hit = 2;
        }
        if (hit < 0) return true; // swallow clicks on the card itself
        pressed = hit;
        pressedAt = System.currentTimeMillis();
        SpotifyLyrics.control(hit == 0 ? "previous" : hit == 2 ? "next" : SpotifyLyrics.get().playing() ? "pause" : "play");
        return true;
    }

    // ------------------------------------------------------------------ drawing helpers
    private static final float SMALL = 0.75f;
    private static void small(DrawContext c, String s, float x, float y, int col) {
        Compat.push(c); Compat.translate(c, x, y); Compat.scale(c, Render2D.pixelScale(SMALL));
        Render2D.text(c, s, 0, 0, col, false);
        Compat.pop(c);
    }
    private static int smallWidth(String s) { return Math.round(Render2D.width(s) * Render2D.pixelScale(SMALL)); }
    private static String fitSmall(String s, int max) {
        if (smallWidth(s) <= max) return s;
        while (s.length() > 3 && smallWidth(s + "…") > max) s = s.substring(0, s.length() - 1);
        return s + "…";
    }
    private static String time(long ms) { long s = Math.max(0, ms) / 1000; return (s / 60) + ":" + String.format("%02d", s % 60); }
    private static String fit(String s, int max, boolean bold) { return Render2D.fit(s, max, bold); }
}
