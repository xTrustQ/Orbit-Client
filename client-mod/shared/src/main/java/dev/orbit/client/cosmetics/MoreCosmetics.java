package dev.orbit.client.cosmetics;

import dev.orbit.client.cosmetics.CosmeticsFeature.Mesh;

import java.util.List;

/**
 * The second wave of Orbit hats and auras (same 3D-in-code style as CosmeticsFeature).
 * Hats: head space (head cube x,z in [-4,4], y in [-8,0], face at z = -4). Auras: model space (neck y=0, feet y=24).
 */
final class MoreCosmetics {
    private MoreCosmetics() {}

    // =====================================================================================================
    // Hats
    // =====================================================================================================
    static void hatLit(Mesh m, String hat, float t) {
        switch (hat) {
            case "Party Hat" -> partyHat(m);
            case "Beanie" -> beanie(m);
            case "Headphones" -> headphones(m);
            case "Viking Helmet" -> viking(m);
            case "Cowboy Hat" -> cowboy(m);
            case "Flower Crown" -> flowers(m, t);
            case "Bunny Ears" -> bunnyEars(m, t);
            case "Santa Hat" -> santa(m);
            case "Propeller Cap" -> propeller(m, t);
            case "Astronaut Helmet" -> astronaut(m);
            case "Orbit Ring" -> orbitPlanets(m, t);
            case "Chef Hat" -> chef(m);
            case "Antlers" -> antlers(m);
            default -> {}
        }
    }
    static void hatGlow(Mesh m, String hat, float t) {
        switch (hat) {
            case "Headphones" -> { int g = 0xFF2EE6A6; m.box(5.55f, -4.4f, -0.8f, 5.75f, -2.8f, 0.8f, g); m.box(-5.75f, -4.4f, -0.8f, -5.55f, -2.8f, 0.8f, g); }
            case "Astronaut Helmet" -> { boolean on = (t % 1.4f) < 0.7f; m.octa(0, -11.6f, 0, 0.5f, 0.5f, 0.5f, on ? 0xFFFF4D6A : 0x60FF4D6A); }
            case "Orbit Ring" -> orbitPath(m, t);
            case "Party Hat" -> confetti(m, t);
            default -> {}
        }
    }

    private static void partyHat(Mesh m) {
        int a = 0xFFFF5DA2, b = 0xFF5DD8FF;
        m.tube(List.of(new float[]{0, -7.9f, 0}, new float[]{0.45f, -10.3f, 0.2f}), new float[]{3.3f, 2.3f}, 18, a, a);
        m.tube(List.of(new float[]{0.45f, -10.3f, 0.2f}, new float[]{0.9f, -12.6f, 0.4f}), new float[]{2.3f, 1.3f}, 18, b, b);
        m.tube(List.of(new float[]{0.9f, -12.6f, 0.4f}, new float[]{1.35f, -15.0f, 0.6f}), new float[]{1.3f, 0.08f}, 18, a, a);
        m.ball(1.4f, -15.3f, 0.6f, 0.9f, 0xFFFFE27A);
        m.ring(0, -7.9f, 0, 3.0f, 3.5f, 18, 0xFFFFE27A);
    }
    private static void confetti(Mesh m, float t) {
        int[] cols = {0xFFFF5DA2, 0xFF5DD8FF, 0xFFFFE27A, 0xFF7DFF9A};
        for (int i = 0; i < 6; i++) {
            float q = (t * 0.5f + i / 6f) % 1f;
            double a = i * 1.7 + t;
            m.octa(1.4f + (float) Math.cos(a) * (1 + q * 3), -15.5f + q * 5, 0.6f + (float) Math.sin(a) * (1 + q * 3), 0.25f, 0.25f, 0.25f,
                    ((int) (255 * Math.sin(q * Math.PI)) << 24) | (cols[i % 4] & 0xFFFFFF));
        }
    }

    private static void beanie(Mesh m) {
        int knit = 0xFF2F6FD6, rib = 0xFF2558B0;
        m.box(-4.45f, -8.7f, -4.45f, 4.45f, -5.6f, 4.45f, knit);
        m.box(-3.6f, -9.4f, -3.6f, 3.6f, -8.7f, 3.6f, knit);
        m.box(-4.65f, -6.4f, -4.65f, 4.65f, -5.3f, 4.65f, rib);
        for (int i = -3; i <= 3; i++) m.box(i - 0.12f, -6.35f, -4.72f, i + 0.12f, -5.35f, -4.64f, 0xFF1E4A96);
        for (int k = 0; k < 5; k++) { double a = k * 1.3; m.ball((float) Math.cos(a) * 0.6f, -10.4f + (k % 2) * 0.3f, (float) Math.sin(a) * 0.6f, 1.0f, 0xFFF4F4F8); }
    }

    private static void headphones(Mesh m) {
        m.tube(List.of(new float[]{-4.9f, -4.5f, 0}, new float[]{-4.6f, -8.3f, 0}, new float[]{0, -9.7f, 0}, new float[]{4.6f, -8.3f, 0}, new float[]{4.9f, -4.5f, 0}),
                new float[]{0.45f, 0.5f, 0.55f, 0.5f, 0.45f}, 8, 0xFF26262E, 0xFF26262E);
        for (int s = -1; s <= 1; s += 2) {
            m.box(s > 0 ? 4.0f : -5.55f, -5.6f, -1.9f, s > 0 ? 5.55f : -4.0f, -1.6f, 1.9f, 0xFF1C1C22);
            m.box(s > 0 ? 4.0f : -4.35f, -5.2f, -1.5f, s > 0 ? 4.35f : -4.0f, -2.0f, 1.5f, 0xFF3A3A46);
        }
    }

    private static void viking(Mesh m) {
        int metal = 0xFF9AA3AD, gold = 0xFFC9962E;
        m.box(-4.5f, -8.8f, -4.5f, 4.5f, -5.6f, 4.5f, metal);
        m.box(-3.5f, -9.6f, -3.5f, 3.5f, -8.8f, 3.5f, metal);
        m.box(-4.65f, -6.4f, -4.65f, 4.65f, -5.5f, 4.65f, gold);
        m.box(-0.5f, -5.6f, -4.75f, 0.5f, -3.4f, -4.5f, metal);
        for (int i = -1; i <= 1; i++) m.box(-0.3f + i * 2.5f, -9.0f, -4.6f, 0.3f + i * 2.5f, -6.4f, -4.5f, 0xFF7E8792); // rivets/ridges
        for (int s = -1; s <= 1; s += 2)
            m.tube(List.of(new float[]{s * 4.4f, -7.0f, 0}, new float[]{s * 6.5f, -8.2f, 0}, new float[]{s * 7.6f, -10.5f, -0.5f}, new float[]{s * 7.4f, -12.6f, -1.2f}),
                    new float[]{1.1f, 0.85f, 0.5f, 0.08f}, 10, 0xFFF2E6C8, 0xFFC8B48A);
    }

    private static void cowboy(Mesh m) {
        int brown = 0xFF8A5A2E, dark = 0xFF5E3A1A;
        int n = 32;
        for (int i = 0; i < n; i++) { // brim, curled up at the sides
            double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n;
            float y0 = -8.0f - 1.8f * (float) Math.pow(Math.cos(a0), 2), y1 = -8.0f - 1.8f * (float) Math.pow(Math.cos(a1), 2);
            m.quad(new float[]{(float) Math.cos(a0) * 4.2f, -8.0f, (float) Math.sin(a0) * 4.2f}, new float[]{(float) Math.cos(a0) * 8.6f, y0, (float) Math.sin(a0) * 8.0f},
                    new float[]{(float) Math.cos(a1) * 8.6f, y1, (float) Math.sin(a1) * 8.0f}, new float[]{(float) Math.cos(a1) * 4.2f, -8.0f, (float) Math.sin(a1) * 4.2f}, brown);
        }
        m.cylinder(0, 0, -8.0f, -12.6f, 4.4f, 3.8f, 24, dark, brown, true);
        m.disc(0, -12.6f, 0, 3.8f, 24, brown);
        m.box(-0.4f, -12.9f, -3.0f, 0.4f, -12.5f, 3.0f, dark);                 // dent
        m.cylinder(0, 0, -8.05f, -9.0f, 4.46f, 4.36f, 24, 0xFF2A1A0E, 0xFF2A1A0E, true);
        m.box(-0.6f, -9.0f, -4.6f, 0.6f, -8.1f, -4.3f, 0xFFD8B04A);            // buckle
    }

    private static void flowers(Mesh m, float t) {
        m.torus(0, -8.15f, 0, 5.4f, 0.38f, 36, 6, 0, 0xFF3E8E3A);
        int[] petals = {0xFFFF8FB3, 0xFFFFFFFF, 0xFFB98CFF, 0xFFFFC94D, 0xFF7FD8FF};
        for (int i = 0; i < 10; i++) {
            double a = i * Math.PI * 2 / 10;
            float cx = (float) Math.cos(a) * 5.4f, cz = (float) Math.sin(a) * 5.4f, cy = -8.5f;
            int col = petals[i % petals.length];
            for (int k = 0; k < 5; k++) {
                double b = k * Math.PI * 2 / 5 + i;
                m.octa(cx + (float) Math.cos(b) * 0.6f, cy + (float) Math.sin(b) * 0.6f, cz, 0.45f, 0.45f, 0.45f, col);
            }
            m.octa(cx, cy, cz, 0.35f, 0.35f, 0.35f, 0xFFFFD84A);
            double l = a + Math.PI / 10;
            m.octa((float) Math.cos(l) * 5.5f, -8.0f, (float) Math.sin(l) * 5.5f, 0.5f, 0.2f, 0.5f, 0xFF5BB84E);
        }
    }

    private static void bunnyEars(Mesh m, float t) {
        int fur = 0xFFF7F7F7, pink = 0xFFFFB3C7;
        for (int s = -1; s <= 1; s += 2) {
            float lean = s * 0.15f + (float) Math.sin(t * 1.4f + s) * 0.05f;
            m.boxR(s * 2.0f, -8.0f, 0, 0, 0, lean, -0.95f, -6.0f, -0.45f, 0.95f, 0, 0.45f, fur);
            m.boxR(s * 2.0f, -8.0f, 0, 0, 0, lean, -0.55f, -5.4f, -0.52f, 0.55f, -0.5f, -0.44f, pink);
            if (s < 0) { // one floppy tip
                float px = s * 2.0f + (float) Math.sin(lean) * 6.0f, py = -8.0f - (float) Math.cos(lean) * 6.0f;
                m.boxR(px, py, 0, 0, 0, lean + 1.1f, -0.95f, -2.6f, -0.45f, 0.95f, 0, 0.45f, fur);
                m.boxR(px, py, 0, 0, 0, lean + 1.1f, -0.55f, -2.2f, -0.52f, 0.55f, -0.3f, -0.44f, pink);
            }
        }
    }

    private static void santa(Mesh m) {
        int red = 0xFFD62B35, white = 0xFFF8F8F8;
        m.tube(List.of(new float[]{0, -8.4f, 0}, new float[]{0, -10.8f, 0.5f}, new float[]{0.3f, -12.6f, 2.0f}, new float[]{0.6f, -13.2f, 3.8f}, new float[]{0.8f, -12.3f, 5.3f}),
                new float[]{5.0f, 3.6f, 2.2f, 1.1f, 0.2f}, 20, red, 0xFFB01E28);
        m.cylinder(0, 0, -7.2f, -9.0f, 5.8f, 5.8f, 28, white, white, false);
        m.ball(0.8f, -12.0f, 5.6f, 1.2f, white);
    }

    private static void propeller(Mesh m, float t) {
        int[] q = {0xFFE5484D, 0xFFFFD84A, 0xFF3B82F6, 0xFF4DD88A};
        m.box(-4.4f, -8.6f, -4.4f, 0, -6.0f, 0, q[0]); m.box(0, -8.6f, -4.4f, 4.4f, -6.0f, 0, q[1]);
        m.box(0, -8.6f, 0, 4.4f, -6.0f, 4.4f, q[2]); m.box(-4.4f, -8.6f, 0, 0, -6.0f, 4.4f, q[3]);
        m.box(-3.6f, -9.1f, -3.6f, 3.6f, -8.6f, 3.6f, 0xFFE5484D);
        m.box(-3.8f, -6.4f, -7.0f, 3.8f, -6.0f, -4.4f, 0xFF3B82F6);            // visor
        m.box(-0.3f, -10.4f, -0.3f, 0.3f, -9.1f, 0.3f, 0xFF9AA3AD);
        float spin = t * 14;
        m.boxR(0, -10.5f, 0, 0, spin, 0, -4.2f, -0.2f, -0.6f, 4.2f, 0.2f, 0.6f, 0xFFE5484D);
        m.boxR(0, -10.5f, 0, 0, spin, 0, -0.5f, -0.35f, -0.5f, 0.5f, 0.15f, 0.5f, 0xFFFFD84A);
    }

    private static void astronaut(Mesh m) {
        m.torus(0, 0.3f, 0, 5.2f, 0.9f, 32, 8, 0, 0xFFEDEFF4);                // neck ring
        m.box(-0.2f, -11.4f, -0.2f, 0.2f, -9.8f, 0.2f, 0xFF9AA3AD);            // antenna
        m.sphere(0, -4.2f, 0, 6.4f, 6.4f, 6.4f, 28, 0x38A8DCFF, Math.PI * 0.86); // glass bubble
        m.quad(new float[]{-3.5f, -8.2f, -5.5f}, new float[]{-1.5f, -9.5f, -5.0f}, new float[]{-1.1f, -8.6f, -5.6f}, new float[]{-3.0f, -7.4f, -5.9f}, 0x70FFFFFF); // shine
    }

    private static void orbitPlanets(Mesh m, float t) {
        double a = t * 1.1;
        float px = (float) Math.cos(a) * 6.5f, pz = (float) Math.sin(a) * 6.5f, py = -11.5f;
        m.ball(px, py, pz, 1.4f, 0xFF2EE6A6);
        m.torus(px, py, pz, 2.1f, 0.18f, 20, 4, 0, 0xFFFFD27A);
        double b = -t * 1.7 + 2;
        m.ball((float) Math.cos(b) * 4.2f, -10.2f, (float) Math.sin(b) * 4.2f, 0.7f, 0xFF9B7BFF);
    }
    private static void orbitPath(Mesh m, float t) {
        m.torus(0, -11.5f, 0, 6.5f, 0.08f, 48, 4, 0, 0x90B48CFF);
        m.torus(0, -10.2f, 0, 4.2f, 0.06f, 40, 4, 0, 0x70FFFFFF);
        for (int i = 0; i < 5; i++) {
            double a = i * 1.26 + t * 0.4;
            float s = 0.18f + 0.1f * (float) Math.abs(Math.sin(t * 3 + i));
            m.octa((float) Math.cos(a) * 7.6f, -12.4f + (float) Math.sin(t + i) * 0.5f, (float) Math.sin(a) * 7.6f, s, s * 1.5f, s, 0xF0FFFFFF);
        }
    }

    private static void chef(Mesh m) {
        int w = 0xFFF6F6F6;
        m.cylinder(0, 0, -7.9f, -10.8f, 4.5f, 4.7f, 24, 0xFFE8E8E8, w, true);
        m.ball(0, -12.6f, 0, 3.0f, w);
        for (int i = 0; i < 5; i++) { double a = i * Math.PI * 2 / 5; m.ball((float) Math.cos(a) * 2.6f, -11.6f, (float) Math.sin(a) * 2.6f, 2.3f, w); }
    }

    private static void antlers(Mesh m) {
        int c0 = 0xFF7A5230, c1 = 0xFFB08560;
        for (int s = -1; s <= 1; s += 2) {
            m.tube(List.of(new float[]{s * 2.6f, -7.8f, 0.5f}, new float[]{s * 3.6f, -10.5f, 0.8f}, new float[]{s * 4.2f, -13f, 0.4f}, new float[]{s * 5.4f, -15f, 0}),
                    new float[]{0.6f, 0.48f, 0.36f, 0.1f}, 8, c0, c1);
            m.tube(List.of(new float[]{s * 3.6f, -10.5f, 0.8f}, new float[]{s * 5.0f, -11.4f, 0.1f}, new float[]{s * 5.8f, -11.9f, -0.7f}), new float[]{0.36f, 0.26f, 0.08f}, 6, c0, c1);
            m.tube(List.of(new float[]{s * 4.2f, -13f, 0.4f}, new float[]{s * 3.6f, -14.6f, 0}, new float[]{s * 3.3f, -15.6f, -0.4f}), new float[]{0.3f, 0.2f, 0.06f}, 6, c0, c1);
        }
    }

    // =====================================================================================================
    // Auras (glowing, model space)
    // =====================================================================================================
    static boolean aura(Mesh m, String type, float t) {
        switch (type) {
            case "Petals" -> petals(m, t);
            case "Music" -> notes(m, t);
            case "Bubbles" -> bubbles(m, t);
            case "Fireflies" -> fireflies(m, t);
            case "Rainbow" -> rainbow(m, t);
            case "Galaxy" -> galaxy(m, t);
            case "Orbit" -> orbit(m, t);
            default -> { return false; }
        }
        return true;
    }
    private static int a(int argb, float k) { return ((int) (Math.max(0, Math.min(1, k)) * (argb >>> 24)) << 24) | (argb & 0xFFFFFF); }

    private static void petals(Mesh m, float t) {
        for (int i = 0; i < 18; i++) {
            float p = (t * 0.09f + i / 18f) % 1f;
            double ang = i * 2.3 + t * 0.4 + p * 3;
            float r = 8 + (i % 3) * 1.8f, y = -9 + p * 33;
            float x = (float) Math.cos(ang) * r, z = (float) Math.sin(ang) * r;
            float rot = t * 1.5f + i, c = (float) Math.cos(rot) * 1.3f, s = (float) Math.sin(rot) * 1.3f;
            int col = a(i % 3 == 0 ? 0xFFFFFFFF : 0xFFFFA8C8, (float) Math.sin(p * Math.PI));
            m.quad(new float[]{x + c, y, z + s * 0.4f}, new float[]{x, y - 0.75f, z + s}, new float[]{x - c, y, z - s * 0.4f}, new float[]{x, y + 0.75f, z - s}, col);
        }
    }
    private static void notes(Mesh m, float t) {
        int[] cols = {0xFF7AF0FF, 0xFFFF8FD0, 0xFFFFE27A, 0xFF9B7BFF};
        for (int i = 0; i < 8; i++) {
            float p = (t * 0.18f + i / 8f) % 1f;
            double ang = i * Math.PI * 2 / 8 + t * 0.5;
            float r = 10, x = (float) Math.cos(ang) * r, z = (float) Math.sin(ang) * r, y = 22 - p * 30 + (float) Math.sin(t * 3 + i) * 0.8f;
            int col = a(cols[i % 4], (float) Math.sin(p * Math.PI));
            m.sphere(x, y, z, 0.8f, 0.6f, 0.8f, 8, col, Math.PI);           // note head
            m.line(new float[]{x + 0.7f, y, z}, new float[]{x + 0.7f, y - 3.2f, z}, 0.14f, col);   // stem
            if (i % 2 == 0) m.line(new float[]{x + 0.7f, y - 3.2f, z}, new float[]{x + 2.0f, y - 2.4f, z}, 0.16f, col);   // flag
        }
    }
    private static void bubbles(Mesh m, float t) {
        for (int i = 0; i < 12; i++) {
            float p = (t * 0.12f + i / 12f) % 1f;
            double ang = i * 2.1 + t * 0.3;
            float r = 8 + (i % 3) * 1.5f + (float) Math.sin(t * 2 + i) * 0.6f;
            float x = (float) Math.cos(ang) * r, z = (float) Math.sin(ang) * r, y = 24 - p * 32, s = 0.6f + (i % 3) * 0.3f;
            float k = (float) Math.sin(p * Math.PI);
            m.sphere(x, y, z, s, s, s, 10, a(0x509CE6FF, k), Math.PI);
            m.octa(x - s * 0.4f, y - s * 0.4f, z - s * 0.4f, s * 0.2f, s * 0.2f, s * 0.2f, a(0xE0FFFFFF, k));
        }
    }
    private static void fireflies(Mesh m, float t) {
        for (int i = 0; i < 14; i++) {
            double ang = i * 2.4 + t * 0.25 * (i % 2 == 0 ? 1 : -1);
            float r = 7 + (float) Math.sin(t * 0.7 + i) * 3, y = 6 + (float) Math.sin(t * 0.9 + i * 1.7) * 9;
            float x = (float) Math.cos(ang) * r, z = (float) Math.sin(ang) * r;
            float blink = (float) Math.max(0, Math.sin(t * 2.2 + i * 1.3));
            m.octa(x, y, z, 0.45f, 0.45f, 0.45f, a(0xFFE8FF7A, 0.3f + blink));
            m.octa(x, y, z, 1.4f, 1.4f, 1.4f, a(0x50C8FF5A, blink));
        }
    }
    private static void rainbow(Mesh m, float t) {
        int[] cols = {0xFFFF4D4D, 0xFFFF9A3D, 0xFFFFE24D, 0xFF5DE36A, 0xFF4DC8FF, 0xFF6A6BFF, 0xFFC27BFF};
        float pulse = (float) Math.sin(t * 2) * 0.4f;
        for (int i = 0; i < cols.length; i++)
            m.torus(0, 23.4f - pulse * 0.2f, 0, 8.5f + i * 0.55f + pulse, 0.26f, 40, 4, t * 0.5f, a(cols[i], 0.75f));
        for (int i = 0; i < 14; i++) {
            float p = (t * 0.2f + i / 14f) % 1f;
            double ang = i * Math.PI * 2 / 14 + t * 0.6;
            float r = 9.5f, y = 23 - p * 26;
            m.octa((float) Math.cos(ang) * r, y, (float) Math.sin(ang) * r, 0.35f, 0.5f, 0.35f, a(cols[i % cols.length], (float) Math.sin(p * Math.PI)));
        }
    }
    private static void galaxy(Mesh m, float t) {
        for (int arm = 0; arm < 3; arm++) for (int i = 0; i < 16; i++) {
            float k = i / 16f;
            double ang = arm * Math.PI * 2 / 3 + k * 4.2 + t * 0.5;
            float r = 2 + k * 10, x = (float) Math.cos(ang) * r, z = (float) Math.sin(ang) * r;
            float s = 0.25f + (1 - k) * 0.25f + 0.1f * (float) Math.sin(t * 4 + i + arm);
            int col = i % 4 == 0 ? 0xFFFFFFFF : arm == 0 ? 0xFFB48CFF : arm == 1 ? 0xFF6FC8FF : 0xFFFF8FD0;
            m.octa(x, 23.5f, z, s, s * 0.6f, s, a(col, 1 - k * 0.5f));
        }
        for (int i = 0; i < 10; i++) {
            double ang = i * 0.63 + t * 0.2;
            float y = 2 + (i * 2.3f) % 18, s = 0.2f + 0.15f * (float) Math.abs(Math.sin(t * 3 + i * 1.7));
            m.octa((float) Math.cos(ang) * 11, y, (float) Math.sin(ang) * 11, s, s * 1.6f, s, 0xF0FFFFFF);
        }
    }
    private static void orbit(Mesh m, float t) {
        float[][] p = {{11, 4, 0.9f}, {13, 13, -0.6f}, {9.5f, 20, 1.3f}};
        int[] cols = {0xFF2EE6A6, 0xFF9B7BFF, 0xFFFFB347};
        for (int i = 0; i < 3; i++) {
            m.torus(0, p[i][1], 0, p[i][0], 0.07f, 48, 4, 0, 0x6CFFFFFF);
            double ang = t * p[i][2] + i * 2;
            float x = (float) Math.cos(ang) * p[i][0], z = (float) Math.sin(ang) * p[i][0];
            m.ball(x, p[i][1], z, 0.9f, cols[i]);
            m.ball(x, p[i][1], z, 1.6f, a(cols[i], 0.2f));
        }
    }
}
