package dev.orbit.client.itemscale;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.orbit.client.OrbitClient;
import dev.orbit.client.module.NewModules;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Item Scale's chosen items (config/orbit-itemscale.json): item id -> own size (0 = use the normal size).
 * Also works out the final size for an item in a given place.
 */
public final class ItemScaleItems {
    private ItemScaleItems() {}

    public enum Where { DROPPED, FIRST_PERSON, THIRD_PERSON }

    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("orbit-itemscale.json");
    private static Map<String, Float> chosen;

    public static Map<String, Float> chosen() {
        if (chosen == null) {
            try { if (Files.exists(FILE)) chosen = new Gson().fromJson(Files.readString(FILE), new TypeToken<LinkedHashMap<String, Float>>() {}.getType()); }
            catch (Exception e) { OrbitClient.LOG.warn("Could not read item scale items", e); }
            if (chosen == null) chosen = new LinkedHashMap<>();
        }
        return chosen;
    }
    public static void save() {
        try { Files.createDirectories(FILE.getParent()); Files.writeString(FILE, new Gson().toJson(chosen())); }
        catch (Exception e) { OrbitClient.LOG.warn("Could not save item scale items", e); }
    }

    public static boolean isChosen(String id) { return chosen().containsKey(id); }
    public static void toggle(String id) { if (chosen().remove(id) == null) chosen().put(id, 0f); save(); }
    /** own size for a chosen item, 0 = none */
    public static float own(String id) { Float f = chosen().get(id); return f == null ? 0 : f; }
    public static void setOwn(String id, float size) { chosen().put(id, Math.max(0, Math.round(size * 20) / 20f)); save(); }

    private static NewModules.ItemScale module() {
        return OrbitClient.modules() != null && OrbitClient.modules().get("itemscale") instanceof NewModules.ItemScale m && m.isEnabled() ? m : null;
    }

    /** final size of this item here (1 = normal) */
    public static float factor(ItemStack stack, Where where) {
        NewModules.ItemScale m = module();
        if (m == null || stack == null || stack.isEmpty()) return 1f;
        float base = switch (where) {
            case DROPPED -> m.dropped.on() ? m.droppedSize.f() : 1f;
            case FIRST_PERSON -> m.held.on() ? m.heldSize.f() : 1f;
            case THIRD_PERSON -> m.thirdSize.f();
        };
        String id = Registries.ITEM.getId(stack.getItem()).toString();
        boolean picked = isChosen(id);
        if (m.filter.is("Only chosen items") && !picked) return 1f;
        if (m.filter.is("All except chosen") && picked) return 1f;
        float own = own(id);
        if (picked && own > 0 && !m.filter.is("All except chosen") && base != 1f) return own;
        return base;
    }

    public static boolean spin() { NewModules.ItemScale m = module(); return m == null || m.spin.on(); }
    public static boolean bob() { NewModules.ItemScale m = module(); return m == null || m.bob.on(); }
    public static boolean thirdFor(boolean mine) { NewModules.ItemScale m = module(); return m != null && (mine ? m.thirdMine.on() : m.thirdOthers.on()); }
}
