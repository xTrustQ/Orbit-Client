package dev.orbit.client.module;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.orbit.client.OrbitClient;
import dev.orbit.client.module.Module.Category;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ModuleManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int CONFIG_VERSION = 5;
    private final List<Module> modules = new ArrayList<>();
    private final Map<String, Module> byId = new HashMap<>();
    private final Path file = FabricLoader.getInstance().getConfigDir().resolve("orbit.json");

    private final FeatureModules.Zoom zoom;
    private final FeatureModules.SkyChanger sky;
    private final FeatureModules.Capes capes;
    private final FeatureModules.Interface ui;
    private final NewModules.Freelook freelook;
    private final NewModules.Crosshair crosshair;
    private final NewModules.Saturation saturation;
    private final EmotesModule emotes;

    public ModuleManager() {
        // Render
        sky = add(new FeatureModules.SkyChanger(), Items.ENDER_EYE, Category.RENDER);
        add(new FeatureModules.Fullbright(), Items.GLOWSTONE_DUST, Category.RENDER);
        add(new NewModules.NoFog(), Items.FEATHER, Category.RENDER);
        add(new NewModules.TimeChanger(), Items.CLOCK, Category.RENDER);
        add(new MotionBlur(), Items.SUGAR, Category.RENDER);
        add(new NewModules.OwnNametag(), Items.NAME_TAG, Category.RENDER);
        add(new NewModules.ChunkBorders(), Items.GRASS_BLOCK, Category.RENDER);
        add(new NewModules.TntTimer(), Items.TNT, Category.RENDER);
        add(new NewModules.ItemScale(), Items.APPLE, Category.RENDER);
        add(new NewModules.HideArmor(), Items.IRON_CHESTPLATE, Category.RENDER);
        add(new NewModules.TrollModule(), Items.CARVED_PUMPKIN, Category.PLAYER);
        add(new NewModules.DeathAnimationsModule(), Items.TOTEM_OF_UNDYING, Category.RENDER);
        add(new NewModules.Hitboxes(), Items.GLASS, Category.COMBAT);
        add(new NewModules.ReachDisplay(), Items.STICK, Category.COMBAT);
        add(new NewModules.ItemPhysics(), Items.DIAMOND, Category.RENDER);
        add(new NewModules.TotemCounter(), Items.TOTEM_OF_UNDYING, Category.COMBAT);
        add(new NewModules.WeatherChanger(), Items.WATER_BUCKET, Category.RENDER);
        add(new PvpModules.KillEffectsModule(), Items.NETHER_STAR, Category.COMBAT);
        add(new PvpModules.TargetHud(), Items.PLAYER_HEAD, Category.COMBAT);
        add(new PvpModules.ComboCounter(), Items.BLAZE_POWDER, Category.COMBAT);
        add(new PvpModules.HitDelay(), Items.CLOCK, Category.COMBAT);
        add(new PvpModules.PearlTracker(), Items.ENDER_PEARL, Category.COMBAT);
        add(new PvpModules.WTapCounter(), Items.FEATHER, Category.COMBAT);
        add(new ClipsModule(), Items.RECOVERY_COMPASS, Category.PLAYER);
        add(new PvpModules.FightStats(), Items.WRITABLE_BOOK, Category.COMBAT);
        add(new PvpModules.FightTimeline(), Items.RECOVERY_COMPASS, Category.COMBAT);
        add(new PvpModules.DamageNumbers(), Items.REDSTONE, Category.COMBAT);
        add(new PvpModules.HitColors(), Items.RED_DYE, Category.COMBAT);
        add(new PvpModules.ProjectilePredictor(), Items.SNOWBALL, Category.COMBAT);
        add(new PvpModules.MovementTrails(), Items.FIREWORK_STAR, Category.RENDER);
        add(new PvpModules.ChatBubbles(), Items.PAPER, Category.PLAYER);
        add(new PvpModules.InventoryProfiles(), Items.CHEST, Category.PLAYER);
        add(new PvpModules.SessionRecap(), Items.MAP, Category.CLIENT);
        // Combat
        add(new HudModules.Armor(), Items.DIAMOND_CHESTPLATE, Category.COMBAT);
        add(new HudModules.Potions(), Items.POTION, Category.COMBAT);
        add(new HudModules.Cps(), Items.STONE_BUTTON, Category.COMBAT);
        add(new HudModules.Keystrokes(), Items.OAK_BUTTON, Category.COMBAT);
        crosshair = add(new NewModules.Crosshair(), Items.TARGET, Category.COMBAT);
        add(new NewModules.HurtCam(), Items.REDSTONE, Category.COMBAT);
        saturation = add(new NewModules.Saturation(), Items.GOLDEN_CARROT, Category.COMBAT);
        add(new NewModules.ShieldStatus(), Items.SHIELD, Category.COMBAT);
        // HUD
        add(new HudModules.Fps(), Items.REPEATER, Category.HUD);
        add(new HudModules.Coords(), Items.COMPASS, Category.HUD);
        add(new HudModules.Direction(), Items.RECOVERY_COMPASS, Category.HUD);
        add(new HudModules.Ping(), Items.ENDER_PEARL, Category.HUD);
        add(new HudModules.Clock(), Items.CLOCK, Category.HUD);
        add(new HudModules.Memory(), Items.REDSTONE, Category.HUD);
        add(new HudModules.Speed(), Items.LEATHER_BOOTS, Category.HUD);
        add(new HudModules.Server(), Items.OAK_SIGN, Category.HUD);
        add(new HudModules.Day(), Items.SUNFLOWER, Category.HUD);
        add(new NewModules.Scoreboard(), Items.WRITABLE_BOOK, Category.HUD);
        add(new SpotifyWidget(), Items.JUKEBOX, Category.HUD);
        add(new LyricsModule(), Items.MUSIC_DISC_CAT, Category.HUD);
        // Player
        zoom = add(new FeatureModules.Zoom(), Items.SPYGLASS, Category.PLAYER);
        freelook = add(new NewModules.Freelook(), Items.ENDER_EYE, Category.PLAYER);
        add(new NewModules.NameSkinsModule(), Items.PLAYER_HEAD, Category.PLAYER);
        add(new NewModules.Cosmetics(), Items.ELYTRA, Category.PLAYER);
        add(new NewModules.Badge(), Items.ENDER_EYE, Category.PLAYER);
        autoReconnect = add(new NewModules.AutoReconnect(), Items.REPEATER, Category.PLAYER);
        waypoints = add(new NewModules.WaypointsModule(), Items.LODESTONE, Category.PLAYER);
        add(new NewModules.GameTime(), Items.CLOCK, Category.HUD);
        emotes = add(new EmotesModule(), Items.ARMOR_STAND, Category.PLAYER);
        add(new FeatureModules.ToggleSprint(), Items.SUGAR, Category.PLAYER);
        capes = add(new FeatureModules.Capes(), Items.WHITE_BANNER, Category.PLAYER);
        // Settings
        ui = add(new FeatureModules.Interface(), Items.COMPARATOR, Category.CLIENT);
    }

    private <M extends Module> M add(M m, Item icon, Category cat) {
        m.icon(icon).in(cat);
        modules.add(m);
        byId.put(m.id, m);
        return m;
    }

    public List<Module> all() { return modules; }
    public Module get(String id) { return byId.get(id); }
    public List<HudModule> hud() {
        List<HudModule> l = new ArrayList<>();
        for (Module m : modules) if (m instanceof HudModule h) l.add(h);
        return l;
    }
    public FeatureModules.Zoom zoom() { return zoom; }
    public FeatureModules.SkyChanger sky() { return sky; }
    public FeatureModules.Capes capes() { return capes; }
    public FeatureModules.Interface ui() { return ui; }
    public NewModules.Freelook freelook() { return freelook; }
    public NewModules.Crosshair crosshair() { return crosshair; }
    public NewModules.Saturation saturation() { return saturation; }
    private NewModules.AutoReconnect autoReconnect;
    private NewModules.WaypointsModule waypoints;
    public NewModules.AutoReconnect autoReconnect() { return autoReconnect; }
    public NewModules.WaypointsModule waypoints() { return waypoints; }
    public EmotesModule emotes() { return emotes; }

    public void tick() { for (Module m : modules) if (m.isEnabled()) m.tick(); }

    /** true when this PC had no Orbit settings yet (a new install) — Settings sync then restores yours */
    public boolean fresh;

    public void load() {
        if (!Files.exists(file)) { fresh = true; return; }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            for (Module m : modules) if (root.has(m.id)) m.load(root.getAsJsonObject(m.id));
            int version = root.has("_version") ? root.get("_version").getAsInt() : 1;
            if (version < 2) sky.setEnabled(false); // v1 menus switched the in-game sky on by themselves
            if (version < 4) ui().smoothFont.set(false); // v4: Minecraft font by default
            if (version < 5 && get("potions") instanceof HudModules.Potions pot) pot.style.set("Tiles"); // new tile look
            if (version < 3) crosshair().outline.set(false); // v2 drew a dark outline round the crosshair by default
        } catch (Exception e) {
            OrbitClient.LOG.warn("Could not read {} — using defaults", file, e);
        }
    }

    /** every module's settings as one JSON object (for configs) */
    public JsonObject snapshot() {
        JsonObject root = new JsonObject();
        root.addProperty("_version", CONFIG_VERSION);
        for (Module m : modules) root.add(m.id, m.save());
        return root;
    }
    /** applies a snapshot (modules missing from it keep their settings) and saves */
    public void apply(JsonObject root) {
        for (Module m : modules) if (root.has(m.id) && root.get(m.id).isJsonObject()) {
            boolean was = m.isEnabled();
            m.load(root.getAsJsonObject(m.id));
            boolean now = m.isEnabled();
            if (was != now) { m.setEnabled(!now); m.setEnabled(now); } // run enable/disable hooks
        }
        save();
    }

    public void save() {
        try {
            JsonObject root = new JsonObject();
            root.addProperty("_version", CONFIG_VERSION);
            for (Module m : modules) root.add(m.id, m.save());
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(root));
        } catch (Exception e) {
            OrbitClient.LOG.warn("Could not save {}", file, e);
        }
    }
}
