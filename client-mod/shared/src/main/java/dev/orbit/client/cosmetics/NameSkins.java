package dev.orbit.client.cosmetics;

import dev.orbit.client.OrbitClient;
import dev.orbit.client.mixin.MinecraftClientAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.Util;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Skins by username: players without a real skin (offline / cracked servers, offline accounts)
 * show the skin of the Minecraft account that has the same name. Looked up once per name, async.
 */
public final class NameSkins {
    private NameSkins() {}
    private static final Map<String, Optional<SkinTextures>> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> PENDING = new ConcurrentHashMap<>();

    /** The skin for this name if it has been loaded, else null (and starts loading it). */
    public static SkinTextures get(String name) {
        if (name == null || name.isBlank() || !name.matches("[A-Za-z0-9_]{2,16}")) return null;
        String key = name.toLowerCase(java.util.Locale.ROOT);
        Optional<SkinTextures> hit = CACHE.get(key);
        if (hit != null) return hit.orElse(null);
        if (PENDING.putIfAbsent(key, true) == null) Util.getIoWorkerExecutor().execute(() -> load(key, name));
        return null;
    }

    private static void load(String key, String name) {
        try {
            var mc = MinecraftClient.getInstance();
            var api = ((MinecraftClientAccessor) mc).orbit$apiServices();
            var id = api.profileRepository().findProfileByName(name);
            if (id.isEmpty()) { CACHE.put(key, Optional.empty()); return; }
            var result = api.sessionService().fetchProfile(id.get().id(), true);
            if (result == null) { CACHE.put(key, Optional.empty()); return; }
            mc.getSkinProvider().fetchSkinTextures(result.profile())
                    .thenAccept(t -> CACHE.put(key, t))
                    .exceptionally(e -> { CACHE.put(key, Optional.empty()); return null; });
        } catch (Exception e) {
            OrbitClient.LOG.debug("Skin lookup for {} failed", name, e);
            CACHE.put(key, Optional.empty());
        } finally {
            PENDING.remove(key);
        }
    }

    /** Forget a name so it is looked up again (e.g. after changing your skin). */
    public static void refresh(String name) { if (name != null) CACHE.remove(name.toLowerCase(java.util.Locale.ROOT)); }
}
