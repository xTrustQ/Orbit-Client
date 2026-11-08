package dev.orbit.client.module;

import dev.orbit.client.compat.Compat;
import com.google.gson.JsonObject;
import dev.orbit.client.gui.Render2D;
import dev.orbit.client.module.setting.Setting;
import net.minecraft.client.gui.DrawContext;

import java.util.List;

/**
 * A draggable on-screen element. Position is stored as a fraction of the free screen space,
 * so layouts survive resolution and GUI-scale changes.
 */
public abstract class HudModule extends Module {
    /** Use for a default X that centres the element horizontally. */
    public static final int CENTER = Integer.MIN_VALUE;
    public double x = Double.NaN, y = Double.NaN;
    private final int defPx, defPy;

    public final Setting.Slider scale = add(new Setting.Slider("scale", "Size", 1.0, 0.5, 3.0, 0.05, "x"));
    public final Setting.Color textColor = add(new Setting.Color("textColor", "Text color", 0xFFFFFFFF));
    public final Setting.Bool chroma = add(new Setting.Bool("chroma", "Rainbow text", false));
    public final Setting.Bool shadow = add(new Setting.Bool("shadow", "Text shadow", true));
    public final Setting.Bool background = add(new Setting.Bool("background", "Background", true));
    public final Setting.Color bgColor = (Setting.Color) add(new Setting.Color("bgColor", "Background color", 0xFF000000)).visibleWhen(this::bg);
    public final Setting.Slider bgOpacity = (Setting.Slider) add(new Setting.Slider("bgOpacity", "Background opacity", 45, 0, 100, 1, "%")).visibleWhen(this::bg);
    public final Setting.Bool rounded = (Setting.Bool) add(new Setting.Bool("rounded", "Rounded corners", true)).visibleWhen(this::bg);
    public final Setting.Bool border = (Setting.Bool) add(new Setting.Bool("border", "Accent border", false)).visibleWhen(this::bg);
    public final Setting.Mode brackets = add(new Setting.Mode("brackets", "Brackets", "None", "None", "[ ]", "( )"));

    private boolean bg() { return background.on(); }

    /** Default position in GUI pixels: negative = from the right/bottom edge, {@link #CENTER} = centred. */
    protected HudModule(String id, String name, String desc, boolean on, int defPx, int defPy) {
        super(id, name, desc, Category.HUD, on);
        this.defPx = defPx;
        this.defPy = defPy;
    }

    private void resolveDefault(int sw, int sh, boolean preview) {
        double w = width(preview) * scaleF(), h = height(preview) * scaleF();
        double px = defPx == CENTER ? (sw - w) / 2 : defPx >= 0 ? defPx : sw - w + defPx;
        double py = defPy >= 0 ? defPy : sh - h + defPy;
        setScreenPos(px, py, sw, sh, preview);
    }

    /** Text lines for simple text elements. Override {@link #contentWidth}/{@link #renderContent} for custom ones. */
    protected List<String> lines(boolean preview) { return List.of(); }

    protected String wrap(String s) {
        return switch (brackets.get()) { case "[ ]" -> "[" + s + "]"; case "( )" -> "(" + s + ")"; default -> s; };
    }

    /** Whether the Background options paint a box behind the whole element. */
    protected boolean outerBackground() { return true; }

    protected int pad() { return background.on() ? 4 : 1; }

    public int contentWidth(boolean preview) {
        int w = 0;
        for (String l : lines(preview)) w = Math.max(w, Render2D.width(wrap(l)));
        return w;
    }
    public int contentHeight(boolean preview) {
        int n = lines(preview).size();
        return n == 0 ? 0 : n * 10 - 1;
    }

    public int width(boolean preview) { return contentWidth(preview) + pad() * 2; }
    public int height(boolean preview) { return contentHeight(preview) + pad() * 2; }

    /** Should this element draw at all right now (e.g. Potions with no effects)? */
    public boolean hasContent() { return contentHeight(false) > 0; }

    protected void renderContent(DrawContext c, boolean preview) {
        int yy = 0;
        int w = contentWidth(preview);
        for (String l : lines(preview)) {
            String s = wrap(l);
            Render2D.chromaText(c, s, (w - Render2D.width(s)) / 2f, yy, textColor.argb(), chroma.on(), shadow.on());
            yy += 10;
        }
    }

    // ------------------------------------------------------------- positioning
    public float scaleF() { return dev.orbit.client.gui.Render2D.pixelScale(scale.f()); }
    public int screenX(int sw, boolean preview) {
        if (Double.isNaN(x)) resolveDefault(sw, Math.max(1, net.minecraft.client.MinecraftClient.getInstance().getWindow().getScaledHeight()), preview); return (int) Math.round(x * Math.max(0, sw - width(preview) * scaleF())); }
    public int screenY(int sh, boolean preview) {
        if (Double.isNaN(y)) resolveDefault(net.minecraft.client.MinecraftClient.getInstance().getWindow().getScaledWidth(), sh, preview); return (int) Math.round(y * Math.max(0, sh - height(preview) * scaleF())); }
    public void setScreenPos(double px, double py, int sw, int sh, boolean preview) {
        double fw = Math.max(1, sw - width(preview) * scaleF()), fh = Math.max(1, sh - height(preview) * scaleF());
        x = Math.max(0, Math.min(1, px / fw));
        y = Math.max(0, Math.min(1, py / fh));
    }
    public void resetPosition() { x = Double.NaN; y = Double.NaN; }

    public void render(DrawContext c, int sw, int sh, boolean preview) {
        if (!preview && !hasContent()) return;
        int w = width(preview), h = height(preview);
        Compat.push(c);
        Compat.translate(c, screenX(sw, preview), screenY(sh, preview));
        Compat.scale(c, scaleF());
        if (background.on() && outerBackground()) {
            int col = Render2D.withAlpha(bgColor.rgb(), (int) (bgOpacity.get() * 2.55));
            int r = rounded.on() ? 3 : 0;
            if (border.on()) {
                Render2D.round(c, 0, 0, w, h, r, Render2D.alpha(Render2D.accent(), 0.9f));
                Render2D.round(c, 1, 1, w - 2, h - 2, Math.max(0, r - 1), col);
            } else Render2D.round(c, 0, 0, w, h, r, col);
        }
        Compat.translate(c, pad(), pad());
        renderContent(c, preview);
        Compat.pop(c);
    }

    @Override public JsonObject save() {
        JsonObject o = super.save();
        if (!Double.isNaN(x)) o.addProperty("x", x);
        if (!Double.isNaN(y)) o.addProperty("y", y);
        return o;
    }
    @Override public void load(JsonObject o) {
        super.load(o);
        if (o.has("x")) x = o.get("x").getAsDouble();
        if (o.has("y")) y = o.get("y").getAsDouble();
    }
}
