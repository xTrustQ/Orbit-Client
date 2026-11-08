package dev.orbit.client.cosmetics;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.orbit.client.OrbitClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import dev.orbit.client.compat.Compat;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads the cape chosen in the Orbit launcher. The launcher passes the path of its cosmetics.json
 * with -Dorbit.cosmetics; the file is re-checked every few seconds so changes apply live.
 */
public final class CapeManager {
    private static final Identifier CAPE = Compat.id("cape/self");
    private static long lastCheck, lastModified = -1;
    private static String loadedFile;
    private static boolean hasCape;

    private CapeManager() {}

    public static Identifier cape() { return hasCape && OrbitClient.modules().capes().isEnabled() ? CAPE : null; }
    /** the PNG of the cape you wear (null for none), so the cosmetics API can share it */
    public static Path currentFile() { return hasCape && OrbitClient.modules().capes().isEnabled() && loadedFile != null ? Path.of(loadedFile) : null; }
    public static boolean elytra() { return OrbitClient.modules().capes().elytra.on(); }

    public static void tick() {
        long now = System.currentTimeMillis();
        if (now - lastCheck < 3000) return;
        lastCheck = now;
        String prop = System.getProperty("orbit.cosmetics");
        if (prop == null) return;
        try {
            Path cfg = Path.of(prop);
            if (!Files.exists(cfg)) { clear(); return; }
            long mod = Files.getLastModifiedTime(cfg).toMillis();
            if (mod == lastModified) return;
            lastModified = mod;
            JsonObject o = JsonParser.parseString(Files.readString(cfg)).getAsJsonObject();
            String file = o.has("capeFile") && !o.get("capeFile").isJsonNull() ? o.get("capeFile").getAsString() : null;
            if (file == null || !Files.exists(Path.of(file))) { clear(); return; }
            if (file.equals(loadedFile) && hasCape) return;
            try (InputStream in = Files.newInputStream(Path.of(file))) {
                NativeImage img = NativeImage.read(in);
                MinecraftClient.getInstance().getTextureManager().registerTexture(CAPE, Compat.texture("orbit-cape", img, false));
            }
            loadedFile = file;
            hasCape = true;
            OrbitClient.LOG.info("Loaded Orbit cape {}", file);
        } catch (Exception e) {
            OrbitClient.LOG.warn("Could not load Orbit cape", e);
        }
    }

    private static void clear() {
        if (hasCape) MinecraftClient.getInstance().getTextureManager().destroyTexture(CAPE);
        hasCape = false;
        loadedFile = null;
    }
}
