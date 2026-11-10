package dev.orbit.client.cosmetics;

import dev.orbit.client.cosmetics.CosmeticsFeature.Mesh;

import java.util.List;

/**
 * Orbit pets: little 3D animals built in code. Pets that can't fly sit on your left shoulder; flying pets hover
 * next to and above it. Units are pixels (1/16 block), -Y is up and the pet looks towards -Z (your front).
 * Shoulder pets stand with their feet at y = 0; flying pets are centred on the origin.
 */
final class Pets {
    private Pets() {}

    static final String[] FLYING = {"Parrot", "Bee", "Owl", "Bat", "Baby Dragon", "Ghost", "Phoenix", "Fairy", "UFO", "Jellyfish"};
    static boolean flies(String pet) { for (String f : FLYING) if (f.equals(pet)) return true; return false; }

    /** where the pet is in body space: {x, y, z, yaw} */
    static float[] place(String pet, float t) {
        if (!flies(pet)) { // sits on the shoulder and slowly turns to look around
            float hop = pet.equals("Frog") ? hop(t) : 0;
            float look = (float) (Math.sin(t * 0.35f) * 0.35f + Math.sin(t * 0.9f) * 0.08f);
            return new float[]{6.3f, -hop, 0.2f, look};
        }
        float slow = pet.equals("Ghost") || pet.equals("Jellyfish") || pet.equals("UFO") ? 0.7f : 1f;
        float x = 10.5f + (float) Math.sin(t * 0.55f) * 1.4f;
        float y = -7.5f + (float) Math.sin(t * 1.9f * slow) * 1.1f;
        float z = 1.2f + (float) Math.cos(t * 0.55f) * 2.0f;
        return new float[]{x, y, z, (float) Math.sin(t * 0.55f) * 0.35f};
    }
    /** a little jump every few seconds (pixels up) */
    private static float hop(float t) { float p = t % 3.2f; return p < 0.45f ? (float) Math.sin(p / 0.45f * Math.PI) * 2.2f : 0; }

    private static boolean blink(float t, float offset) { return (t + offset) % 4.3f < 0.13f; }

    // =====================================================================================================
    static void lit(Mesh m, String pet, float t) {
        switch (pet) {
            case "Cat" -> cat(m, t, 0xFFE8913A, 0xFFF6D3A8, 0xFFC0661C, 0xFF7AD14A, false);
            case "Fox" -> fox(m, t);
            case "Puppy" -> puppy(m, t);
            case "Bunny" -> bunny(m, t);
            case "Panda" -> panda(m, t);
            case "Penguin" -> penguin(m, t);
            case "Frog" -> frog(m, t);
            case "Axolotl" -> axolotl(m, t);
            case "Duck" -> duck(m, t);
            case "Parrot" -> parrot(m, t);
            case "Bee" -> bee(m, t);
            case "Owl" -> owl(m, t);
            case "Bat" -> bat(m, t);
            case "Baby Dragon" -> dragon(m, t);
            case "Ghost" -> ghostEyes(m, t);
            case "UFO" -> ufo(m, t);
            default -> {}
        }
    }
    static void glow(Mesh m, String pet, float t) {
        switch (pet) {
            case "Bat" -> { m.box(-0.75f, -2.2f, -1.25f, -0.25f, -1.75f, -1.18f, 0xFFFF3B3B); m.box(0.25f, -2.2f, -1.25f, 0.75f, -1.75f, -1.18f, 0xFFFF3B3B); }
            case "Baby Dragon" -> { m.box(-1.25f, -3.1f, -2.0f, -0.6f, -2.5f, -1.4f, 0xFFFFD84A); m.box(0.6f, -3.1f, -2.0f, 1.25f, -2.5f, -1.4f, 0xFFFFD84A); dragonFlame(m, t); }
            case "Ghost" -> ghost(m, t);
            case "Phoenix" -> phoenix(m, t);
            case "Fairy" -> fairy(m, t);
            case "UFO" -> ufoLights(m, t);
            case "Jellyfish" -> jelly(m, t);
            case "Bee" -> beeWings(m, t);
            default -> {}
        }
    }

    // ------------------------------------------------------------------ helpers
    /** two eyes on the front face (z), centred at ±dx, top y0; closed for a moment now and then */
    private static void eyes(Mesh m, float dx, float y0, float z, float size, int iris, int pupil, float t) {
        boolean shut = blink(t, dx);
        for (int s = -1; s <= 1; s += 2) {
            float x = s * dx;
            if (shut) { m.box(x - size / 2, y0 + size * 0.45f, z - 0.06f, x + size / 2, y0 + size * 0.6f, z, 0xFF1A1A1A); continue; }
            m.box(x - size / 2, y0, z - 0.05f, x + size / 2, y0 + size, z, iris);
            if (pupil != 0) m.box(x - size * 0.22f, y0 + size * 0.15f, z - 0.09f, x + size * 0.22f, y0 + size * 0.85f, z - 0.04f, pupil);
            sparkle(m, x - size * 0.18f, y0 + size * 0.12f, z - 0.12f, size * 0.32f);
        }
    }
    /** the little white shine that makes eyes look alive */
    private static void sparkle(Mesh m, float x, float y, float z, float s) { m.box(x - s / 2, y, z - 0.03f, x + s / 2, y + s, z, 0xFFFFFFFF); }
    /** soft pink cheeks */
    private static void blush(Mesh m, float dx, float y, float z, float w) {
        for (int s = -1; s <= 1; s += 2) m.box(s * dx - w / 2, y, z - 0.07f, s * dx + w / 2, y + w * 0.45f, z - 0.02f, 0x99FF8FA8);
    }
    /** a paw seen from the front with pale toe beans */
    private static void paw(Mesh m, float x0, float x1, float z, int fur, int bean) {
        m.box(x0, -1.3f, z - 0.9f, x1, 0, z, fur);
        float w = (x1 - x0) / 3;
        for (int k = 0; k < 3; k++) m.box(x0 + k * w + w * 0.2f, -0.35f, z - 0.98f, x0 + (k + 1) * w - w * 0.2f, -0.12f, z - 0.88f, bean);
    }
    /** a flapping wing (box) on one side: pivot at the body's edge, sweeping up and down around Z */
    private static void wing(Mesh m, int side, float px, float py, float pz, float angle, float len, float h, float z0, float z1, int col) {
        float x0 = side > 0 ? 0 : -len, x1 = side > 0 ? len : 0;
        m.boxR(side * px, py, pz, 0, 0, -side * angle, x0, -0.15f, z0, x1, 0.15f, z1, col);
        m.boxR(side * px, py, pz, 0, 0, -side * angle, side > 0 ? 0 : -len * 0.7f, 0.15f, z0 + 0.3f, side > 0 ? len * 0.7f : 0, h, z1 - 0.2f, col);
    }
    private static void tail(Mesh m, float[][] pts, float[] radii, int c0, int c1) { m.tube(List.of(pts), radii, 8, c0, c1); }

    // ------------------------------------------------------------------ shoulder pets
    private static void cat(Mesh m, float t, int fur, int light, int dark, int eye, boolean fox) {
        m.box(-1.4f, -3.2f, -0.6f, 1.4f, 0, 2.2f, fur);                       // body (sitting)
        m.box(-0.9f, -2.8f, -0.75f, 0.9f, -0.4f, -0.55f, light);             // chest
        for (int k = 0; k < 3; k++) m.box(-1.45f, -2.9f + k * 0.9f, 0.4f + k * 0.4f, 1.45f, -2.55f + k * 0.9f, 1.0f + k * 0.4f, dark); // back stripes
        m.box(-1.6f, -1.6f, 0.4f, -1.4f, -0.2f, 2.0f, fur); m.box(1.4f, -1.6f, 0.4f, 1.6f, -0.2f, 2.0f, fur);                          // haunches
        paw(m, -1.2f, -0.35f, -0.4f, fur, 0xFFFFB3C2); paw(m, 0.35f, 1.2f, -0.4f, fur, 0xFFFFB3C2);                                 // front paws
        float tilt = (float) Math.sin(t * 0.7f) * 0.12f;
        m.boxR(0, -3.0f, 0, 0, 0, tilt, -1.0f, -1.6f, -1.97f, -0.4f, -1.3f, -1.9f, 0x99FF8FA8);   // blush
        m.boxR(0, -3.0f, 0, 0, 0, tilt, 0.4f, -1.6f, -1.97f, 1.0f, -1.3f, -1.9f, 0x99FF8FA8);
        for (int s = -1; s <= 1; s += 2) for (int k = 0; k < 2; k++)                         // whiskers
            m.boxR(0, -3.0f, 0, 0, 0, tilt, s > 0 ? 0.8f : -2.6f, -0.9f + k * 0.35f, -2.0f, s > 0 ? 2.6f : -0.8f, -0.82f + k * 0.35f, -1.96f, 0xFFF4F0EA);
        m.boxR(0, -3.0f, 0, 0, 0, tilt, -1.8f, -3.0f, -1.9f, 1.8f, 0, 0.9f, fur);   // head
        m.boxR(0, -3.0f, 0, 0, 0, tilt, -0.8f, -1.2f, -2.2f, 0.8f, -0.2f, -1.85f, light);  // muzzle
        m.boxR(0, -3.0f, 0, 0, 0, tilt, -0.25f, -1.3f, -2.3f, 0.25f, -0.95f, -2.15f, 0xFFFF8FA8); // nose
        m.boxR(0, -3.0f, 0, 0, 0, tilt, -0.3f, -3.05f, -1.5f, 0.3f, -2.98f, 0.5f, dark); // stripe
        for (int s = -1; s <= 1; s += 2) {
            m.boxR(0, -3.0f, 0, 0, 0, tilt, s > 0 ? 0.7f : -1.7f, -3.9f, -0.6f, s > 0 ? 1.7f : -0.7f, -3.0f, 0.2f, fur);   // ears
            m.boxR(0, -3.0f, 0, 0, 0, tilt, s > 0 ? 0.95f : -1.45f, -3.6f, -0.65f, s > 0 ? 1.45f : -0.95f, -3.05f, -0.6f, 0xFFFFB3C2);
            boolean shut = blink(t, 0);
            if (shut) m.boxR(0, -3.0f, 0, 0, 0, tilt, s * 0.9f - 0.35f, -1.9f, -1.97f, s * 0.9f + 0.35f, -1.75f, -1.9f, 0xFF1A1A1A);
            else {
                m.boxR(0, -3.0f, 0, 0, 0, tilt, s * 0.9f - 0.4f, -2.3f, -1.96f, s * 0.9f + 0.4f, -1.45f, -1.9f, eye);
                m.boxR(0, -3.0f, 0, 0, 0, tilt, s * 0.9f - 0.13f, -2.25f, -2.0f, s * 0.9f + 0.13f, -1.5f, -1.95f, 0xFF141414);
                m.boxR(0, -3.0f, 0, 0, 0, tilt, s * 0.9f - 0.35f, -2.25f, -2.04f, s * 0.9f - 0.12f, -2.0f, -1.99f, 0xFFFFFFFF); // shine
            }
        }
        float sway = (float) Math.sin(t * 1.8f) * 0.9f;
        tail(m, new float[][]{{0, -0.4f, 2.1f}, {0.3f, -0.6f, 3.3f}, {sway * 0.6f, -2.2f, 3.9f}, {sway, -3.9f, 3.5f}}, new float[]{0.5f, 0.45f, 0.42f, 0.3f}, fur, dark);
    }

    private static void fox(Mesh m, float t) {
        int fur = 0xFFE5772E, white = 0xFFF8F1E6, black = 0xFF2A1E18;
        m.box(-1.4f, -3.2f, -0.6f, 1.4f, 0, 2.3f, fur);
        m.box(-0.9f, -2.9f, -0.75f, 0.9f, -0.5f, -0.55f, white);
        m.box(-1.2f, -1.3f, -1.3f, -0.35f, 0, -0.4f, black); m.box(0.35f, -1.3f, -1.3f, 1.2f, 0, -0.4f, black);
        m.box(-1.9f, -6.0f, -1.7f, 1.9f, -3.0f, 1.0f, fur);                    // head
        m.box(-1.9f, -4.0f, -1.75f, -0.9f, -3.0f, -1.65f, white); m.box(0.9f, -4.0f, -1.75f, 1.9f, -3.0f, -1.65f, white); // cheeks
        m.box(-0.75f, -4.3f, -2.9f, 0.75f, -3.2f, -1.7f, white);              // snout
        m.box(-0.3f, -4.35f, -3.0f, 0.3f, -3.95f, -2.85f, black);             // nose
        for (int s = -1; s <= 1; s += 2) {                                    // pointy ears with black tips
            m.box(s > 0 ? 0.6f : -1.8f, -7.2f, -0.5f, s > 0 ? 1.8f : -0.6f, -6.0f, 0.3f, fur);
            m.box(s > 0 ? 0.85f : -1.55f, -7.8f, -0.4f, s > 0 ? 1.55f : -0.85f, -7.2f, 0.2f, black);
        }
        eyes(m, 0.95f, -5.3f, -1.72f, 0.7f, 0xFF3A2410, 0xFF141414, t);
        m.box(-1.3f, -1.1f, -1.35f, -0.4f, 0, -0.45f, 0xFF2A1E18); m.box(0.4f, -1.1f, -1.35f, 1.3f, 0, -0.45f, 0xFF2A1E18); // dark socks
        float sway = (float) Math.sin(t * 1.5f) * 1.1f;
        tail(m, new float[][]{{0, -0.6f, 2.2f}, {0.5f, -0.8f, 3.8f}, {sway, -1.6f, 5.0f}, {sway * 1.2f, -2.8f, 5.6f}, {sway * 1.3f, -3.6f, 5.5f}},
                new float[]{0.6f, 1.05f, 1.15f, 0.9f, 0.15f}, fur, white);
    }

    private static void puppy(Mesh m, float t) {
        int fur = 0xFFB27A47, light = 0xFFEACDA2, dark = 0xFF6E4524;
        m.box(-1.5f, -3.0f, -0.6f, 1.5f, 0, 2.4f, fur);
        m.box(-1.0f, -2.7f, -0.75f, 1.0f, -0.4f, -0.55f, light);
        m.box(-1.25f, -1.3f, -1.3f, -0.4f, 0, -0.4f, light); m.box(0.4f, -1.3f, -1.3f, 1.25f, 0, -0.4f, light);
        float tilt = (float) Math.sin(t * 0.9f) * 0.18f;
        m.boxR(0, -3.0f, 0, 0, 0, tilt, -1.8f, -3.1f, -1.8f, 1.8f, 0, 1.0f, fur);
        m.boxR(0, -3.0f, 0, 0, 0, tilt, -0.9f, -1.4f, -2.7f, 0.9f, -0.2f, -1.7f, light);   // snout
        m.boxR(0, -3.0f, 0, 0, 0, tilt, -0.35f, -1.5f, -2.8f, 0.35f, -1.05f, -2.6f, 0xFF1A1A1A); // nose
        float pant = (float) Math.abs(Math.sin(t * 6)) * 0.35f;
        m.boxR(0, -3.0f, 0, 0, 0, tilt, -0.35f, -0.25f, -2.6f, 0.35f, 0.4f + pant, -2.1f, 0xFFFF7A93);   // tongue
        for (int s = -1; s <= 1; s += 2) {                                    // floppy ears
            float flop = 0.25f + (float) Math.sin(t * 2.2f + s) * 0.12f;
            m.boxR(s * 1.8f, -5.8f, -0.4f, 0, 0, s * flop, s > 0 ? 0 : -0.45f, 0, -0.65f, s > 0 ? 0.45f : 0, 2.8f, 0.65f, dark);
            boolean shut = blink(t, 1.3f);
            if (shut) m.boxR(0, -3.0f, 0, 0, 0, tilt, s * 0.85f - 0.3f, -2.0f, -1.86f, s * 0.85f + 0.3f, -1.85f, -1.8f, 0xFF1A1A1A);
            else {
                m.boxR(0, -3.0f, 0, 0, 0, tilt, s * 0.85f - 0.35f, -2.35f, -1.86f, s * 0.85f + 0.35f, -1.5f, -1.8f, 0xFF1A1A1A);
                m.boxR(0, -3.0f, 0, 0, 0, tilt, s * 0.85f - 0.28f, -2.3f, -1.9f, s * 0.85f - 0.05f, -2.05f, -1.85f, 0xFFFFFFFF);
            }
            m.boxR(0, -3.0f, 0, 0, 0, tilt, s * 1.25f - 0.3f, -1.5f, -1.86f, s * 1.25f + 0.3f, -1.2f, -1.8f, 0x99FF8FA8);   // blush
        }
        m.box(0.4f, -2.6f, 0.4f, 1.55f, -1.2f, 2.0f, dark);                  // a darker patch on the back
        paw(m, -1.25f, -0.4f, -0.4f, light, 0xFF8A5A34); paw(m, 0.4f, 1.25f, -0.4f, light, 0xFF8A5A34);
        float wag = (float) Math.sin(t * 9) * 0.9f;
        tail(m, new float[][]{{0, -1.0f, 2.3f}, {wag * 0.5f, -1.9f, 3.1f}, {wag, -2.9f, 3.4f}}, new float[]{0.45f, 0.4f, 0.25f}, fur, dark);
    }

    private static void bunny(Mesh m, float t) {
        int fur = 0xFFF6F3EF, pink = 0xFFFFB0C4;
        m.box(-1.5f, -2.8f, -0.6f, 1.5f, 0, 2.1f, fur);
        m.box(-1.2f, -0.9f, -1.6f, -0.3f, 0, -0.4f, fur); m.box(0.3f, -0.9f, -1.6f, 1.2f, 0, -0.4f, fur);
        m.box(-1.45f, -5.2f, -1.6f, 1.45f, -2.6f, 0.8f, fur);
        float twitch = (float) Math.sin(t * 14) * 0.04f;
        m.box(-0.3f, -3.8f + twitch, -1.75f, 0.3f, -3.4f + twitch, -1.6f, pink);
        eyes(m, 0.8f, -4.6f, -1.62f, 0.65f, 0xFF1A1A1A, 0, t);
        blush(m, 1.0f, -3.7f, -1.6f, 0.6f);
        m.box(-0.9f, -2.4f, -0.75f, 0.9f, -0.5f, -0.55f, 0xFFFFFFFF);         // fluffy chest
        for (int s = -1; s <= 1; s += 2) {
            float lean = s * 0.18f + (float) Math.sin(t * 1.3f + s) * 0.08f;
            m.boxR(s * 0.75f, -5.2f, -0.3f, 0, 0, lean, -0.45f, -4.2f, -0.25f, 0.45f, 0, 0.3f, fur);
            m.boxR(s * 0.75f, -5.2f, -0.3f, 0, 0, lean, -0.25f, -3.8f, -0.32f, 0.25f, -0.4f, -0.24f, pink);
        }
        m.ball(0, -1.0f, 2.4f, 0.75f, 0xFFFFFFFF);                            // fluffy tail
    }

    private static void panda(Mesh m, float t) {
        int white = 0xFFF4F4F2, black = 0xFF1E1E22;
        m.box(-1.8f, -3.2f, -0.8f, 1.8f, 0, 2.2f, white);
        m.box(-1.8f, -3.0f, -0.6f, 1.8f, -2.0f, 1.6f, black);                 // shoulders band
        m.box(-1.5f, -2.2f, -1.4f, -0.5f, 0, -0.5f, black); m.box(0.5f, -2.2f, -1.4f, 1.5f, 0, -0.5f, black); // arms
        float nod = (float) Math.sin(t * 1.1f) * 0.08f;
        m.boxR(0, -3.0f, 0, nod, 0, 0, -2.0f, -3.3f, -1.9f, 2.0f, 0, 1.0f, white);
        m.boxR(0, -3.0f, 0, nod, 0, 0, -0.35f, -1.2f, -2.0f, 0.35f, -0.8f, -1.9f, black);   // nose
        for (int s = -1; s <= 1; s += 2) {
            m.boxR(0, -3.0f, 0, nod, 0, 0, s > 0 ? 0.9f : -1.9f, -4.0f, -0.5f, s > 0 ? 1.9f : -0.9f, -3.1f, 0.4f, black);   // ears
            m.boxR(0, -3.0f, 0, nod, 0, 0, s > 0 ? 0.35f : -1.45f, -2.3f, -1.96f, s > 0 ? 1.45f : -0.35f, -1.3f, -1.9f, black); // eye patches
            if (!blink(t, 2)) {
                m.boxR(0, -3.0f, 0, nod, 0, 0, s * 0.85f - 0.28f, -2.1f, -1.98f, s * 0.85f + 0.28f, -1.5f, -1.93f, 0xFF2A2A30);
                m.boxR(0, -3.0f, 0, nod, 0, 0, s * 0.85f - 0.22f, -2.05f, -2.02f, s * 0.85f - 0.02f, -1.85f, -1.97f, 0xFFFFFFFF);
            }
            m.boxR(0, -3.0f, 0, nod, 0, 0, s * 1.5f - 0.3f, -1.2f, -1.98f, s * 1.5f + 0.3f, -0.95f, -1.92f, 0x99FF8FA8);
        }
    }

    private static void penguin(Mesh m, float t) {
        int black = 0xFF1F2430, white = 0xFFF3F5F8, orange = 0xFFFF9F2E;
        float waddle = (float) Math.sin(t * 2.4f) * 0.08f;
        m.boxR(0, 0, 0, 0, 0, waddle, -1.6f, -4.2f, -1.2f, 1.6f, -0.3f, 1.4f, black);
        m.boxR(0, 0, 0, 0, 0, waddle, -1.1f, -3.7f, -1.35f, 1.1f, -0.5f, -1.15f, white);
        m.boxR(0, 0, 0, 0, 0, waddle, -1.4f, -6.6f, -1.1f, 1.4f, -4.2f, 1.2f, black);
        m.boxR(0, 0, 0, 0, 0, waddle, -1.0f, -6.0f, -1.25f, 1.0f, -4.5f, -1.05f, white);
        m.boxR(0, 0, 0, 0, 0, waddle, -0.35f, -5.2f, -1.9f, 0.35f, -4.75f, -1.2f, orange);   // beak
        for (int s = -1; s <= 1; s += 2) {
            if (!blink(t, 0.7f)) m.boxR(0, 0, 0, 0, 0, waddle, s * 0.55f - 0.2f, -5.9f, -1.32f, s * 0.55f + 0.2f, -5.4f, -1.26f, 0xFF141414);
            float flap = 0.2f + (float) Math.max(0, Math.sin(t * 3.0f)) * 0.5f;
            m.boxR(s * 1.6f, -3.9f, 0.1f, 0, 0, s * flap, s > 0 ? 0 : -0.35f, 0, -0.8f, s > 0 ? 0.35f : 0, 2.9f, 0.8f, black);
            m.box(s > 0 ? 0.2f : -1.2f, -0.35f, -1.7f, s > 0 ? 1.2f : -0.2f, 0, -0.3f, orange);                       // feet
        }
    }

    private static void frog(Mesh m, float t) {
        int green = 0xFF5DBB4A, dark = 0xFF3C8A30, belly = 0xFFD2EBA0;
        m.box(-1.9f, -2.2f, -1.6f, 1.9f, 0, 1.9f, green);
        m.box(-1.5f, -0.9f, -1.75f, 1.5f, -0.2f, -1.55f, belly);
        m.box(-1.3f, -1.15f, -1.68f, 1.3f, -1.0f, -1.6f, dark);               // smile
        for (int s = -1; s <= 1; s += 2) {
            m.box(s > 0 ? 0.6f : -1.7f, -3.1f, -1.4f, s > 0 ? 1.7f : -0.6f, -2.1f, -0.3f, green);   // eye bumps
            if (blink(t, 0.4f)) m.box(s > 0 ? 0.7f : -1.6f, -2.7f, -1.46f, s > 0 ? 1.6f : -0.7f, -2.5f, -1.4f, dark);
            else { m.box(s > 0 ? 0.75f : -1.55f, -2.95f, -1.47f, s > 0 ? 1.55f : -0.75f, -2.2f, -1.4f, 0xFFFFFFFF);
                   m.box(s > 0 ? 1.0f : -1.3f, -2.8f, -1.52f, s > 0 ? 1.3f : -1.0f, -2.35f, -1.46f, 0xFF141414); }
            m.box(s > 0 ? 1.6f : -2.4f, -1.0f, 0.2f, s > 0 ? 2.4f : -1.6f, 0, 1.9f, dark);             // back legs
        }
    }

    private static void axolotl(Mesh m, float t) {
        int pink = 0xFFF7A9CB, gill = 0xFFE0457F, belly = 0xFFFFD3E4;
        m.box(-1.2f, -1.6f, -0.8f, 1.2f, 0, 2.6f, pink);
        m.box(-0.9f, -0.3f, -0.6f, 0.9f, 0.02f, 2.3f, belly);
        m.box(-1.7f, -3.1f, -2.4f, 1.7f, -0.5f, -0.4f, pink);                  // head
        m.box(-0.7f, -1.3f, -2.47f, 0.7f, -1.15f, -2.4f, 0xFFB0507A);          // smile
        eyes(m, 1.05f, -2.5f, -2.42f, 0.55f, 0xFF1A1A1A, 0, t);
        blush(m, 1.3f, -1.75f, -2.4f, 0.45f);
        for (int s = -1; s <= 1; s += 2) for (int g = 0; g < 3; g++) {         // frilly gills
            float wave = (float) Math.sin(t * 3 + g + s) * 0.12f;
            m.boxR(s * 1.7f, -2.6f + g * 0.7f, -1.2f, 0, 0, s * (0.5f - g * 0.4f + wave), s > 0 ? 0 : -1.3f, -0.18f, -0.25f, s > 0 ? 1.3f : 0, 0.18f, 0.25f, gill);
        }
        for (int s = -1; s <= 1; s += 2) { m.box(s > 0 ? 1.2f : -1.9f, -0.5f, -0.2f, s > 0 ? 1.9f : -1.2f, 0, 0.5f, pink); m.box(s > 0 ? 1.2f : -1.9f, -0.5f, 1.6f, s > 0 ? 1.9f : -1.2f, 0, 2.3f, pink); }
        float sway = (float) Math.sin(t * 2.2f) * 0.35f;
        m.boxR(0, -0.9f, 2.6f, 0, sway, 0, -0.15f, -0.8f, 0, 0.15f, 0.6f, 2.4f, pink);
    }

    private static void duck(Mesh m, float t) {
        int yellow = 0xFFFFD84A, orange = 0xFFFF9A2E;
        m.box(-1.3f, -2.2f, -1.2f, 1.3f, 0, 1.8f, yellow);
        m.box(-0.5f, -2.0f, 1.8f, 0.5f, -1.2f, 2.4f, yellow);                  // tail tip
        float bob = (float) Math.sin(t * 2.6f) * 0.15f;
        m.box(-1.0f, -4.4f + bob, -1.6f, 1.0f, -2.2f + bob, 0.4f, yellow);
        m.box(-0.6f, -3.3f + bob, -2.4f, 0.6f, -2.8f + bob, -1.5f, orange);    // beak
        eyes(m, 0.6f, -4.0f + bob, -1.62f, 0.5f, 0xFF1A1A1A, 0, t);
        blush(m, 0.8f, -3.25f + bob, -1.6f, 0.4f);
        m.box(-0.2f, -4.9f + bob, -0.6f, 0.2f, -4.4f + bob, 0.2f, 0xFFFFE680);  // little tuft
        for (int s = -1; s <= 1; s += 2) {
            float flap = 0.1f + (float) Math.max(0, Math.sin(t * 5)) * 0.35f;
            m.boxR(s * 1.3f, -1.9f, 0.1f, 0, 0, s * flap, s > 0 ? 0 : -0.3f, 0, -0.8f, s > 0 ? 0.3f : 0, 1.5f, 1.2f, 0xFFF2C53A);
            m.box(s > 0 ? 0.2f : -0.9f, -0.2f, -1.4f, s > 0 ? 0.9f : -0.2f, 0, -0.3f, orange);
        }
    }

    // ------------------------------------------------------------------ flying pets
    private static void parrot(Mesh m, float t) {
        int red = 0xFFE23B3B, blue = 0xFF2F7BE0, yellow = 0xFFFFD23A;
        m.box(-1.2f, -1.6f, -1.0f, 1.2f, 1.6f, 1.4f, red);
        m.box(-1.1f, -3.4f, -1.3f, 1.1f, -1.4f, 0.8f, red);
        m.box(-0.4f, -2.6f, -2.1f, 0.4f, -1.7f, -1.3f, 0xFF3A3A40);           // beak
        m.box(-0.4f, -2.0f, -2.2f, 0.4f, -1.5f, -1.9f, 0xFF3A3A40);
        for (int s = -1; s <= 1; s += 2) {
            m.box(s * 0.7f - 0.35f, -3.0f, -1.36f, s * 0.7f + 0.35f, -2.3f, -1.3f, 0xFFFFFFFF);
            if (!blink(t, 0.5f)) { m.box(s * 0.7f - 0.17f, -2.88f, -1.4f, s * 0.7f + 0.17f, -2.42f, -1.35f, 0xFF141414); sparkle(m, s * 0.7f - 0.07f, -2.85f, -1.42f, 0.12f); }
            m.box(s * 0.7f - 0.4f, -2.2f, -1.36f, s * 0.7f + 0.4f, -1.95f, -1.3f, 0xFFFFE07A);   // yellow cheek
            float a = 0.2f + (float) Math.sin(t * 14) * 0.75f;
            wing(m, s, 1.2f, -1.0f, 0.2f, a, 3.6f, 2.6f, -0.8f, 1.3f, blue);
            m.boxR(s * 1.2f, -1.0f, 0.2f, 0, 0, -s * a, s > 0 ? 2.4f : -3.6f, -0.18f, -0.7f, s > 0 ? 3.6f : -2.4f, 0.18f, 1.2f, yellow);
        }
        m.boxR(0, 1.4f, 1.0f, -0.5f, 0, 0, -0.7f, 0, -0.2f, 0.7f, 3.2f, 0.25f, blue);   // tail
        m.boxR(0, 1.4f, 1.0f, -0.5f, 0, 0, -0.35f, 2.4f, -0.25f, 0.35f, 3.6f, 0.3f, yellow);
    }

    private static void bee(Mesh m, float t) {
        int yellow = 0xFFFFC93A, black = 0xFF241E1A;
        m.box(-1.3f, -1.2f, -1.4f, 1.3f, 1.2f, 2.1f, yellow);
        m.box(-1.35f, -1.25f, -0.2f, 1.35f, 1.25f, 0.4f, black);
        m.box(-1.35f, -1.25f, 1.0f, 1.35f, 1.25f, 1.6f, black);
        m.box(-1.1f, -1.0f, -2.4f, 1.1f, 1.0f, -1.3f, black);                  // head
        m.box(-0.15f, -0.15f, 2.1f, 0.15f, 0.15f, 2.7f, black);                // stinger
        for (int s = -1; s <= 1; s += 2) {
            m.box(s * 0.55f - 0.3f, -0.6f, -2.46f, s * 0.55f + 0.3f, 0.1f, -2.4f, 0xFFFFFFFF);
            if (!blink(t, 0.2f)) { m.box(s * 0.55f - 0.14f, -0.48f, -2.5f, s * 0.55f + 0.14f, -0.02f, -2.45f, 0xFF000000); sparkle(m, s * 0.55f - 0.06f, -0.45f, -2.52f, 0.1f); }
            m.line(new float[]{s * 0.4f, -1.0f, -2.2f}, new float[]{s * 0.9f, -2.3f, -2.9f}, 0.1f, black);   // antennae
            for (int k = 0; k < 3; k++) m.box(s * 0.7f - 0.12f, 1.2f, -0.8f + k * 0.8f, s * 0.7f + 0.12f, 1.8f, -0.6f + k * 0.8f, black);
        }
    }
    private static void beeWings(Mesh m, float t) {
        for (int s = -1; s <= 1; s += 2) {
            float a = 0.5f + (float) Math.sin(t * 40) * 0.6f;
            m.boxR(s * 0.5f, -1.2f, 0, 0, 0, -s * a, s > 0 ? 0 : -2.6f, -0.05f, -0.6f, s > 0 ? 2.6f : 0, 0.05f, 1.4f, 0x8CE8F6FF);
        }
    }

    private static void owl(Mesh m, float t) {
        int brown = 0xFF8A5A34, light = 0xFFD9B98A, dark = 0xFF5E3A20;
        m.box(-1.6f, -2.0f, -1.2f, 1.6f, 1.8f, 1.3f, brown);
        m.box(-1.1f, -1.6f, -1.35f, 1.1f, 1.4f, -1.15f, light);
        for (int k = 0; k < 3; k++) m.box(-0.6f + k * 0.5f, -0.6f + k * 0.5f, -1.4f, -0.3f + k * 0.5f, -0.3f + k * 0.5f, -1.3f, dark);
        float turn = (float) Math.sin(t * 0.6f) * 0.5f;                        // owls turn their head
        m.boxR(0, -2.0f, 0, 0, turn, 0, -1.7f, -2.7f, -1.4f, 1.7f, 0, 1.2f, brown);
        m.boxR(0, -2.0f, 0, 0, turn, 0, -1.4f, -2.4f, -1.5f, 1.4f, -0.3f, -1.4f, light);   // face
        for (int s = -1; s <= 1; s += 2) {
            m.boxR(0, -2.0f, 0, 0, turn, 0, s * 0.72f - 0.55f, -2.0f, -1.58f, s * 0.72f + 0.55f, -0.9f, -1.5f, 0xFFFFB02E);
            if (!blink(t, 3)) {
                m.boxR(0, -2.0f, 0, 0, turn, 0, s * 0.72f - 0.28f, -1.75f, -1.63f, s * 0.72f + 0.28f, -1.15f, -1.56f, 0xFF141414);
                m.boxR(0, -2.0f, 0, 0, turn, 0, s * 0.72f - 0.22f, -1.72f, -1.67f, s * 0.72f - 0.04f, -1.54f, -1.62f, 0xFFFFFFFF);
            }
            m.boxR(0, -2.0f, 0, 0, turn, 0, s > 0 ? 0.7f : -1.6f, -3.6f, -0.6f, s > 0 ? 1.6f : -0.7f, -2.7f, 0.2f, dark);   // ear tufts
            float a = 0.1f + (float) Math.sin(t * 7) * 0.6f;
            wing(m, s, 1.6f, -1.2f, 0, a, 3.4f, 2.8f, -0.9f, 1.2f, dark);
        }
        m.boxR(0, -2.0f, 0, 0, turn, 0, -0.25f, -0.95f, -1.75f, 0.25f, -0.4f, -1.5f, 0xFF3A3A40);   // beak
    }

    private static void bat(Mesh m, float t) {
        int fur = 0xFF3A2E3A, wingCol = 0xFF2A1F2A;
        m.box(-1.0f, -1.2f, -0.9f, 1.0f, 1.4f, 1.0f, fur);
        m.box(-1.0f, -2.8f, -1.2f, 1.0f, -1.0f, 0.8f, fur);
        for (int s = -1; s <= 1; s += 2) {
            m.box(s > 0 ? 0.3f : -1.0f, -3.7f, -0.4f, s > 0 ? 1.0f : -0.3f, -2.8f, 0.2f, fur);
            float a = (float) Math.sin(t * 16) * 0.9f;
            m.boxR(s * 1.0f, -0.8f, 0, 0, 0, -s * a, s > 0 ? 0 : -4.2f, -0.1f, -0.6f, s > 0 ? 4.2f : 0, 0.1f, 1.0f, wingCol);
            m.boxR(s * 1.0f, -0.8f, 0, 0, 0, -s * a, s > 0 ? 0.4f : -3.8f, 0.1f, -0.2f, s > 0 ? 3.8f : -0.4f, 2.6f, 0.6f, wingCol);
        }
        m.box(-0.35f, -1.5f, -1.28f, 0.35f, -1.25f, -1.2f, 0xFFFFFFFF);        // fangs
    }

    private static void dragon(Mesh m, float t) {
        int purple = 0xFF7A4FD0, belly = 0xFFE7C870, dark = 0xFF4E2E96, horn = 0xFFF2E6C8;
        m.box(-1.4f, -1.4f, -1.4f, 1.4f, 1.6f, 2.0f, purple);
        m.box(-1.0f, -0.8f, -1.55f, 1.0f, 1.4f, -1.35f, belly);
        m.box(-1.3f, -3.6f, -2.8f, 1.3f, -1.4f, -0.6f, purple);                // head
        m.box(-0.8f, -2.7f, -3.8f, 0.8f, -1.6f, -2.7f, purple);                // snout
        m.box(-0.5f, -2.5f, -3.85f, -0.2f, -2.2f, -3.78f, 0xFF2A1A40); m.box(0.2f, -2.5f, -3.85f, 0.5f, -2.2f, -3.78f, 0xFF2A1A40);
        for (int s = -1; s <= 1; s += 2) {
            m.boxR(s * 0.8f, -3.6f, -1.0f, -0.5f, 0, s * -0.2f, -0.25f, -1.4f, -0.25f, 0.25f, 0, 0.25f, horn);
            float a = 0.2f + (float) Math.sin(t * 9) * 0.8f;
            m.boxR(s * 1.4f, -1.0f, 0.2f, 0, 0, -s * a, s > 0 ? 0 : -4.0f, -0.12f, -0.6f, s > 0 ? 4.0f : 0, 0.12f, 1.4f, dark);
            m.boxR(s * 1.4f, -1.0f, 0.2f, 0, 0, -s * a, s > 0 ? 0.5f : -3.4f, 0.12f, -0.2f, s > 0 ? 3.4f : -0.5f, 2.4f, 1.1f, 0xFFA57CF0);
        }
        for (int k = 0; k < 3; k++) m.box(-0.2f, -1.9f, -0.6f + k * 0.9f, 0.2f, -1.4f, -0.2f + k * 0.9f, horn);   // back spikes
        float sway = (float) Math.sin(t * 2) * 0.8f;
        tail(m, new float[][]{{0, 0.8f, 1.9f}, {sway * 0.5f, 1.5f, 3.4f}, {sway, 2.4f, 4.4f}, {sway * 1.2f, 3.2f, 5.0f}}, new float[]{0.8f, 0.6f, 0.4f, 0.1f}, purple, dark);
    }
    private static void dragonFlame(Mesh m, float t) { // a puff of fire now and then
        float p = t % 5f;
        if (p > 1.0f) return;
        for (int i = 0; i < 6; i++) {
            float q = (p + i * 0.08f) % 1f, s = 0.25f + q * 0.5f;
            m.octa((float) Math.sin(i * 2.1) * q * 0.6f, -2.0f + q * 0.4f, -4.0f - q * 3.2f, s, s, s, i % 2 == 0 ? 0xD0FF8A2A : 0xD0FFD24A);
        }
    }

    private static void ghostEyes(Mesh m, float t) {
        for (int s = -1; s <= 1; s += 2) m.box(s * 0.75f - 0.3f, -1.6f, -1.72f, s * 0.75f + 0.3f, -0.7f, -1.66f, 0xFF15151E);
        float o = 0.25f + (float) Math.abs(Math.sin(t * 1.5f)) * 0.15f;
        m.box(-o, -0.2f, -1.72f, o, 0.4f, -1.66f, 0xFF15151E);                 // "oo"
    }
    private static void ghost(Mesh m, float t) {
        int body = 0xC8F4F6FF;
        m.sphere(0, -0.6f, 0, 1.8f, 1.8f, 1.6f, 14, body, Math.PI / 2);        // round top
        m.box(-1.8f, -0.6f, -1.6f, 1.8f, 1.6f, 1.6f, body);
        for (int i = 0; i < 4; i++) {                                         // wavy hem
            float x = -1.8f + i * 0.9f, w = (float) Math.sin(t * 4 + i * 1.5f) * 0.35f;
            m.box(x, 1.6f, -1.6f, x + 0.9f, 2.3f + w, 1.6f, body);
        }
        for (int s = -1; s <= 1; s += 2) {
            float wave = (float) Math.sin(t * 3 + s) * 0.3f;
            m.boxR(s * 1.8f, 0.2f, -0.3f, 0, 0, s * (0.6f + wave), s > 0 ? 0 : -1.2f, -0.3f, -0.3f, s > 0 ? 1.2f : 0, 0.3f, 0.3f, body);
        }
        m.box(-1.9f, -2.0f, -1.7f, 1.9f, 2.0f, 1.7f, 0x1CBFEFFF);              // soft glow
    }

    private static void phoenix(Mesh m, float t) {
        int body = 0xFFFF8A2A, head = 0xFFFFB347, tip = 0xE6FFE07A;
        m.box(-1.0f, -1.4f, -1.0f, 1.0f, 1.4f, 1.6f, body);
        m.box(-0.9f, -3.0f, -1.4f, 0.9f, -1.4f, 0.4f, head);
        m.box(-0.3f, -2.3f, -2.1f, 0.3f, -1.8f, -1.4f, 0xFFFFE07A);
        for (int s = -1; s <= 1; s += 2) m.box(s * 0.5f - 0.18f, -2.6f, -1.47f, s * 0.5f + 0.18f, -2.2f, -1.42f, 0xFF3A1406);
        for (int k = 0; k < 3; k++) {                                         // crest
            float f = (float) Math.sin(t * 9 + k) * 0.15f;
            m.octa(-0.4f + k * 0.4f, -3.5f - k % 2 * 0.3f + f, -0.3f + k * 0.3f, 0.25f, 0.6f, 0.25f, tip);
        }
        for (int s = -1; s <= 1; s += 2) {
            float a = 0.25f + (float) Math.sin(t * 8) * 0.8f;
            for (int k = 0; k < 4; k++) {                                     // fiery feathers
                float len = 3.6f - k * 0.5f;
                m.boxR(s * 1.0f, -0.9f + k * 0.5f, 0.2f, 0, 0, -s * (a + k * 0.18f), s > 0 ? 0 : -len, -0.12f, -0.6f + k * 0.1f, s > 0 ? len : 0, 0.12f, 1.0f, k % 2 == 0 ? 0xE6FF5A1F : tip);
            }
        }
        for (int i = 0; i < 7; i++) {                                         // flame tail
            float q = (t * 1.4f + i / 7f) % 1f, s = 0.55f * (1 - q) + 0.1f;
            m.octa((float) Math.sin(t * 5 + i) * 0.4f, 1.4f + q * 3.5f, 1.4f + q * 2.2f, s, s * 1.4f, s, q < 0.5f ? 0xE0FFB347 : 0xC0FF5A1F);
        }
        m.box(-1.5f, -3.2f, -1.6f, 1.5f, 1.8f, 2.0f, 0x18FF8A2A);
    }

    private static void fairy(Mesh m, float t) {
        float pulse = 0.85f + (float) Math.sin(t * 5) * 0.12f;
        m.ball(0, 0, 0, 0.8f * pulse, 0xFFFFF6CF);
        m.ball(0, 0, 0, 1.6f * pulse, 0x3CFFE38A);
        m.ball(0, 0, 0, 2.5f * pulse, 0x14FFE38A);
        for (int s = -1; s <= 1; s += 2) {
            float a = 0.3f + (float) Math.sin(t * 22) * 0.55f;
            m.boxR(s * 0.6f, -0.4f, 0.4f, 0, 0, -s * a, s > 0 ? 0 : -2.2f, -1.8f, -0.03f, s > 0 ? 2.2f : 0, 0.2f, 0.03f, 0x9CB6E8FF);
            m.boxR(s * 0.6f, 0.2f, 0.4f, 0, 0, -s * (a * 0.7f - 0.5f), s > 0 ? 0 : -1.5f, -0.1f, -0.03f, s > 0 ? 1.5f : 0, 1.4f, 0.03f, 0x9CFFC4F0);
        }
        for (int i = 0; i < 8; i++) {                                         // sparkle trail
            float q = (t * 0.8f + i / 8f) % 1f;
            double a = t * 2 + i;
            float s = 0.22f * (1 - q);
            m.octa((float) Math.cos(a) * 1.4f, q * 4.5f, 1.0f + (float) Math.sin(a) * 1.4f + q * 1.5f, s, s, s, q < 0.5f ? 0xF0FFFFFF : 0xC0FFE38A);
        }
    }

    private static void ufo(Mesh m, float t) {
        float wob = (float) Math.sin(t * 1.3f) * 0.12f;
        int metal = 0xFFB8C2CC, dark = 0xFF6E7781;
        // tilt by drawing in a slightly rotated frame is costly: just two tapered hulls
        m.cylinder(0, 0, 0 + wob, -0.7f + wob, 3.6f, 2.5f, 24, metal, 0xFFDDE3E8, false);
        m.cylinder(0, 0, 0 + wob, 0.8f + wob, 3.6f, 1.8f, 24, metal, dark, false);
        m.sphere(0, -0.7f + wob, 0, 1.7f, 1.6f, 1.7f, 16, 0x5590E0FF, Math.PI / 2);   // glass dome
        m.box(-0.55f, -1.9f + wob, -0.55f, 0.55f, -0.75f + wob, 0.55f, 0xFF7BE36A);   // little alien
        m.box(-0.45f, -1.6f + wob, -0.6f, -0.1f, -1.25f + wob, -0.56f, 0xFF141414);
        m.box(0.1f, -1.6f + wob, -0.6f, 0.45f, -1.25f + wob, -0.56f, 0xFF141414);
    }
    private static void ufoLights(Mesh m, float t) {
        float wob = (float) Math.sin(t * 1.3f) * 0.12f;
        int[] cols = {0xFFFF4D6A, 0xFFFFD84D, 0xFF4DFF9A, 0xFF4DC8FF};
        for (int i = 0; i < 10; i++) {
            double a = i * Math.PI * 2 / 10 + t * 1.6;
            boolean on = ((int) (t * 6) + i) % 3 != 0;
            m.octa((float) Math.cos(a) * 3.3f, 0.05f + wob, (float) Math.sin(a) * 3.3f, 0.32f, 0.32f, 0.32f, on ? cols[i % 4] : 0x60FFFFFF);
        }
        float beam = 0.35f + (float) Math.sin(t * 3) * 0.1f;                   // soft beam below
        m.cylinder(0, 0, 0.8f + wob, 5.5f, 1.2f, 2.6f, 16, ((int) (beam * 120) << 24) | 0x9CF0FF, 0x009CF0FF, true);
    }

    private static void jelly(Mesh m, float t) {
        float pulse = 1 + (float) Math.sin(t * 2.4f) * 0.1f;
        m.sphere(0, 0, 0, 2.0f * pulse, 1.6f / pulse, 2.0f * pulse, 16, 0xA8D58CFF, Math.PI / 2);   // bell
        m.sphere(0, -0.1f, 0, 1.2f, 0.9f, 1.2f, 12, 0xC0FFB8F0, Math.PI / 2);
        m.ring(0, 0, 0, 0, 2.0f * pulse, 16, 0x70B48CFF);
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI * 2 / 6;
            float bx = (float) Math.cos(a) * 1.3f, bz = (float) Math.sin(a) * 1.3f;
            float[][] pts = new float[5][];
            for (int k = 0; k < 5; k++) {
                float sway = (float) Math.sin(t * 3 - k * 0.8f + i) * 0.25f * k;
                pts[k] = new float[]{bx * (1 - k * 0.08f) + sway, k * 1.1f, bz * (1 - k * 0.08f) + sway * 0.5f};
            }
            m.tube(List.of(pts), new float[]{0.16f, 0.14f, 0.12f, 0.09f, 0.04f}, 5, 0xC0E0B0FF, 0x40FFB8F0);
        }
        for (int s = -1; s <= 1; s += 2) m.box(s * 0.65f - 0.18f, -0.9f, -1.9f * pulse - 0.05f, s * 0.65f + 0.18f, -0.5f, -1.9f * pulse + 0.05f, 0xFF2A1440);
    }
}
