package dev.orbit.client.gui;

import dev.orbit.client.OrbitClient;
import dev.orbit.client.compat.Compat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/** Loads PNG bytes / data URLs into GUI textures (cached by key). Render thread only. */
public final class Tex {
    private Tex() {}
    private static final Map<String, Identifier> CACHE = new HashMap<>();
    private static int counter;

    public static Identifier get(String key) { return CACHE.get(key); }

    public static Identifier fromDataUrl(String key, String dataUrl, boolean smooth) {
        if (dataUrl == null) return null;
        int i = dataUrl.indexOf(',');
        try { return fromPng(key, Base64.getDecoder().decode(i >= 0 ? dataUrl.substring(i + 1) : dataUrl), smooth); }
        catch (IllegalArgumentException e) { return null; }
    }

    public static Identifier fromPng(String key, byte[] png, boolean smooth) {
        Identifier hit = CACHE.get(key);
        if (hit != null) return hit;
        try {
            NativeImage img = NativeImage.read(png);
            Identifier id = Compat.id("dyn/" + (counter++));
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, Compat.texture("orbit-" + key, img, smooth));
            CACHE.put(key, id);
            return id;
        } catch (Exception e) {
            OrbitClient.LOG.debug("Could not load texture {}", key, e);
            return null;
        }
    }

    public static void forget(String key) {
        Identifier id = CACHE.remove(key);
        if (id != null) MinecraftClient.getInstance().getTextureManager().destroyTexture(id);
    }
}
