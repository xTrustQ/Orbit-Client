package dev.orbit.client.troll;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.orbit.client.OrbitClient;
import dev.orbit.client.mixin.LimbAnimatorAccessor;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Troll Mod: change how players look — on YOUR screen only. A player can be shown as any entity
 * (with their name tag on top) or be completely invisible except for the name tag.
 * Saved in config/orbit-troll.json: player name (lower case) or "*" for everyone -> prank.
 */
public final class Troll {
    private Troll() {}

    public enum Kind { NONE, INVISIBLE, DISGUISE }
    public static final class Prank {
        public Kind kind = Kind.NONE;
        public String entity = "minecraft:pig";
        public Prank() {}
        public Prank(Kind kind, String entity) { this.kind = kind; this.entity = entity; }
    }

    public static final String EVERYONE = "*";
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("orbit-troll.json");
    private static Map<String, Prank> pranks;
    private static final Map<UUID, Entity> FAKES = new HashMap<>();
    private static final Set<String> BROKEN = new HashSet<>();
    /** entity types that can't stand in for a player */
    private static final Set<String> EXCLUDED = Set.of("minecraft:player", "minecraft:mannequin", "minecraft:fishing_bobber", "minecraft:marker",
            "minecraft:area_effect_cloud", "minecraft:interaction", "minecraft:block_display", "minecraft:item_display", "minecraft:text_display",
            "minecraft:painting", "minecraft:item", "minecraft:falling_block", "minecraft:lightning_bolt", "minecraft:ender_dragon",
            "minecraft:experience_orb", "minecraft:leash_knot", "minecraft:evoker_fangs", "minecraft:llama_spit", "minecraft:shulker_bullet",
            "minecraft:ominous_item_spawner", "minecraft:eye_of_ender", "minecraft:wind_charge", "minecraft:breeze_wind_charge");

    private static Map<String, Prank> data() {
        if (pranks == null) {
            try { if (Files.exists(FILE)) pranks = new Gson().fromJson(Files.readString(FILE), new TypeToken<LinkedHashMap<String, Prank>>() {}.getType()); }
            catch (Exception e) { OrbitClient.LOG.warn("Could not read troll settings", e); }
            if (pranks == null) pranks = new LinkedHashMap<>();
        }
        return pranks;
    }
    private static void save() {
        try { Files.createDirectories(FILE.getParent()); Files.writeString(FILE, new Gson().toJson(data())); }
        catch (Exception e) { OrbitClient.LOG.warn("Could not save troll settings", e); }
    }

    public static boolean enabled() { return OrbitClient.modules() != null && OrbitClient.modules().get("troll") instanceof dev.orbit.client.module.NewModules.TrollModule m && m.isEnabled(); }
    private static boolean includeMe() { return OrbitClient.modules().get("troll") instanceof dev.orbit.client.module.NewModules.TrollModule m && m.self.on(); }

    /** the prank set for exactly this key (player name or "*"), never null */
    public static Prank of(String key) { Prank p = data().get(key.toLowerCase(Locale.ROOT)); return p == null ? new Prank() : p; }
    public static void set(String key, Kind kind, String entity) {
        key = key.toLowerCase(Locale.ROOT);
        if (kind == Kind.NONE) data().remove(key); else data().put(key, new Prank(kind, entity == null ? of(key).entity : entity));
        FAKES.clear();
        save();
    }
    public static void clearAll() { data().clear(); FAKES.clear(); save(); }
    public static int count() { return data().size(); }
    public static Set<String> keys() { return new LinkedHashSet<>(data().keySet()); }

    /** what to do with this player right now (their own prank wins over "everyone") */
    public static Prank prankFor(AbstractClientPlayerEntity p) {
        if (!enabled()) return null;
        var mc = MinecraftClient.getInstance();
        Prank own = data().get(p.getGameProfile().name().toLowerCase(Locale.ROOT));
        // a prank set on your own name always applies to you (see it in F5); "Everyone else" only with the setting
        Prank pr = own != null ? own : p == mc.player ? (includeMe() ? data().get(EVERYONE) : null) : data().get(EVERYONE);
        return pr == null || pr.kind == Kind.NONE ? null : pr;
    }

    public static boolean usable(String id) { return !EXCLUDED.contains(id) && !BROKEN.contains(id); }
    public static void broken(String id) { BROKEN.add(id); FAKES.clear(); OrbitClient.LOG.warn("Troll Mod: {} can't be used as a disguise", id); }

    /** a stand-in entity for this player, moved to exactly where the player is */
    public static Entity fakeFor(AbstractClientPlayerEntity p, String typeId) {
        if (!usable(typeId)) return null;
        var mc = MinecraftClient.getInstance();
        Entity fake = FAKES.get(p.getUuid());
        if (fake == null || fake.getEntityWorld() != mc.world || !Registries.ENTITY_TYPE.getId(fake.getType()).toString().equals(typeId)) {
            Identifier id = Identifier.tryParse(typeId);
            EntityType<?> type = id == null ? null : Registries.ENTITY_TYPE.getOptionalValue(id).orElse(null);
            if (type == null || mc.world == null) return null;
            try { fake = type.create(mc.world, SpawnReason.LOAD); } catch (Throwable t) { broken(typeId); return null; }
            if (fake == null) { broken(typeId); return null; }
            fake.setId(-1_000_000 - Math.abs(p.getId())); // never collides with real entities
            FAKES.put(p.getUuid(), fake);
        }
        sync(fake, p, p == mc.player);
        return fake;
    }

    private static void sync(Entity f, AbstractClientPlayerEntity p, boolean me) {
        f.setPosition(p.getX(), p.getY(), p.getZ());
        f.lastX = p.lastX; f.lastY = p.lastY; f.lastZ = p.lastZ;
        f.lastRenderX = p.lastRenderX; f.lastRenderY = p.lastRenderY; f.lastRenderZ = p.lastRenderZ;
        f.setYaw(p.getYaw()); f.lastYaw = p.lastYaw;
        f.setPitch(p.getPitch()); f.lastPitch = p.lastPitch;
        f.age = p.age;
        f.setOnGround(p.isOnGround());
        f.setSneaking(p.isSneaking());
        f.setCustomName(p.getDisplayName());
        f.setCustomNameVisible(!me && !hasServerTag(p));
        if (f instanceof LivingEntity l) {
            l.bodyYaw = p.bodyYaw; l.lastBodyYaw = p.lastBodyYaw;
            l.headYaw = p.headYaw; l.lastHeadYaw = p.lastHeadYaw;
            l.hurtTime = p.hurtTime;
            l.deathTime = p.deathTime;
            l.handSwingProgress = p.handSwingProgress; l.lastHandSwingProgress = p.lastHandSwingProgress;
            LimbAnimatorAccessor to = (LimbAnimatorAccessor) l.limbAnimator, from = (LimbAnimatorAccessor) p.limbAnimator;
            to.orbit$setSpeed(from.orbit$getSpeed());
            to.orbit$setLastSpeed(from.orbit$getLastSpeed());
            to.orbit$setAnimationProgress(from.orbit$getAnimationProgress());
        }
    }

    /**
     * If this entity is a server-made name tag (servers often show name + health with a text display or an
     * invisible armor stand riding the player, or floating right above them), the pranked player it belongs to.
     */
    public static AbstractClientPlayerEntity tagOwner(Entity e) {
        if (!enabled() || e instanceof net.minecraft.entity.player.PlayerEntity) return null;
        if (e.getRootVehicle() instanceof AbstractClientPlayerEntity p && e != p) return prankFor(p) != null ? p : null;
        boolean tagLike = e instanceof net.minecraft.entity.decoration.DisplayEntity.TextDisplayEntity
                || (e instanceof net.minecraft.entity.decoration.ArmorStandEntity a && a.isInvisible());
        if (!tagLike) return null;
        var world = MinecraftClient.getInstance().world;
        if (world == null) return null;
        for (AbstractClientPlayerEntity p : world.getPlayers()) {
            double dx = e.getX() - p.getX(), dz = e.getZ() - p.getZ(), dy = e.getY() - p.getY();
            if (dx * dx + dz * dz < 0.36 && dy > 0.3 && dy < 3.2 && prankFor(p) != null) return p;
        }
        return null;
    }
    private static final Map<UUID, Long> SERVER_TAGS = new HashMap<>();
    public static void sawServerTag(AbstractClientPlayerEntity p) { SERVER_TAGS.put(p.getUuid(), System.currentTimeMillis()); }
    /** the server shows its own name tag for this player (so the disguise doesn't add a second one) */
    public static boolean hasServerTag(AbstractClientPlayerEntity p) { Long t = SERVER_TAGS.get(p.getUuid()); return t != null && System.currentTimeMillis() - t < 1500; }
    /** how far to lift / lower the server's tag so it sits on the disguise's head */
    public static double headOffset(AbstractClientPlayerEntity p) {
        Prank pr = prankFor(p);
        if (pr == null || pr.kind != Kind.DISGUISE) return 0;
        Entity fake = FAKES.get(p.getUuid());
        return fake == null ? 0 : fake.getHeight() - p.getHeight() + (fake.getHeight() < p.getHeight() ? 0.3 : 0.1); // a little gap above the head
    }
    /** Disguised players sound like their mob when they get hurt (Troll Mod setting). */
    public static net.minecraft.sound.SoundEvent hurtSound(LivingEntity self, net.minecraft.entity.damage.DamageSource source, net.minecraft.sound.SoundEvent original) {
        if (!(self instanceof AbstractClientPlayerEntity p) || !(OrbitClient.modules().get("troll") instanceof dev.orbit.client.module.NewModules.TrollModule m) || !m.mobSounds.on()) return original;
        Prank pr = prankFor(p);
        if (pr == null || pr.kind != Kind.DISGUISE) return original;
        if (pr.kind == Kind.INVISIBLE) return original;
        Entity fake = fakeFor(p, pr.entity);
        if (!(fake instanceof LivingEntity l)) return original;
        try {
            var s = ((dev.orbit.client.mixin.LivingEntityInvoker) l).orbit$hurtSound(source);
            return s != null ? s : original;
        } catch (Throwable t) { return original; }
    }
    /** the disguise mob's hurt/death sound for a player sound sent by the server, or null to keep it */
    public static net.minecraft.sound.SoundEvent mobSound(AbstractClientPlayerEntity p, String playerSoundPath) {
        if (!(OrbitClient.modules().get("troll") instanceof dev.orbit.client.module.NewModules.TrollModule m) || !m.mobSounds.on()) return null;
        Prank pr = prankFor(p);
        if (pr == null) return null;
        if (pr.kind == Kind.INVISIBLE) return null;
        Entity fake = fakeFor(p, pr.entity);
        if (!(fake instanceof LivingEntity l)) return null;
        try {
            var inv = (dev.orbit.client.mixin.LivingEntityInvoker) l;
            if (playerSoundPath.equals("entity.player.death")) return inv.orbit$deathSound();
            return inv.orbit$hurtSound(MinecraftClient.getInstance().world.getDamageSources().generic());
        } catch (Throwable t) { return null; }
    }
    /** every entity type you can pick, sorted by name */
    public static List<EntityType<?>> choices() {
        List<EntityType<?>> l = new ArrayList<>();
        for (EntityType<?> t : Registries.ENTITY_TYPE) if (usable(Registries.ENTITY_TYPE.getId(t).toString())) l.add(t);
        l.sort(Comparator.comparing(t -> t.getName().getString()));
        return l;
    }
    public static String name(String typeId) {
        Identifier id = Identifier.tryParse(typeId);
        return id == null ? typeId : Registries.ENTITY_TYPE.getOptionalValue(id).map(t -> t.getName().getString()).orElse(typeId);
    }
}
