package dev.orbit.client.emote;

import net.minecraft.client.model.ModelPart;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

/** Procedural emotes: each one sets head/body/limb rotations over time (seconds). */
public enum Emote {
    WAVE("Wave", false, 2.5f, Items.WHITE_BANNER, (p, t) -> { p.rightArm.pitch = -2.8f; p.rightArm.roll = 0.35f + 0.45f * sin(t * 9); p.head.yaw = 0.15f * sin(t * 2); }),
    CLAP("Clap", false, 2.4f, Items.NOTE_BLOCK, (p, t) -> { float c = 0.3f + 0.25f * Math.abs(sin(t * 8)); p.rightArm.pitch = -1.3f; p.leftArm.pitch = -1.3f; p.rightArm.yaw = -c; p.leftArm.yaw = c; }),
    CHEER("Cheer", false, 2.5f, Items.FIREWORK_ROCKET, (p, t) -> { float b = sin(t * 10) * 0.25f; p.rightArm.pitch = -2.9f + b; p.leftArm.pitch = -2.9f - b; p.rightArm.roll = 0.3f; p.leftArm.roll = -0.3f; p.head.pitch = -0.35f; }),
    SALUTE("Salute", false, 2.2f, Items.IRON_HELMET, (p, t) -> { p.rightArm.pitch = -2.2f; p.rightArm.yaw = -0.6f; p.rightArm.roll = -0.4f; p.head.pitch = -0.1f; }),
    POINT("Point", false, 2f, Items.ARROW, (p, t) -> { p.rightArm.pitch = -1.55f; p.rightArm.yaw = -0.1f; p.head.pitch = -0.05f; }),
    BOW("Bow", false, 2.2f, Items.LEATHER_HELMET, (p, t) -> { float b = Math.min(1f, t * 2) * (t > 1.6f ? Math.max(0, 1 - (t - 1.6f) * 2.5f) : 1); p.body.pitch = 0.6f * b; p.head.pitch = 0.6f * b; p.rightArm.pitch = 0.4f * b; p.leftArm.pitch = 0.4f * b; }),
    FACEPALM("Facepalm", false, 2.4f, Items.ROTTEN_FLESH, (p, t) -> { p.rightArm.pitch = -2.4f; p.rightArm.yaw = 0.55f; p.head.pitch = 0.35f; p.head.yaw = 0.1f * sin(t * 3); }),
    SHRUG("Shrug", false, 1.8f, Items.STRING, (p, t) -> { float s = Math.min(1, t * 3) * (t > 1.3f ? Math.max(0, 1 - (t - 1.3f) * 2) : 1); p.rightArm.roll = 0.7f * s; p.leftArm.roll = -0.7f * s; p.rightArm.pitch = -0.4f * s; p.leftArm.pitch = -0.4f * s; p.head.roll = 0.15f * s; }),
    // ---- store emotes
    FLOSS("Floss", true, 4f, Items.LIME_DYE, (p, t) -> { float s = sin(t * 8); p.body.yaw = 0.2f * s; p.rightArm.roll = 0.5f + 0.4f * s; p.leftArm.roll = -0.5f + 0.4f * s; p.rightArm.pitch = 0.3f * cos(t * 8); p.leftArm.pitch = -0.3f * cos(t * 8); p.rightLeg.roll = 0.08f * s; p.leftLeg.roll = 0.08f * s; }),
    DAB("Dab", true, 1.8f, Items.BLAZE_ROD, (p, t) -> { p.rightArm.pitch = -2.3f; p.rightArm.yaw = -0.4f; p.rightArm.roll = 0.6f; p.leftArm.pitch = -1.6f; p.leftArm.yaw = 0.9f; p.head.pitch = 0.6f; p.head.yaw = -0.6f; }),
    DISCO("Disco", true, 4f, Items.JUKEBOX, (p, t) -> { boolean up = ((int) (t * 2.5f)) % 2 == 0; p.rightArm.pitch = up ? -2.8f : 0.3f; p.rightArm.roll = up ? 0.4f : -0.2f; p.leftArm.pitch = 0.2f; p.leftArm.roll = -0.3f; p.body.yaw = 0.25f * sin(t * 5); p.rightLeg.pitch = 0.3f * sin(t * 10); p.leftLeg.pitch = -0.3f * sin(t * 10); }),
    WINDMILL("Windmill", true, 3f, Items.HOPPER, (p, t) -> { p.rightArm.pitch = t * 12; p.leftArm.pitch = t * 12 + 3.14f; p.head.pitch = 0.1f * sin(t * 12); }),
    ZOMBIE("Zombie", true, 4f, Items.ZOMBIE_HEAD, (p, t) -> { p.rightArm.pitch = -1.5f + 0.08f * sin(t * 4); p.leftArm.pitch = -1.5f - 0.08f * sin(t * 4); p.head.roll = 0.25f * sin(t * 1.5f); p.head.pitch = 0.2f; p.rightLeg.pitch = 0.4f * sin(t * 4); p.leftLeg.pitch = -0.4f * sin(t * 4); }),
    TPOSE("T-Pose", true, 3f, Items.ARMOR_STAND, (p, t) -> { p.rightArm.roll = 1.5708f; p.leftArm.roll = -1.5708f; p.rightArm.pitch = 0; p.leftArm.pitch = 0; p.head.pitch = 0; p.head.yaw = 0; }),
    // ---- emotes with props (drawn by cosmetics.Props)
    JOYRIDE("Joyride", true, 10f, Items.MINECART, (p, t) -> {
        // parked: press the start button (centre console), hold the wheel while it cranks, then enjoy the idle
        float press = Math.max(0, Math.min(1, Math.min((t - 0.05f) / 0.25f, (1.2f - t) / 0.3f)));
        float on = t > 1.45f ? 1 : 0;
        float vib = on * 0.015f * (float) Math.sin(t * 55);
        recline(p, 0.18f, 9); // sits low, under the roof
        p.rightLeg.pitch = -1.45f; p.leftLeg.pitch = -1.45f; p.rightLeg.yaw = 0.06f; p.leftLeg.yaw = -0.06f;
        p.rightArm.pitch = -1.25f + vib; p.rightArm.yaw = 0.25f;
        p.leftArm.pitch = -1.25f + press * 0.35f + vib; p.leftArm.yaw = -0.25f - press * 0.45f;   // reaching for the button
        float look = t > 1.45f ? 0.35f * (float) Math.sin((t - 1.45f) * 0.7f) : 0;              // then looks around, pleased
        p.head.yaw = -press * 0.35f + look; p.head.pitch = -0.1f + press * 0.35f;
    }),
    SUNBATHE("Sunbathe", true, 10f, Items.SUNFLOWER, (p, t) -> {
        recline(p, 0.95f, 7);
        p.rightLeg.pitch = -1.5708f; p.leftLeg.pitch = -1.15f; p.leftLeg.roll = -0.12f;
        p.rightArm.pitch = -3.3f; p.leftArm.pitch = -3.3f; p.rightArm.roll = -0.55f; p.leftArm.roll = 0.55f; // hands behind the head
        p.head.pitch -= 0.15f + 0.05f * (float) Math.sin(t * 0.8f); p.head.yaw = 0.2f * (float) Math.sin(t * 0.4f);
    }),
    CAMPFIRE("Campfire", true, 10f, Items.CAMPFIRE, (p, t) -> {
        recline(p, -0.25f, 6);
        p.rightLeg.pitch = -1.25f; p.leftLeg.pitch = -1.25f; p.rightLeg.yaw = 0.12f; p.leftLeg.yaw = -0.12f;
        p.rightArm.pitch = -1.15f + 0.05f * (float) Math.sin(t * 1.7f); p.rightArm.yaw = 0.15f; // holding the stick over the fire
        p.leftArm.pitch = -0.75f; p.leftArm.yaw = -0.2f;
        p.head.pitch += 0.4f; p.head.yaw = 0.08f * (float) Math.sin(t * 0.6f);
    }),
    DJ("DJ Set", true, 8f, Items.MUSIC_DISC_CAT, (p, t) -> {
        float bob = Math.abs((float) Math.sin(t * 4.6f));
        lower(p, bob * 1.2f);
        p.rightArm.pitch = -0.95f; p.rightArm.yaw = 0.2f + 0.3f * (float) Math.sin(t * 13); // scratching
        p.leftArm.pitch = -0.85f; p.leftArm.yaw = -0.15f;
        p.head.pitch = 0.25f + 0.2f * bob; p.head.yaw = 0.1f * (float) Math.sin(t * 2.3f);
        p.rightLeg.pitch = 0.1f * bob; p.leftLeg.pitch = -0.1f * bob;
    }),
    HOVERBOARD("Hoverboard", true, 8f, Items.PHANTOM_MEMBRANE, (p, t) -> {
        lower(p, -hoverLift(t));
        float sway = (float) Math.sin(t * 2.2f);
        p.rightLeg.roll = 0.3f; p.leftLeg.roll = -0.3f; p.rightLeg.pitch = 0.1f * sway; p.leftLeg.pitch = -0.1f * sway;
        p.rightArm.roll = 1.0f + 0.25f * sway; p.leftArm.roll = -1.0f + 0.25f * sway; p.rightArm.pitch = -0.2f; p.leftArm.pitch = -0.2f;
        p.body.yaw = 0.12f * sway; p.head.yaw = 0.25f * sway; p.head.pitch = 0.1f;
    }),
    MONEY("Money Flex", true, 8f, Items.EMERALD, (p, t) -> {
        float flick = Math.max(0, (float) Math.sin(t * 7)); // throwing bills over and over
        p.rightArm.pitch = -2.3f - 0.55f * flick; p.rightArm.yaw = -0.2f; p.rightArm.roll = 0.15f * flick;
        p.leftArm.pitch = -1.0f; p.leftArm.yaw = 0.25f;                                          // holding the stack
        p.head.pitch = -0.2f + 0.08f * (float) Math.sin(t * 3.5f); p.head.yaw = 0.25f * (float) Math.sin(t * 1.2f);
        p.body.yaw = 0.08f * (float) Math.sin(t * 3.5f);
        p.rightLeg.roll = 0.08f; p.leftLeg.roll = -0.08f;
    });

    /** how far the hoverboard floats (pixels) */
    public static float hoverLift(float t) { return 5 + 1.3f * (float) Math.sin(t * 3); }
    /** whether this emote comes with a prop */
    public boolean hasProp() { return ordinal() >= JOYRIDE.ordinal(); }

    /** moves the whole player model down (pixels; negative = up) */
    private static void lower(Parts p, float dy) {
        p.head.originY += dy; p.body.originY += dy; p.rightArm.originY += dy; p.leftArm.originY += dy; p.rightLeg.originY += dy; p.leftLeg.originY += dy;
    }
    /**
     * Leans the upper body back (radians, negative = forward) around the hips, as if sitting in a seat,
     * then lowers everything onto the seat.
     */
    private static void recline(Parts p, float a, float lowerBy) {
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        p.body.originY = 12 - 12 * c; p.body.originZ = 12 * s; p.body.pitch = -a;
        p.head.originY = 12 - 12 * c; p.head.originZ = 12 * s; p.head.pitch = -a * 0.7f;
        p.rightArm.originY = 12 - 10 * c; p.rightArm.originZ = 10 * s;
        p.leftArm.originY = 12 - 10 * c; p.leftArm.originZ = 10 * s;
        lower(p, lowerBy);
    }

    /** Head/body/limbs of a player model. */
    public record Parts(ModelPart head, ModelPart body, ModelPart rightArm, ModelPart leftArm, ModelPart rightLeg, ModelPart leftLeg) {}
    public interface Pose { void apply(Parts p, float t); }

    public final String title;
    public final boolean premium;
    public final float seconds;
    public final Item icon;
    private final Pose pose;

    Emote(String title, boolean premium, float seconds, Item icon, Pose pose) {
        this.title = title; this.premium = premium; this.seconds = seconds; this.icon = icon; this.pose = pose;
    }

    /** Applies the pose with a short blend in/out so emotes don't snap. */
    public void apply(Parts p, float t) {
        float blend = Math.min(1f, Math.min(t * 6f, (seconds - t) * 6f));
        if (blend <= 0) return;
        float[] before = snapshot(p);
        pose.apply(p, t);
        if (blend < 1f) mix(p, before, blend);
    }

    private static float[] snapshot(Parts p) {
        ModelPart[] a = {p.head(), p.body(), p.rightArm(), p.leftArm(), p.rightLeg(), p.leftLeg()};
        float[] s = new float[18];
        for (int i = 0; i < 6; i++) { s[i * 3] = a[i].pitch; s[i * 3 + 1] = a[i].yaw; s[i * 3 + 2] = a[i].roll; }
        return s;
    }
    private static void mix(Parts p, float[] b, float k) {
        ModelPart[] a = {p.head(), p.body(), p.rightArm(), p.leftArm(), p.rightLeg(), p.leftLeg()};
        for (int i = 0; i < 6; i++) {
            a[i].pitch = b[i * 3] + (a[i].pitch - b[i * 3]) * k;
            a[i].yaw = b[i * 3 + 1] + (a[i].yaw - b[i * 3 + 1]) * k;
            a[i].roll = b[i * 3 + 2] + (a[i].roll - b[i * 3 + 2]) * k;
        }
    }

    public static Emote byName(String n) { for (Emote e : values()) if (e.name().equalsIgnoreCase(n)) return e; return null; }
    private static float sin(float x) { return (float) Math.sin(x); }
    private static float cos(float x) { return (float) Math.cos(x); }
}
