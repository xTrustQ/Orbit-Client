package dev.orbit.client.sky;

/**
 * Procedural skies. Each preset is a pure function from a view direction to a colour, so the
 * generated cube map is seamless on every edge.
 */
public enum SkyPreset {
    AURORA("Aurora") {
        @Override int color(double x, double y, double z) {
            double e = y;
            double[] c = grad(e, new double[][]{{-1, .01, .02, .05}, {0, .04, .12, .18}, {.25, .02, .07, .14}, {1, .005, .01, .04}});
            add(c, stars(x, y, z, 0.9975, 1.0), Math.max(0, e));
            double az = Math.atan2(z, x);
            double wave = 0.32 + 0.08 * fbm(Math.cos(az) * 2, Math.sin(az) * 2, 3.1, 3);
            double band = Math.exp(-Math.pow((e - wave) * 7, 2)) * Math.max(0, e);
            double curtain = 0.4 + 0.6 * Math.pow(Math.abs(Math.sin(az * 9 + 3 * fbm(x * 3, z * 3, 1.7, 2))), 0.6);
            double a = band * curtain * 1.6;
            double h = clamp((e - wave) * 4 + 0.5);
            add(c, mix(new double[]{.15, 1, .6}, new double[]{.55, .35, 1}, h), a);
            return rgb(c);
        }
        @Override int horizon() { return 0x0D2B3A; }
    },
    NEBULA("Nebula") {
        @Override int color(double x, double y, double z) {
            double[] c = {.02, .01, .05};
            double n = fbm(x * 2.2 + 5, y * 2.2, z * 2.2, 5);
            double m = fbm(x * 3.1, y * 3.1 + 9, z * 3.1, 4);
            add(c, new double[]{.85, .2, .65}, Math.pow(clamp(n * 1.6 - .45), 2) * 1.3);
            add(c, new double[]{.2, .45, 1}, Math.pow(clamp(m * 1.6 - .5), 2) * 1.2);
            add(c, stars(x, y, z, 0.996, 1.0), 1);
            return rgb(c);
        }
        @Override int horizon() { return 0x1A0C33; }
    },
    SYNTHWAVE("Synthwave") {
        @Override int color(double x, double y, double z) {
            double e = y;
            double[] c = grad(e, new double[][]{{-1, .08, .01, .12}, {0, 1, .45, .35}, {.08, 1, .3, .55}, {.35, .45, .1, .6}, {1, .07, .02, .2}});
            add(c, stars(x, y, z, 0.998, 1), clamp(e * 2 - .4));
            double[] sun = norm(0, .17, -1);
            double d = Math.acos(clamp(x * sun[0] + y * sun[1] + z * sun[2], -1, 1));
            if (d < 0.22) {
                double v = (e - (sun[1] - 0.22)) / 0.44;
                boolean cut = v < 0.55 && ((int) Math.floor(v * 22)) % 2 == 0;
                if (!cut) c = mix(new double[]{1, .35, .55}, new double[]{1, .9, .35}, clamp(v * 1.3));
            }
            add(c, new double[]{1, .4, .6}, Math.exp(-d * 5) * 0.5);
            return rgb(c);
        }
        @Override int horizon() { return 0xFF6A5A; }
    },
    BLOOD_MOON("Blood Moon") {
        @Override int color(double x, double y, double z) {
            double[] c = grad(y, new double[][]{{-1, .05, 0, 0}, {0, .3, .03, .02}, {.4, .09, .01, .01}, {1, .02, 0, .01}});
            add(c, new double[]{1, .5, .45}, stars(x, y, z, 0.998, 1)[0] * clamp(y));
            double[] moon = norm(.35, .5, .8);
            double d = Math.acos(clamp(x * moon[0] + y * moon[1] + z * moon[2], -1, 1));
            if (d < 0.12) {
                double crater = fbm(x * 30, y * 30, z * 30, 3);
                c = mix(new double[]{.95, .2, .12}, new double[]{.6, .05, .04}, crater);
            } else add(c, new double[]{1, .15, .08}, Math.exp(-(d - .12) * 9) * .8);
            return rgb(c);
        }
        @Override int horizon() { return 0x4A0806; }
    },
    DEEP_SPACE("Deep Space") {
        @Override int color(double x, double y, double z) {
            double[] c = {.005, .006, .015};
            double[] nrm = norm(.3, .8, .5);
            double p = x * nrm[0] + y * nrm[1] + z * nrm[2];
            double band = Math.exp(-p * p * 18);
            double dust = fbm(x * 4, y * 4, z * 4, 5);
            add(c, mix(new double[]{.55, .6, .85}, new double[]{1, .75, .5}, dust), band * (.35 + .9 * dust));
            add(c, new double[]{.05, .03, .02}, -band * clamp(fbm(x * 9, y * 9, z * 9, 3) * 2 - 1) * 4);
            add(c, stars(x, y, z, 0.994 - band * 0.01, 1.1), 1);
            return rgb(c);
        }
        @Override int horizon() { return 0x05060F; }
    },
    COTTON_CANDY("Cotton Candy") {
        @Override int color(double x, double y, double z) {
            double[] c = grad(y, new double[][]{{-1, .95, .75, .85}, {0, 1, .78, .9}, {.3, .78, .82, 1}, {1, .55, .72, 1}});
            double cl = fbm(x / (y + 1.2) * 2.5, 0.3, z / (y + 1.2) * 2.5, 5);
            double a = clamp((cl - .45) * 3) * clamp(y * 6);
            add(c, new double[]{1, .97, 1}, a * .85 - 0);
            return rgb(c);
        }
        @Override int horizon() { return 0xFFC8E6; }
    },
    GOLDEN_HOUR("Golden Hour") {
        @Override int color(double x, double y, double z) {
            double[] c = grad(y, new double[][]{{-1, .35, .2, .12}, {0, 1, .72, .42}, {.25, .55, .7, .9}, {1, .28, .48, .82}});
            double[] sun = norm(-1, .08, .25);
            double d = Math.acos(clamp(x * sun[0] + y * sun[1] + z * sun[2], -1, 1));
            add(c, new double[]{1, .85, .55}, Math.exp(-d * 3.5) * .9);
            if (d < 0.05) c = new double[]{1, .97, .85};
            double cl = fbm(x / (y + 1.1) * 3, 1.3, z / (y + 1.1) * 3, 5);
            double a = clamp((cl - .5) * 3) * clamp(y * 5);
            c = mix(c, mix(new double[]{1, .6, .4}, new double[]{1, .9, .8}, Math.exp(-d)), a * .8);
            return rgb(c);
        }
        @Override int horizon() { return 0xF7B57A; }
    },
    VOID("Void") {
        @Override int color(double x, double y, double z) {
            double[] c = {.01, .0, .02};
            add(c, new double[]{.7, .5, 1}, stars(x, y, z, 0.997, 1)[0]);
            double warp = fbm(x * 3, y * 3, z * 3, 4) - .5;
            double p = z + warp * .35;
            double rift = Math.exp(-p * p * 220) * clamp(y + .3);
            add(c, new double[]{.95, .45, 1}, rift * 1.4);
            add(c, new double[]{.45, .1, .7}, Math.exp(-p * p * 14) * .35 * clamp(y + .3));
            return rgb(c);
        }
        @Override int horizon() { return 0x12041F; }
    },
    /** Golden Hour while the sun is up, Aurora at night, fading between them at dusk and dawn */
    DYNAMIC("Dynamic (day / night)") {
        @Override int color(double x, double y, double z) { return colorAt(x, y, z, night); }
        @Override int colorAt(double x, double y, double z, double n) {
            if (n <= 0.001) return GOLDEN_HOUR.color(x, y, z);
            if (n >= 0.999) return AURORA.color(x, y, z);
            int a = GOLDEN_HOUR.color(x, y, z), b = AURORA.color(x, y, z);
            return lerpRgb(a, b, n);
        }
        @Override int horizon() { return lerpRgb(GOLDEN_HOUR.horizon(), AURORA.horizon(), night) & 0xFFFFFF; }
    };

    /** how much it is night right now (0 = day, 1 = night) — set by the sky renderer for the Dynamic sky */
    public static volatile double night;
    public boolean dynamic() { return this == DYNAMIC; }

    public final String title;
    SkyPreset(String title) { this.title = title; }

    /** ARGB colour for a normalised direction. */
    abstract int color(double x, double y, double z);
    /** the colour for a given amount of night (only the Dynamic sky changes with it) */
    int colorAt(double x, double y, double z, double night) { return color(x, y, z); }
    /** Colour used to tint the fog so the horizon blends with the sky. */
    abstract int horizon();
    public int horizonColor() { return horizon(); }
    static int lerpRgb(int a, int b, double t) {
        int r = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t), g = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t), bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
        return (a & 0xFF000000) | (r << 16) | (g << 8) | bl;
    }

    public static SkyPreset byTitle(String t) { for (SkyPreset p : values()) if (p.title.equals(t)) return p; return AURORA; }

    // ------------------------------------------------------------------ maths helpers
    static double clamp(double v) { return clamp(v, 0, 1); }
    static double clamp(double v, double a, double b) { return v < a ? a : Math.min(v, b); }
    static double[] norm(double x, double y, double z) { double l = Math.sqrt(x * x + y * y + z * z); return new double[]{x / l, y / l, z / l}; }
    static double[] mix(double[] a, double[] b, double t) { t = clamp(t); return new double[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t}; }
    static void add(double[] c, double[] d, double k) { c[0] += d[0] * k; c[1] += d[1] * k; c[2] += d[2] * k; }
    static int rgb(double[] c) {
        // soft tonemap so additive glows don't clip harshly
        int r = (int) (255 * clamp(c[0] / (1 + Math.max(0, c[0] - 1)))), g = (int) (255 * clamp(c[1] / (1 + Math.max(0, c[1] - 1)))), b = (int) (255 * clamp(c[2] / (1 + Math.max(0, c[2] - 1))));
        return 0xFF000000 | r << 16 | g << 8 | b;
    }
    /** Piecewise gradient by elevation: rows are {elevation, r, g, b}. */
    static double[] grad(double e, double[][] stops) {
        if (e <= stops[0][0]) return new double[]{stops[0][1], stops[0][2], stops[0][3]};
        for (int i = 1; i < stops.length; i++) if (e <= stops[i][0]) {
            double t = (e - stops[i - 1][0]) / (stops[i][0] - stops[i - 1][0]);
            t = t * t * (3 - 2 * t);
            return mix(new double[]{stops[i - 1][1], stops[i - 1][2], stops[i - 1][3]}, new double[]{stops[i][1], stops[i][2], stops[i][3]}, t);
        }
        double[] l = stops[stops.length - 1];
        return new double[]{l[1], l[2], l[3]};
    }
    static double hash(int x, int y, int z) {
        int h = x * 374761393 + y * 668265263 + z * 1274126177;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0x7FFFFFFF) / (double) 0x7FFFFFFF;
    }
    static double noise(double x, double y, double z) {
        int xi = (int) Math.floor(x), yi = (int) Math.floor(y), zi = (int) Math.floor(z);
        double xf = x - xi, yf = y - yi, zf = z - zi;
        double u = xf * xf * (3 - 2 * xf), v = yf * yf * (3 - 2 * yf), w = zf * zf * (3 - 2 * zf);
        double a = lerp(hash(xi, yi, zi), hash(xi + 1, yi, zi), u), b = lerp(hash(xi, yi + 1, zi), hash(xi + 1, yi + 1, zi), u);
        double c = lerp(hash(xi, yi, zi + 1), hash(xi + 1, yi, zi + 1), u), d = lerp(hash(xi, yi + 1, zi + 1), hash(xi + 1, yi + 1, zi + 1), u);
        return lerp(lerp(a, b, v), lerp(c, d, v), w);
    }
    static double fbm(double x, double y, double z, int oct) {
        double s = 0, amp = 0.5, f = 1, norm = 0;
        for (int i = 0; i < oct; i++) { s += amp * noise(x * f, y * f, z * f); norm += amp; amp *= 0.5; f *= 2.03; }
        return s / norm;
    }
    static double lerp(double a, double b, double t) { return a + (b - a) * t; }
    /** Sparse twinkling-free star field: returns an RGB contribution. */
    static double[] stars(double x, double y, double z, double threshold, double intensity) {
        double k = 420;
        int xi = (int) Math.floor(x * k), yi = (int) Math.floor(y * k), zi = (int) Math.floor(z * k);
        double h = hash(xi, yi, zi);
        if (h < threshold) return new double[]{0, 0, 0};
        double b = (h - threshold) / (1 - threshold) * intensity;
        double t = hash(zi, xi, yi);
        return new double[]{b * (0.8 + 0.2 * t), b * 0.9, b * (1.0 - 0.2 * t + 0.1)};
    }
}
