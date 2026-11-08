package dev.orbit.client.gui;

import dev.orbit.client.cosmetics.CosmeticsFeature;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.function.Supplier;

/**
 * 3D player for the title screen, drawn with Minecraft's real player renderer (no world needed):
 * skin, cape and Orbit hats/wings all show — so the Locker can preview them. Drag to spin.
 */
public final class PlayerPreview {
    public int x, y, w, h;
    /** Tiles: a fixed id with its own cosmetics (see CosmeticsFeature.OVERRIDES), a point to centre on and a zoom. */
    public int id = CosmeticsFeature.PREVIEW_ID;
    public float focusY = 0.9625f, zoom = 1f;
    public boolean animate = true;
    /** draw into an area larger than the box so wings / halos are not clipped (off for small tiles) */
    public boolean expand = true;
    private final Supplier<SkinTextures> skin;
    private float spin = -25, spinTarget = -25;
    /** keeps turning slowly on its own (main menu) */
    public boolean autoSpin;
    private long lastDrag, lastFrame = System.nanoTime();
    private double dragX = Double.NaN;
    private final long start = System.currentTimeMillis();

    public PlayerPreview(int x, int y, int w, int h, Supplier<SkinTextures> skin) { this.x = x; this.y = y; this.w = w; this.h = h; this.skin = skin; }

    public boolean contains(double mx, double my) { return mx >= x && my >= y && mx < x + w && my < y + h; }

    /** Turns the model to show its back (for capes / wings) or front. */
    public void showBack(boolean back) { spinTarget = back ? 160 : -25; lastDrag = 0; }
    public void setSpin(float deg) { spin = spinTarget = deg; }

    public void drag(double mx) {
        if (!Double.isNaN(dragX)) { spin += (float) (mx - dragX) * 2.2f; spinTarget = spin; }
        dragX = mx;
        lastDrag = System.currentTimeMillis();
    }
    public void release() { dragX = Double.NaN; }

    public void render(DrawContext c) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        if (autoSpin && System.currentTimeMillis() - lastDrag > 2500) spinTarget += dt * 22; // gentle turntable
        if (System.currentTimeMillis() - lastDrag > 400) spin = Render2D.approach(spin, spinTarget, 6, dt);

        SkinTextures tex = skin.get();
        if (tex == null) return;
        PlayerEntityRenderState s = new PlayerEntityRenderState();
        s.entityType = EntityType.PLAYER;
        s.skinTextures = tex;
        s.id = id;
        s.hatVisible = s.jacketVisible = s.leftPantsLegVisible = s.rightPantsLegVisible = s.leftSleeveVisible = s.rightSleeveVisible = true;
        s.capeVisible = true;
        s.width = 0.6f; s.height = 1.8f; s.standingEyeHeight = 1.62f;
        s.baseScale = 1; s.ageScale = 1;
        s.pose = EntityPose.STANDING;
        s.positionOffset = Vec3d.ZERO;
        s.light = 0xF000F0;
        s.age = animate ? (System.currentTimeMillis() - start) / 50f : 0; // idle arm sway
        s.bodyYaw = 180 + spin;
        s.relativeHeadYaw = 0;
        s.pitch = 0;
        s.field_53536 = 6; // cape hangs slightly away from the back
        float size = h / 2.15f * zoom;
        Quaternionf flip = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf tilt = new Quaternionf().rotateX((float) Math.toRadians(-6));
        flip.mul(tilt);
        // same model size and centre, bigger drawing area (the entity is clipped to this rectangle)
        int ex = expand ? Math.round(w * 0.75f) : 0, ey = expand ? Math.round(h * 0.12f) : 0;
        c.addEntity(s, size, new Vector3f(0, focusY, 0), flip, tilt, x - ex, y - ey, x + w + ex, y + h + ey);
    }
}
