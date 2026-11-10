package dev.orbit.client.friends;

import dev.orbit.client.cosmetics.NameSkins;
import dev.orbit.client.gui.Render2D;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

/** Small Orbit-style pop-ups in the top-right corner: a friend came online, an invite arrived. */
public final class FriendToasts {
    private FriendToasts() {}

    private record Toast(String face, String title, String sub, boolean invite, long born) {}
    private static final List<Toast> TOASTS = new ArrayList<>();
    private static final long LIFE = 5500;

    public static void show(String faceName, String title, String sub, boolean invite) {
        synchronized (TOASTS) {
            TOASTS.add(new Toast(faceName, title, sub, invite, System.currentTimeMillis()));
            while (TOASTS.size() > 3) TOASTS.remove(0);
        }
    }

    public static void render(DrawContext c, int sw) {
        List<Toast> list;
        synchronized (TOASTS) { TOASTS.removeIf(t -> System.currentTimeMillis() - t.born > LIFE); list = new ArrayList<>(TOASTS); }
        int y = 6;
        for (Toast t : list) {
            float age = (System.currentTimeMillis() - t.born) / 1000f;
            float in = Render2D.easeOut(age / 0.35f), out = Render2D.easeOut((LIFE / 1000f - age) / 0.35f);
            float a = Math.min(in, out);
            int w = Math.max(Render2D.boldWidth(t.title), Render2D.width(t.sub)) + 44, h = 30;
            // top centre (Minecraft's own toasts live in the top-right corner), sliding down into place
            int x = (sw - w) / 2;
            int yy = Math.round(y - (1 - a) * (h + 10));
            int acc = Render2D.accent();
            Render2D.round(c, x, yy, w, h, 9, Render2D.withAlpha(0x0B0818, (int) (225 * a)));
            Render2D.round(c, x, yy, 3, h, 1, Render2D.alpha(t.invite ? acc : 0xFF4ADE80, a));
            var skin = t.face != null ? NameSkins.get(t.face) : null;
            if (skin != null) {
                var tex = skin.body().texturePath();
                dev.orbit.client.compat.Compat.blitTint(c, tex, x + 9, yy + 7, 16, 16, 8, 8, 8, 8, 64, 64, Render2D.alpha(0xFFFFFFFF, a));
                dev.orbit.client.compat.Compat.blitTint(c, tex, x + 9, yy + 7, 16, 16, 40, 8, 8, 8, 64, 64, Render2D.alpha(0xFFFFFFFF, a));
            } else Render2D.disc(c, x + 17, yy + 15, 7, Render2D.alpha(acc, a * 0.6f));
            Render2D.bold(c, t.title, x + 32, yy + 6, Render2D.alpha(0xFFFFFFFF, a), false);
            Render2D.text(c, t.sub, x + 32, yy + 17, Render2D.alpha(Render2D.MUTED, a), false);
            y += h + 5;
        }
    }
}
