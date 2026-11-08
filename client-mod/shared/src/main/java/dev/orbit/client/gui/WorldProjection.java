package dev.orbit.client.gui;

import net.minecraft.client.MinecraftClient;

/** Projects a world position onto the HUD (GUI coordinates) for the current camera. */
public final class WorldProjection {
    private WorldProjection() {}

    /** the real FOV this frame (sprint / speed / zoom included), falling back to the setting */
    private static double fov() {
        var mc = MinecraftClient.getInstance();
        try {
            return ((dev.orbit.client.mixin.GameRendererInvoker) mc.gameRenderer)
                    .orbit$getFov(mc.gameRenderer.getCamera(), mc.getRenderTickCounter().getTickProgress(false), true);
        } catch (Throwable t) { return mc.options.getFov().getValue(); }
    }

    /**
     * {x, y, distance, depth} on screen. With allowBehind, points behind the camera are still returned
     * (depth negative, x/y mirrored so they point the right way) — used for edge arrows. Otherwise null.
     */
    public static float[] project(double wx, double wy, double wz, int sw, int sh, boolean allowBehind) {
        var mc = MinecraftClient.getInstance();
        if (mc.gameRenderer == null) return null;
        var cam = mc.gameRenderer.getCamera();
        var cp = cam.getCameraPos();
        double dx = wx - cp.x, dy = wy - cp.y, dz = wz - cp.z;
        double yaw = Math.toRadians(cam.getYaw()), pitch = Math.toRadians(cam.getPitch());
        double fx = -Math.sin(yaw) * Math.cos(pitch), fy = -Math.sin(pitch), fz = Math.cos(yaw) * Math.cos(pitch);
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
        double ux = -rz * fy, uy = rz * fx - rx * fz, uz = rx * fy; // right x forward
        double depth = dx * fx + dy * fy + dz * fz;
        if (depth < 0.05 && !allowBehind) return null;
        double d = Math.abs(depth) < 0.05 ? 0.05 : Math.abs(depth);
        double f = (sh / 2.0) / Math.tan(Math.toRadians(fov()) / 2);
        double sx = (dx * rx + dz * rz) / d * f, sy = (dx * ux + dy * uy + dz * uz) / d * f;
        if (depth < 0) { sx = -sx; sy = -sy; }
        return new float[]{(float) (sw / 2.0 + sx), (float) (sh / 2.0 - sy), (float) Math.sqrt(dx * dx + dy * dy + dz * dz), (float) depth};
    }

    public static float[] project(double wx, double wy, double wz, int sw, int sh) { return project(wx, wy, wz, sw, sh, false); }
}
