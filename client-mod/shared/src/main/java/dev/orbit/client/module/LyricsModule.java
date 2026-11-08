package dev.orbit.client.module;

import dev.orbit.client.compat.Compat;
import dev.orbit.client.gui.Render2D;
import dev.orbit.client.module.setting.Setting;
import dev.orbit.client.spotify.SpotifyLyrics;
import net.minecraft.client.gui.DrawContext;

import java.util.List;

/**
 * Synced Spotify lyrics. "Sky" draws big smooth lines up in the sky that alternate left and
 * right, with a karaoke fill sweeping through the current line and the previous line fading
 * on the other side. "Card" is a compact box you can place with the HUD editor.
 */
public final class LyricsModule extends HudModule {
    private static final int W = 300;
    final Setting.Mode style = add(new Setting.Mode("style", "Style", "Sky", "Sky", "Card"));
    final Setting.Mode colors = (Setting.Mode) add(new Setting.Mode("colors", "Colours", "Glow", "Glow", "Gradient", "Accent", "White")).visibleWhen(() -> style.is("Sky"));
    final Setting.Slider size = (Setting.Slider) add(new Setting.Slider("skySize", "Lyric size", 1.0, 0.5, 2.0, 0.05, "x")).visibleWhen(() -> style.is("Sky"));
    final Setting.Mode position = (Setting.Mode) add(new Setting.Mode("skyPos", "Position", "Fixed in the sky", "Fixed in the sky", "On screen")).visibleWhen(() -> style.is("Sky"));
    final Setting.Slider height = (Setting.Slider) add(new Setting.Slider("skyHeight", "Height in the sky", 30, 10, 60, 1, "%")).visibleWhen(() -> style.is("Sky"));
    final Setting.Bool recentre = (Setting.Bool) add(new Setting.Bool("recentre", "Move in front of me after looking away 6s", true)).visibleWhen(() -> style.is("Sky") && position.is("Fixed in the sky"));
    final Setting.Bool karaoke = (Setting.Bool) add(new Setting.Bool("karaoke", "Karaoke fill", true)).visibleWhen(() -> style.is("Sky"));
    final Setting.Slider timing = add(new Setting.Slider("timing", "Timing (show lines earlier)", 250, -1000, 1500, 50, "ms"));
    final Setting.Bool songInfo = (Setting.Bool) add(new Setting.Bool("songInfo", "Show song & artist", true)).visibleWhen(() -> style.is("Card"));
    final Setting.Bool nextLine = (Setting.Bool) add(new Setting.Bool("nextLine", "Show next line", true)).visibleWhen(() -> style.is("Card"));
    final Setting.Bool progressBar = (Setting.Bool) add(new Setting.Bool("progress", "Song progress bar", true)).visibleWhen(() -> style.is("Card"));

    private int lastIndex = -2;
    private long changedAt;
    private String prevText = "";
    private int prevSide;

    public LyricsModule() {
        super("lyrics", "Spotify Lyrics", "Synced lyrics of what's playing, up in the sky", true, CENTER, 62);
        background.set(false);
    }

    private boolean sky() { return style.is("Sky"); }
    @Override protected boolean outerBackground() { return !sky(); }
    @Override public int contentWidth(boolean p) { return W; }
    @Override public int contentHeight(boolean p) { return (songInfo.on() ? 12 : 0) + 11 + (nextLine.on() ? 11 : 0) + (progressBar.on() ? 6 : 0); }
    @Override public boolean hasContent() { return isEnabled() && !SpotifyLyrics.get().title().isEmpty(); }

    // ------------------------------------------------------------------ shared lyric state
    private record State(String cur, String next, String prev, String older, float lineProgress, float songProgress, String title, boolean demo) {}

    private static String lineAt(List<SpotifyLyrics.Line> lines, int i) {
        if (i < 0 || i >= lines.size()) return "";
        String t = lines.get(i).text();
        return t.isEmpty() ? "♪" : t;
    }

    private State state() {
        SpotifyLyrics.leadMs = timing.i();
        SpotifyLyrics.Snapshot s = SpotifyLyrics.get();
        if (s.title().isEmpty()) return new State("Звёзды над нами", "", "Your lyrics appear here", "", (System.currentTimeMillis() % 3000) / 3000f, 0.4f, "Orbit Lyrics", true);
        List<SpotifyLyrics.Line> lines = s.lines();
        int i = s.index();
        if (i != lastIndex) {
            prevText = lastIndex >= 0 && lastIndex < lines.size() ? lines.get(lastIndex).text() : "";
            prevSide = Math.floorMod(lastIndex, 2);
            lastIndex = i;
            changedAt = System.currentTimeMillis();
        }
        String prev = "ok".equals(s.status()) ? lineAt(lines, i - 1) : "", older = "ok".equals(s.status()) ? lineAt(lines, i - 2) : "";
        String cur, next = "";
        float lp = 1;
        switch (s.status()) {
            case "loading" -> cur = "Searching the stars for lyrics…";
            case "nolyrics" -> cur = "♪ No synced lyrics for this song";
            default -> {
                cur = i < 0 || lines.get(i).text().isEmpty() ? "♪" : lines.get(i).text();
                next = i + 1 < lines.size() ? lines.get(i + 1).text() : "";
                if (i >= 0) {
                    long start = lines.get(i).timeMs();
                    long gap = i + 1 < lines.size() ? lines.get(i + 1).timeMs() - start : 4000;
                    long sung = Math.min(gap, 500 + cur.length() * 95L); // roughly how long the line is sung
                    lp = Math.max(0, Math.min(1, (s.position() + SpotifyLyrics.leadMs - start) / (float) Math.max(1, sung)));
                }
            }
        }
        float sp = s.durationMs() > 0 ? Math.min(1f, s.position() / (float) s.durationMs()) : 0;
        return new State(cur, next, prev, older, lp, sp, s.title() + "  ·  " + s.artist(), false);
    }

    // ------------------------------------------------------------------ sky
    /*
     * Two fixed spots in the sky, left and right. Line i goes to side i % 2 and is bright with the
     * karaoke sweep; the line before it stays on the other side (dimmer) until it is replaced, so
     * there is always one line on each side. Long lines wrap onto two rows instead of stretching.
     */
    @Override
    public void render(DrawContext c, int sw, int sh, boolean preview) {
        if (!sky()) { super.render(c, sw, sh, preview); return; }
        if (!preview && !hasContent()) return;
        State st = state();
        float k = 1.15f * size.f() * Math.max(0.6f, sh / 330f);
        float t = Render2D.easeOut((System.currentTimeMillis() - changedAt) / 450f);
        int side = Math.floorMod(lastIndex, 2);
        float baseY = sh * height.f() / 100f;
        float sung = 0.62f; // brightness of the line that was just sung

        float[][] spot = new float[2][];
        if (position.is("On screen")) {
            spot[0] = new float[]{sw * 0.24f, baseY};
            spot[1] = new float[]{sw * 0.76f, baseY};
        } else {
            anchor(st);
            float elev = height.f() * 0.8f;
            // spread the two lines to ~29% / 71% of the screen width at the current FOV (like the reference)
            double focal = (sh / 2.0) / Math.tan(Math.toRadians(mc.options.getFov().getValue()) / 2);
            float off = (float) Math.toDegrees(Math.atan(0.21 * sw / focal));
            spot[0] = project(anchorYaw - off, -elev, sw, sh);
            spot[1] = project(anchorYaw + off, -elev, sw, sh);
            boolean visible = spot[0] != null || spot[1] != null;
            if (visible) lastSeen = System.currentTimeMillis();
        }
        int other = 1 - side;
        if (!st.prev().isEmpty() && spot[other] != null) skyLine(c, st.prev(), sw, spot[other][0], spot[other][1], k, Render2D.lerp(1f, sung, t), 1f, false);
        if (spot[side] == null) return;
        // the line that used to be on this side fades out while the new one fades in
        if (!st.older().isEmpty() && t < 1) skyLine(c, st.older(), sw, spot[side][0], spot[side][1], k, sung * (1 - t), 1f, false);
        skyLine(c, st.cur(), sw, spot[side][0], spot[side][1] + (1 - t) * 5, k, Math.max(0.04f, t), karaoke.on() ? st.lineProgress() : 1f, true);
    }

    // ------------------------------------------------------------------ fixed-in-the-sky placement
    private float anchorYaw = Float.NaN;
    private String anchorSong = "";
    private long lastSeen = System.currentTimeMillis();

    /** Picks the sky direction the lyrics live in: where you look when a song starts (or after looking away a while). */
    private void anchor(State st) {
        var cam = mc.gameRenderer == null ? null : mc.gameRenderer.getCamera();
        if (cam == null) return;
        long now = System.currentTimeMillis();
        boolean lost = recentre.on() && now - lastSeen > 6000;
        if (Float.isNaN(anchorYaw) || !st.title().equals(anchorSong) || lost) {
            anchorYaw = cam.getYaw();
            anchorSong = st.title();
            lastSeen = now;
        }
    }

    /** Screen position of a direction (yaw/pitch in degrees) for the current camera, or null if behind/off screen. */
    private float[] project(float yaw, float pitch, int sw, int sh) {
        var cam = mc.gameRenderer == null ? null : mc.gameRenderer.getCamera();
        if (cam == null) return null;
        double cy = Math.toRadians(cam.getYaw()), cp = Math.toRadians(cam.getPitch());
        double ty = Math.toRadians(yaw), tp = Math.toRadians(pitch);
        // target direction
        double dx = -Math.sin(ty) * Math.cos(tp), dy = -Math.sin(tp), dz = Math.cos(ty) * Math.cos(tp);
        // camera basis: forward, right, up
        double fx = -Math.sin(cy) * Math.cos(cp), fy = -Math.sin(cp), fz = Math.cos(cy) * Math.cos(cp);
        double rx = -Math.cos(cy), rz = -Math.sin(cy);
        double ux = ry0(rz, fy), uy = rz * fx - rx * fz, uz = rx * fy;
        double depth = dx * fx + dy * fy + dz * fz;
        if (depth < 0.15) return null;
        double fov = Math.toRadians(mc.options.getFov().getValue());
        double f = (sh / 2.0) / Math.tan(fov / 2);
        float x = (float) (sw / 2.0 + (dx * rx + dz * rz) / depth * f);
        float y = (float) (sh / 2.0 - (dx * ux + dy * uy + dz * uz) / depth * f);
        if (x < -sw * 0.5f || x > sw * 1.5f || y < -sh * 0.5f || y > sh * 1.5f) return null;
        return new float[]{x, y};
    }
    // up = right x forward (right has no y component)
    private static double ry0(double rz, double fy) { return -rz * fy; }

    /** Splits a lyric into at most two rows that fit maxW (text units); returns 1 row if it fits. */
    private static List<String> wrap(String s, int maxW) {
        if (Render2D.displayWidth(s) <= maxW) return List.of(s);
        String[] words = s.split(" ");
        int best = -1, bestDiff = Integer.MAX_VALUE;
        for (int i = 1; i < words.length; i++) { // split point with the most even halves
            String a = String.join(" ", java.util.Arrays.copyOfRange(words, 0, i)), b = String.join(" ", java.util.Arrays.copyOfRange(words, i, words.length));
            int diff = Math.abs(Render2D.displayWidth(a) - Render2D.displayWidth(b));
            if (diff < bestDiff) { bestDiff = diff; best = i; }
        }
        if (best < 0) return List.of(s);
        return List.of(String.join(" ", java.util.Arrays.copyOfRange(words, 0, best)), String.join(" ", java.util.Arrays.copyOfRange(words, best, words.length)));
    }

    /** One lyric (1–2 rows) on the left (0) or right (1) of the sky, with glow and karaoke fill. */
    private void skyLine(DrawContext c, String s, int sw, float cx, float y, float k, float alpha, float fill, boolean current) {
        int maxW = (int) (sw * 0.34f / k);
        List<String> rows = wrap(s, maxW);
        int widest = 0;
        for (String r : rows) widest = Math.max(widest, Render2D.displayWidth(r));
        float kk = Render2D.pixelScale(widest > maxW ? k * maxW / (float) widest : k);

        // not sung yet: lavender (white in White mode); already sung: white
        int dim = colors.is("White") ? Render2D.withAlpha(0xFFFFFF, 130) : Render2D.lerpColor(Render2D.accent(), 0xFFFFFFFF, 0.4f);
        int glow = colors.is("White") ? 0xFFFFFFFF : Render2D.accent();
        float total = 0;
        for (String r : rows) total += Render2D.displayWidth(r);
        float edge = total * fill, done = 0;
        for (int ri = 0; ri < rows.size(); ri++) {
            String row = rows.get(ri);
            int w = Render2D.displayWidth(row);
            float x = cx - w * kk / 2f;
            if (position.is("On screen")) x = Math.max(6, Math.min(sw - 6 - w * kk, x));
            Compat.push(c);
            Compat.translate(c, Render2D.snap(x), Render2D.snap(y + ri * 11 * kk));
            Compat.scale(c, kk);
            if (current && !colors.is("White")) { // soft glow behind the current line
                int g = Render2D.alpha(glow, 0.09f * alpha);
                for (int i = 0; i < 6; i++) {
                    double an = i * Math.PI / 3;
                    Render2D.display(c, row, (float) Math.cos(an), (float) Math.sin(an), g, false);
                }
            }
            if (fill >= 1 || edge >= done + w) drawColored(c, row, alpha, done, total);
            else if (edge <= done) Render2D.display(c, row, 0, 0, Render2D.alpha(dim, alpha), false);
            else { // the sweep is inside this row: light it character by character
                float cxp = 0;
                for (int i = 0; i < row.length(); ) {
                    int n = Character.charCount(row.codePointAt(i));
                    String ch = row.substring(i, i + n);
                    int cw = Render2D.displayWidth(ch);
                    float f = Math.max(0, Math.min(1, (edge - done - cxp) / Math.max(1, cw)));
                    Render2D.display(c, ch, cxp, 0, Render2D.alpha(Render2D.lerpColor(dim, colorAt((done + cxp) / Math.max(1, total)), f), alpha), false);
                    cxp += cw;
                    i += n;
                }
            }
            Compat.pop(c);
            done += w;
        }
    }

    private void drawColored(DrawContext c, String s, float alpha, float offset, float total) {
        if (!colors.is("Gradient")) { Render2D.display(c, s, 0, 0, Render2D.alpha(litColor(), alpha), false); return; }
        float x = 0;
        for (int i = 0; i < s.length(); ) {
            int n = Character.charCount(s.codePointAt(i));
            String ch = s.substring(i, i + n);
            Render2D.display(c, ch, x, 0, Render2D.alpha(colorAt((offset + x) / Math.max(1, total)), alpha), false);
            x += Render2D.displayWidth(ch);
            i += n;
        }
    }


    private int litColor() {
        return switch (colors.get()) {
            case "Accent" -> Render2D.lerpColor(Render2D.accent(), 0xFFFFFFFF, 0.25f);
            case "Gradient" -> Render2D.lerpColor(Render2D.accent(), 0xFFFFFFFF, 0.45f);
            default -> 0xFFFFFFFF;
        };
    }
    /** Lit colour at a position along the line (gradient mode sweeps from light lavender to the accent). */
    private int colorAt(float f) {
        if (!colors.is("Gradient")) return litColor();
        return Render2D.lerpColor(Render2D.lerpColor(Render2D.accent(), 0xFFFFFFFF, 0.7f), Render2D.lerpColor(Render2D.accent(), Render2D.accent2(), 0.4f), f);
    }

    // ------------------------------------------------------------------ card
    @Override
    protected void renderContent(DrawContext c, boolean preview) {
        State st = state();
        int acc = Render2D.accent();
        int y = 0;
        if (songInfo.on()) {
            String ti = fit(st.title(), W);
            Render2D.text(c, ti, (W - Render2D.width(ti)) / 2f, y, Render2D.withAlpha(0xC8C0E8, 200), shadow.on());
            y += 12;
        }
        float t = Render2D.easeOut((System.currentTimeMillis() - changedAt) / 260f);
        int lineColor = chroma.on() ? 0xFFFFFFFF : textColor.argb();
        if (t < 1 && !prevText.isEmpty()) {
            String p = fit(prevText, W);
            Render2D.bold(c, p, (W - Render2D.boldWidth(p)) / 2f, y - 6 * t, Render2D.alpha(lineColor, 1 - t), shadow.on());
        }
        String cl = fit(st.cur(), W);
        Render2D.bold(c, cl, (W - Render2D.boldWidth(cl)) / 2f, y + 6 * (1 - t), Render2D.alpha(lineColor, Math.max(0.05f, t)), shadow.on());
        y += 11;
        if (nextLine.on() && !st.next().isEmpty()) {
            String nl = fit(st.next(), W);
            Render2D.text(c, nl, (W - Render2D.width(nl)) / 2f, y, Render2D.withAlpha(0xFFFFFF, 120), shadow.on());
        }
        if (nextLine.on()) y += 11;
        if (progressBar.on()) {
            int bw = 120, bx = (W - bw) / 2;
            Render2D.round(c, bx, y + 2, bw, 2, 1, Render2D.withAlpha(0xFFFFFF, 50));
            Render2D.round(c, bx, y + 2, Math.max(2, Math.round(bw * st.songProgress())), 2, 1, acc);
        }
    }

    private static String fit(String s, int max) {
        if (Render2D.boldWidth(s) <= max) return s;
        while (s.length() > 3 && Render2D.boldWidth(s + "…") > max) s = s.substring(0, s.length() - 1);
        return s + "…";
    }
}
