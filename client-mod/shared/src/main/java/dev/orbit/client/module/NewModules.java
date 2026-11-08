package dev.orbit.client.module;

import dev.orbit.client.OrbitClient;
import dev.orbit.client.gui.CrosshairEditorScreen;
import dev.orbit.client.gui.Render2D;
import dev.orbit.client.module.setting.Setting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.math.MathHelper;

import java.util.List;

/** The second wave of Orbit mods (camera, crosshair, world tweaks, combat helpers). */
public final class NewModules {
    private NewModules() {}

    // ================================================================= Freelook
    public static final class Freelook extends Module {
        final Setting.Key key = add(new Setting.Key("key", "Freelook key", () -> OrbitClient.freelookKey));
        final Setting.Mode mode = add(new Setting.Mode("mode", "Key mode", "Hold", "Hold", "Toggle"));
        final Setting.Bool invert = add(new Setting.Bool("invert", "Invert vertical", false));
        final Setting.Mode view = add(new Setting.Mode("view", "Camera", "Behind", "Behind", "In front", "First person"));
        public float yaw, pitch;
        private boolean active, toggled;
        private Perspective previous;

        public Freelook() { super("freelook", "Freelook", "Look around without turning your player", Category.PLAYER, true); }

        public boolean active() { return active; }

        @Override public void tick() {
            if (mc.player == null) return;
            boolean held;
            if (mode.is("Toggle")) {
                while (OrbitClient.freelookKey.wasPressed()) toggled = !toggled;
                held = toggled;
            } else held = mc.currentScreen == null && OrbitClient.freelookKey.isPressed();
            if (held && !active) {
                active = true;
                yaw = mc.player.getYaw();
                pitch = mc.player.getPitch();
                previous = mc.options.getPerspective();
                mc.options.setPerspective(view.is("First person") ? Perspective.FIRST_PERSON : view.is("In front") ? Perspective.THIRD_PERSON_FRONT : Perspective.THIRD_PERSON_BACK);
            } else if (!held && active) {
                active = false;
                if (previous != null) mc.options.setPerspective(previous);
            }
        }
        @Override protected void onDisable() { toggled = false; if (active) { active = false; if (previous != null) mc.options.setPerspective(previous); } }
        /** For tests: start/stop freelook as if the key were toggled. */
        public void forceToggle(boolean on) { mode.set("Toggle"); toggled = on; tick(); }

        /** From the mouse mixin: returns true when the turn was consumed by freelook. */
        public boolean turn(double dx, double dy) {
            if (!active || !isEnabled()) return false;
            yaw += (float) dx * 0.15f;
            pitch = MathHelper.clamp(pitch + (float) dy * 0.15f * (invert.on() ? -1 : 1), -90, 90);
            return true;
        }
    }

    // ================================================================= Crosshair
    public static final class Crosshair extends Module {
        public static final int GRID = 15;
        public final Setting.Mode style = add(new Setting.Mode("style", "Style", "Cross", "Cross", "Plus gap", "Dot", "Circle", "Custom"));
        public final Setting.Slider size = (Setting.Slider) add(new Setting.Slider("size", "Size", 5, 1, 12, 1, "")).visibleWhen(() -> !style.is("Custom") && !style.is("Dot"));
        public final Setting.Slider thickness = (Setting.Slider) add(new Setting.Slider("thickness", "Thickness", 1, 1, 4, 1, "")).visibleWhen(() -> !style.is("Custom"));
        public final Setting.Slider gap = (Setting.Slider) add(new Setting.Slider("gap", "Gap", 2, 0, 8, 1, "")).visibleWhen(() -> style.is("Plus gap"));
        public final Setting.Slider scale = (Setting.Slider) add(new Setting.Slider("scale", "Custom scale", 1, 1, 3, 1, "x")).visibleWhen(() -> style.is("Custom"));
        public final Setting.Color color = add(new Setting.Color("color", "Color", 0xFFFFFFFF));
        public final Setting.Bool outline = add(new Setting.Bool("outline", "Dark outline (easier to see on snow)", false));
        public final Setting.Bool centerDot = (Setting.Bool) add(new Setting.Bool("dot", "Center dot", false)).visibleWhen(() -> !style.is("Custom") && !style.is("Dot"));
        public final Setting.Bool thirdPerson = add(new Setting.Bool("third", "Show in third person", false));
        public final Setting.Pixels pixels = (Setting.Pixels) add(new Setting.Pixels("pixels", "Painted crosshair", GRID, defaultPixels())).visibleWhen(() -> false);
        public final Setting.Action paint = add(new Setting.Action("paint", "Paint your own", "Open painter", () -> mc.setScreen(new CrosshairEditorScreen(mc.currentScreen, this))));

        public Crosshair() { super("crosshair", "Crosshair", "Pick a style or paint your own pixel crosshair", Category.COMBAT, false); }

        private static boolean[] defaultPixels() {
            boolean[] p = new boolean[GRID * GRID];
            int c = GRID / 2;
            for (int i = 2; i < GRID - 2; i++) { if (Math.abs(i - c) > 1) { p[c * GRID + i] = true; p[i * GRID + c] = true; } }
            p[c * GRID + c] = true;
            return p;
        }

        public boolean replacesVanilla() {
            return isEnabled() && (mc.options.getPerspective().isFirstPerson() || thirdPerson.on());
        }

        public final Setting.Bool dynamic = add(new Setting.Bool("dynamic", "Dynamic: changes with what you're doing", true));
        public final Setting.Color targetColor = (Setting.Color) add(new Setting.Color("targetColor", "Colour when aiming at a player / mob", 0xFFFF4D5E)).visibleWhen(() -> this.dynamic.on());
        private long hitPulseAt;
        public void render(DrawContext c, int sw, int sh) {
            if (!replacesVanilla() || mc.options.hudHidden) return;
            int cx = sw / 2, cy = sh / 2, col = color.argb(), t = thickness.i(), s = size.i();
            if (dynamic.on() && mc.player != null) {
                // aiming at something you can hit -> target colour; just hit -> a quick spread; recharging -> faded
                if (mc.targetedEntity instanceof net.minecraft.entity.LivingEntity) col = targetColor.argb();
                if (dev.orbit.client.pvp.Combat.lastHitAt > hitPulseAt) hitPulseAt = dev.orbit.client.pvp.Combat.lastHitAt;
                float since = (System.currentTimeMillis() - hitPulseAt) / 180f;
                if (since < 1) s += Math.round(3 * (1 - since));
                float cd = mc.player.getAttackCooldownProgress(0);
                if (cd < 1) col = Render2D.alpha(col, 0.45f + 0.55f * cd);
                if (mc.player.isUsingItem()) s = Math.max(1, s - 1); // drawing a bow / eating / blocking: tighter
            }
            switch (style.get()) {
                case "Dot" -> box(c, cx - t, cy - t, t * 2 + (t == 1 ? 1 : 0), t * 2 + (t == 1 ? 1 : 0), col);
                case "Circle" -> {
                    int r = s + 1;
                    for (int i = 0; i < 64; i++) {
                        double a = i / 64.0 * Math.PI * 2;
                        box(c, (int) Math.round(cx + Math.cos(a) * r), (int) Math.round(cy + Math.sin(a) * r), t, t, col);
                    }
                    if (centerDot.on()) box(c, cx, cy, 1, 1, col);
                }
                case "Custom" -> {
                    int k = scale.i(), half = GRID * k / 2;
                    for (int y = 0; y < GRID; y++) for (int x = 0; x < GRID; x++)
                        if (pixels.get(x, y)) box(c, cx - half + x * k, cy - half + y * k, k, k, col);
                }
                default -> {
                    int g = style.is("Plus gap") ? gap.i() : 0, o = t / 2;
                    box(c, cx - s - g, cy - o, s, t, col);
                    box(c, cx + g + 1, cy - o, s, t, col);
                    box(c, cx - o, cy - s - g, t, s, col);
                    box(c, cx - o, cy + g + 1, t, s, col);
                    if (style.is("Cross") && g == 0) box(c, cx - o, cy - o, t, t, col);
                    if (centerDot.on()) box(c, cx, cy, 1, 1, col);
                }
            }
        }

        private void box(DrawContext c, int x, int y, int w, int h, int col) {
            if (outline.on()) c.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xA0000000);
            c.fill(x, y, x + w, y + h, col);
        }
    }

    // ================================================================= Own nametag
    public static final class OwnNametag extends Module {
        public OwnNametag() { super("nametag", "Own Nametag", "See your own name above you in third person", Category.RENDER, false); }
        public boolean show() { return isEnabled() && mc.options != null && !mc.options.getPerspective().isFirstPerson(); }
    }

    // ================================================================= Time changer
    public static final class TimeChanger extends Module {
        final Setting.Mode preset = add(new Setting.Mode("preset", "Time", "Custom", "Sunrise", "Noon", "Sunset", "Midnight", "Custom"));
        final Setting.Slider time = (Setting.Slider) add(new Setting.Slider("time", "Custom time", 6000, 0, 23999, 250, "")).visibleWhen(() -> preset.is("Custom"));
        final Setting.Bool cycle = add(new Setting.Bool("cycle", "Let it keep moving", false));
        private long started = System.currentTimeMillis();
        public TimeChanger() { super("time", "Time Changer", "Change the time of day — only for you", Category.RENDER, false); }
        @Override protected void onEnable() { started = System.currentTimeMillis(); }
        public long time() {
            long base = switch (preset.get()) { case "Sunrise" -> 23200; case "Noon" -> 6000; case "Sunset" -> 12600; case "Midnight" -> 18000; default -> time.i(); };
            if (cycle.on()) base += (System.currentTimeMillis() - started) / 50;
            return base;
        }
    }

    // ================================================================= No fog
    public static final class NoFog extends Module {
        public final Setting.Bool liquids = add(new Setting.Bool("liquids", "Also under water & lava", false));
        public NoFog() { super("nofog", "No Fog", "Removes distance fog — overworld, Nether & End", Category.RENDER, false); }
    }

    // ================================================================= Cosmetics
    /** Hats, wings and auras on your own player (pick them in the title screen's Locker). */
    public static final class Cosmetics extends Module {
        public static final String[] HATS = {"None", "Halo", "Crown", "Top Hat", "Witch Hat", "Devil Horns", "Cat Ears"};
        public static final String[] WINGS = {"None", "Angel", "Demon", "Butterfly", "Dragon"};
        public static final String[] AURAS = {"None", "Stardust", "Flames", "Hearts", "Enchanted", "Snow", "Souls"};
        public final Setting.Mode hat = add(new Setting.Mode("hat", "Hat", "None", HATS));
        public final Setting.Mode wings = add(new Setting.Mode("wings", "Wings", "None", WINGS));
        public final Setting.Mode aura = add(new Setting.Mode("aura", "Aura", "None", AURAS));
        public final Setting.Bool others = add(new Setting.Bool("others", "Show other Orbit players' cosmetics", true));
        // all three are drawn by CosmeticsFeature as 3D meshes (auras too), so they show in previews and F5
        public Cosmetics() { super("cosmetics", "Cosmetics", "Hats, wings and auras — change them in the Locker", Category.PLAYER, true); }
    }

    // ================================================================= Item scale
    public static final class ItemScale extends Module {
        // dropped items
        public final Setting.Bool dropped = add(new Setting.Bool("dropped", "Scale dropped items", true));
        public final Setting.Slider droppedSize = (Setting.Slider) add(new Setting.Slider("scale", "Dropped item size", 1.5, 0.25, 4.0, 0.05, "x")).visibleWhen(() -> this.dropped.on());
        public final Setting.Bool spin = add(new Setting.Bool("spin", "Dropped items spin", true));
        public final Setting.Bool bob = add(new Setting.Bool("bob", "Dropped items float up and down", true));
        // in your hand
        public final Setting.Bool held = add(new Setting.Bool("held", "Scale item in hand (first person)", false));
        public final Setting.Slider heldSize = (Setting.Slider) add(new Setting.Slider("heldsize", "Hand item size", 0.8, 0.2, 2.5, 0.05, "x")).visibleWhen(() -> this.held.on());
        // third person
        public final Setting.Bool thirdMine = add(new Setting.Bool("thirdmine", "Scale my held item (third person)", false));
        public final Setting.Bool thirdOthers = add(new Setting.Bool("thirdothers", "Scale others' held items (players & mobs)", false));
        public final Setting.Slider thirdSize = (Setting.Slider) add(new Setting.Slider("thirdsize", "Third person item size", 1.3, 0.25, 3.0, 0.05, "x")).visibleWhen(() -> this.thirdMine.on() || this.thirdOthers.on());
        // which items
        public final Setting.Mode filter = add(new Setting.Mode("filter", "Which items", "All items", "All items", "Only chosen items", "All except chosen"));
        public final Setting.Action choose = add(new Setting.Action("choose", "Chosen items (with own sizes)", "Choose…", () -> mc.setScreen(new dev.orbit.client.gui.ItemScaleScreen(mc.currentScreen))));
        public ItemScale() { super("itemscale", "Item Scale", "Resize dropped and held items — all of them or only the ones you pick", Category.RENDER, false); }
    }

    // ================================================================= Troll Mod
    public static final class TrollModule extends Module {
        public final Setting.Action open = add(new Setting.Action("open", "Players & disguises", "Open Troll menu", () -> mc.setScreen(new dev.orbit.client.gui.TrollScreen(mc.currentScreen))));
        public final Setting.Bool mobSounds = add(new Setting.Bool("mobsounds", "Disguised players sound like their mob when hit", true));
        public final Setting.Bool self = add(new Setting.Bool("self", "\"Everyone else\" includes me too (pranks on your own name always apply — F5)", false));
        public final Setting.Action reset = add(new Setting.Action("reset", "Undo every prank", "Reset all", dev.orbit.client.troll.Troll::clearAll));
        public TrollModule() { super("troll", "Troll Mod", "Show players as any mob, or make them invisible with just their name tag — only you see it", Category.PLAYER, false); }
    }

    // ================================================================= Hitboxes
    /** Entity hitboxes (like F3 + B) with your own colours, and filters for players / mobs / other things. */
    public static final class Hitboxes extends Module {
        public final Setting.Color color = add(new Setting.Color("color", "Box colour", 0xFFFFFFFF));
        public final Setting.Bool target = add(new Setting.Bool("target", "Highlight what you're aiming at", true));
        public final Setting.Color targetColor = (Setting.Color) add(new Setting.Color("targetColor", "Aimed-at colour", 0xFFFF4D5E)).visibleWhen(() -> this.target.on());
        public final Setting.Bool players = add(new Setting.Bool("players", "Players", true));
        public final Setting.Bool mobs = add(new Setting.Bool("mobs", "Mobs & animals", true));
        public final Setting.Bool other = add(new Setting.Bool("other", "Items, arrows & other", false));
        public final Setting.Bool eyes = add(new Setting.Bool("eyes", "Eye height line", true));
        public final Setting.Bool look = add(new Setting.Bool("look", "Look direction line", true));
        public Hitboxes() { super("hitboxes", "Hitboxes", "See exactly where you can hit players and mobs", Category.COMBAT, false); }
        public boolean shows(net.minecraft.entity.Entity e) {
            if (e instanceof net.minecraft.entity.player.PlayerEntity) return players.on();
            if (e instanceof net.minecraft.entity.LivingEntity) return mobs.on();
            return other.on();
        }
        // the boxes are drawn by Orbit itself for every entity (see cosmetics.Props.renderHitbox)
    }

    // ================================================================= Reach display
    /** How far away you hit something (eye to the nearest point of its hitbox). */
    public static final class ReachDisplay extends HudModule {
        final Setting.Slider decimals = add(new Setting.Slider("decimals", "Decimals", 2, 0, 3, 1, ""));
        final Setting.Slider fade = add(new Setting.Slider("fade", "Hide after", 3, 1, 15, 1, "s"));
        private static double last = -1;
        private static long at;
        public ReachDisplay() { super("reach", "Reach Display", "Shows the distance of your last hit", false, 2, -60); category = Category.COMBAT; }
        public static void onAttack(net.minecraft.entity.player.PlayerEntity p, net.minecraft.entity.Entity target) {
            var eye = p.getEyePos();
            var b = target.getBoundingBox();
            double cx = Math.max(b.minX, Math.min(eye.x, b.maxX)), cy = Math.max(b.minY, Math.min(eye.y, b.maxY)), cz = Math.max(b.minZ, Math.min(eye.z, b.maxZ));
            last = eye.distanceTo(new net.minecraft.util.math.Vec3d(cx, cy, cz));
            at = System.currentTimeMillis();
        }
        @Override public boolean hasContent() { return last >= 0 && System.currentTimeMillis() - at < fade.get() * 1000; }
        @Override protected List<String> lines(boolean p) {
            if (!hasContent() && !p) return List.of();
            double v = hasContent() ? last : 3.0;
            return List.of(String.format(java.util.Locale.ROOT, "%." + decimals.i() + "f blocks", v));
        }
    }

    // ================================================================= Item physics
    /** Dropped items fall over and lie flat on the ground instead of floating and spinning. */
    public static final class ItemPhysics extends Module {
        public final Setting.Bool tumble = add(new Setting.Bool("tumble", "Tumble while falling", true));
        public ItemPhysics() { super("itemphysics", "Item Physics", "Dropped items lie flat on the ground and tumble when they fall", Category.RENDER, false); }
    }

    // ================================================================= Totem counter
    public static final class TotemCounter extends HudModule {
        final Setting.Bool hideZero = add(new Setting.Bool("hideZero", "Hide when you have none", false));
        public TotemCounter() { super("totems", "Totem Counter", "How many Totems of Undying you carry", false, -2, -110); category = Category.COMBAT; }
        private int count() {
            if (mc.player == null) return 0;
            var inv = mc.player.getInventory();
            int n = 0;
            for (int i = 0; i < inv.size(); i++) { var st = inv.getStack(i); if (st.isOf(net.minecraft.item.Items.TOTEM_OF_UNDYING)) n += st.getCount(); }
            return n;
        }
        @Override public boolean hasContent() { return mc.player != null && (!hideZero.on() || count() > 0); }
        @Override public int contentWidth(boolean p) { return 18 + Render2D.width("x" + Math.max(1, count())); }
        @Override public int contentHeight(boolean p) { return 16; }
        @Override protected void renderContent(DrawContext c, boolean preview) {
            int n = preview && count() == 0 ? 3 : count();
            Render2D.item(c, net.minecraft.item.Items.TOTEM_OF_UNDYING, 0, 0, 16);
            int col = n == 0 ? 0xFFFF5C5C : n == 1 ? 0xFFFFC14D : 0xFFFFE27A;
            Render2D.text(c, "x" + n, 18, 4, col, shadow.on());
        }
    }

    // ================================================================= Weather changer
    /** Changes the weather on your screen only (the server's weather is unchanged). */
    public static final class WeatherChanger extends Module {
        public final Setting.Mode weather = add(new Setting.Mode("weather", "Weather", "Clear", "Clear", "Rain", "Thunder", "Snow"));
        public WeatherChanger() { super("weather", "Weather Changer", "Pick the weather you see: clear, rain, thunder or snow", Category.RENDER, false); }
        public float rain() { return weather.is("Clear") ? 0 : 1; }
        public float thunder() { return weather.is("Thunder") ? 1 : 0; }
        public boolean snow() { return weather.is("Snow"); }
    }

    // ================================================================= Death animations
    public static final class DeathAnimationsModule extends Module {
        public final Setting.Mode style = add(new Setting.Mode("style", "Animation", "Soul Ascend", dev.orbit.client.death.DeathAnimations.NAMES));
        public final Setting.Bool players = add(new Setting.Bool("players", "Other players", true));
        public final Setting.Bool self = add(new Setting.Bool("self", "Myself", true));
        public final Setting.Bool mobs = add(new Setting.Bool("mobs", "Mobs", true));
        public DeathAnimationsModule() { super("deathanim", "Death Animations", "Cool custom deaths: souls rising, black holes, lightning, fireworks, shattering ice — only you see them", Category.RENDER, false); }
    }

    // ================================================================= Hide armor
    public static final class HideArmor extends Module {
        public final Setting.Bool self = add(new Setting.Bool("self", "Hide my armor", true));
        public final Setting.Bool others = add(new Setting.Bool("others", "Hide other players' armor", true));
        public HideArmor() { super("hidearmor", "Hide Armor", "Don't draw armor on you or other players (only for you)", Category.RENDER, false); }
    }

    // ================================================================= Auto reconnect
    /** When you get disconnected, a "Reconnect" button counts down and joins the server again. */
    public static final class AutoReconnect extends Module {
        final Setting.Slider delay = add(new Setting.Slider("delay", "Reconnect after", 5, 1, 30, 1, "s"));
        final Setting.Bool auto = add(new Setting.Bool("auto", "Reconnect automatically", true));
        private static net.minecraft.client.network.ServerInfo last;
        public AutoReconnect() { super("autoreconnect", "Auto Reconnect", "Rejoin the server after a disconnect", Category.PLAYER, true); }

        /** called once from OrbitClient */
        public void install() {
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
                var s = client.getCurrentServerEntry();
                if (s != null) last = new net.minecraft.client.network.ServerInfo(s.name, s.address, net.minecraft.client.network.ServerInfo.ServerType.OTHER);
            });
            net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
                if (!(screen instanceof net.minecraft.client.gui.screen.DisconnectedScreen) || !isEnabled() || last == null) return;
                final long[] deadline = {auto.on() ? System.currentTimeMillis() + delay.i() * 1000L : Long.MAX_VALUE};
                var target = last;
                var button = net.minecraft.client.gui.widget.ButtonWidget.builder(net.minecraft.text.Text.literal("Reconnect"), b -> join(client, target))
                        .dimensions(w / 2 - 100, h - 52, 200, 20).build();
                net.fabricmc.fabric.api.client.screen.v1.Screens.getButtons(screen).add(button);
                var cancel = net.minecraft.client.gui.widget.ButtonWidget.builder(net.minecraft.text.Text.literal("Cancel auto reconnect"), b -> { deadline[0] = Long.MAX_VALUE; b.visible = false; })
                        .dimensions(w / 2 - 100, h - 28, 200, 20).build();
                cancel.visible = auto.on();
                net.fabricmc.fabric.api.client.screen.v1.Screens.getButtons(screen).add(cancel);
                net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.afterTick(screen).register(s -> {
                    long left = deadline[0] - System.currentTimeMillis();
                    if (deadline[0] == Long.MAX_VALUE) { button.setMessage(net.minecraft.text.Text.literal("Reconnect to " + target.name)); return; }
                    button.setMessage(net.minecraft.text.Text.literal("Reconnecting in " + Math.max(0, (left + 999) / 1000) + "s"));
                    if (left <= 0) { deadline[0] = Long.MAX_VALUE; join(client, target); }
                });
            });
        }
        private static void join(net.minecraft.client.MinecraftClient client, net.minecraft.client.network.ServerInfo s) {
            dev.orbit.client.compat.Compat.connect(new dev.orbit.client.gui.OrbitServersScreen(new dev.orbit.client.gui.OrbitTitleScreen()), s);
        }
    }

    // ================================================================= In-game clock
    public static final class GameTime extends HudModule {
        final Setting.Mode format = add(new Setting.Mode("format", "Format", "24h", "24h", "12h"));
        final Setting.Bool day = add(new Setting.Bool("day", "Show day number", true));
        final Setting.Bool weatherShown = add(new Setting.Bool("weather", "Show the weather", true));
        public GameTime() { super("gametime", "World Time", "The Minecraft time of day (sun & moon)", false, 2, 90); category = Category.HUD; }
        @Override protected List<String> lines(boolean preview) {
            long tod = mc.world != null ? mc.world.getTimeOfDay() : 6000;
            long dayTime = Math.floorMod(tod, 24000L);
            int hours = (int) ((dayTime / 1000 + 6) % 24), minutes = (int) (dayTime % 1000 * 60 / 1000);
            String icon = dayTime < 12300 || dayTime > 23850 ? "☀" : "☾";
            String time = format.is("12h") ? ((hours + 11) % 12 + 1) + ":" + String.format("%02d", minutes) + (hours < 12 ? " AM" : " PM") : String.format("%02d:%02d", hours, minutes);
            String wx = "";
            if (weatherShown.on() && mc.world != null) wx = mc.world.isThundering() ? "  ·  ⛈ Thunder" : mc.world.isRaining() ? "  ·  ☂ Rain" : "  ·  Clear";
            return List.of(icon + " " + time + (day.on() ? "  ·  Day " + (Math.floorDiv(tod, 24000L) + 1) : "") + wx);
        }
    }

    // ================================================================= Waypoints
    public static final class WaypointsModule extends Module {
        final Setting.Key key = add(new Setting.Key("key", "Add waypoint here", () -> OrbitClient.waypointKey));
        final Setting.Bool distance = add(new Setting.Bool("distance", "Show distance", true));
        final Setting.Bool beam = add(new Setting.Bool("beam", "Light beam into the sky", true));
        final Setting.Bool arrows = add(new Setting.Bool("arrows", "Arrows on the screen edge for waypoints out of view", true));
        final Setting.Bool coords = add(new Setting.Bool("coords", "Show coordinates when you look at one", true));
        final Setting.Bool death = add(new Setting.Bool("death", "Save where I die (\"Last death\")", true));
        final Setting.Slider maxDistance = add(new Setting.Slider("max", "Hide farther than", 5000, 50, 20000, 50, "m"));
        final Setting.Action manage = add(new Setting.Action("manage", "Your waypoints", "Manage", () -> mc.setScreen(new dev.orbit.client.gui.WaypointsScreen(mc.currentScreen))));
        private boolean wasDead;
        public WaypointsModule() { super("waypoints", "Waypoints", "Mark places and see them through walls — with beams, edge arrows and a death point", Category.PLAYER, true); }

        @Override public void tick() {
            while (OrbitClient.waypointKey.wasPressed()) {
                var w = dev.orbit.client.waypoints.Waypoints.addHere();
                if (w != null && mc.player != null) { dev.orbit.client.gui.Sounds.success(); mc.player.sendMessage(net.minecraft.text.Text.literal("§d[Orbit] §fAdded §e" + w.name + " §7(" + w.x + ", " + w.y + ", " + w.z + ") — rename it in Right Shift → Player → Waypoints"), false); }
            }
            // remember where you died (one "Last death" per world, replaced each time)
            boolean dead = mc.player != null && (mc.player.isDead() || mc.player.getHealth() <= 0);
            if (dead && !wasDead && death.on()) {
                var list = dev.orbit.client.waypoints.Waypoints.current();
                list.removeIf(w -> "Last death".equals(w.name));
                var p = mc.player;
                list.add(new dev.orbit.client.waypoints.Waypoints.Waypoint("Last death", p.getBlockX(), p.getBlockY(), p.getBlockZ(), dev.orbit.client.waypoints.Waypoints.dimension(), 0xFFFF5C5C));
                dev.orbit.client.waypoints.Waypoints.save();
            }
            wasDead = dead;
        }

        /** beams, pins, name pills and edge arrows for this dimension's waypoints (HUD pass) */
        public void renderMarkers(DrawContext c, int sw, int sh) {
            if (!isEnabled() || mc.world == null || mc.options.hudHidden) return;
            dev.orbit.client.gui.WaypointRenderer.render(c, sw, sh, new dev.orbit.client.gui.WaypointRenderer.Options(distance.on(), maxDistance.get(), beam.on(), arrows.on(), coords.on()));
        }
    }
    // ================================================================= Orbit badge
    public static final class Badge extends Module {
        public Badge() { super("badge", "Orbit Badge", "Planet icon before Orbit players' names (name tags & Tab)", Category.PLAYER, true); }
    }

    // ================================================================= Skins by username
    public static final class NameSkinsModule extends Module {
        public NameSkinsModule() { super("nameskins", "Skins by Name", "Players without a skin (cracked servers, offline accounts) get the skin of their username", Category.PLAYER, true); }
    }

    // ================================================================= Chunk borders
    /** Shows Minecraft's chunk border lines (like F3+G) without opening the debug screen. */
    public static final class ChunkBorders extends Module {
        public ChunkBorders() { super("chunkborders", "Chunk Borders", "Show chunk boundaries (like F3 + G)", Category.RENDER, false); }
        private void apply(boolean on) {
            if (mc == null || mc.debugHudEntryList == null) return;
            mc.debugHudEntryList.setEntryVisibility(net.minecraft.client.gui.hud.debug.DebugHudEntries.CHUNK_BORDERS,
                    on ? net.minecraft.client.gui.hud.debug.DebugHudEntryVisibility.ALWAYS_ON : net.minecraft.client.gui.hud.debug.DebugHudEntryVisibility.NEVER);
            if (mc.worldRenderer != null && mc.worldRenderer.debugRenderer != null) mc.worldRenderer.debugRenderer.initRenderers(); // renderers are only rebuilt on demand
        }
        private boolean applied;
        @Override public void tick() { if (!applied && mc.world != null) { apply(true); applied = true; } }
        @Override protected void onEnable() { applied = false; }
        @Override protected void onDisable() { apply(false); applied = false; }
    }

    // ================================================================= TNT timer
    /** Seconds until primed TNT explodes, shown above it (drawn by EntityRendererMixin as a name tag). */
    public static final class TntTimer extends Module {
        final Setting.Bool decimals = add(new Setting.Bool("decimals", "Show tenths of a second", true));
        public TntTimer() { super("tnttimer", "TNT Timer", "Countdown above lit TNT", Category.RENDER, true); }
        public net.minecraft.text.Text label(net.minecraft.entity.TntEntity tnt) {
            float s = Math.max(0, tnt.getFuse()) / 20f;
            String t = decimals.on() ? String.format(java.util.Locale.ROOT, "%.1fs", s) : (int) Math.ceil(s) + "s";
            int col = s > 2.5f ? 0x55FF55 : s > 1f ? 0xFFDD33 : 0xFF5544;
            return net.minecraft.text.Text.literal(t).withColor(col);
        }
    }

    // ================================================================= Shield status
    /** Whether your shield works right now (axes disable it for a few seconds) plus its durability. */
    public static final class ShieldStatus extends HudModule {
        final Setting.Bool tintShield = add(new Setting.Bool("tint", "Colour the shield itself (green = works, red = disabled)", true));
        final Setting.Bool interpolate = (Setting.Bool) add(new Setting.Bool("interpolate", "Fade from red back to green during the cooldown", true)).visibleWhen(() -> this.tintShield.on());
        final Setting.Color readyColor = (Setting.Color) add(new Setting.Color("readyColor", "Working colour", 0xFF5CFF6E)).visibleWhen(() -> this.tintShield.on());
        final Setting.Color disabledColor = (Setting.Color) add(new Setting.Color("disabledColor", "Disabled colour", 0xFFFF4A4A)).visibleWhen(() -> this.tintShield.on());
        final Setting.Bool text = add(new Setting.Bool("text", "Also show a text box with the timer", false));
        final Setting.Bool durability = (Setting.Bool) add(new Setting.Bool("durability", "Show durability", true)).visibleWhen(() -> this.text.on());
        private long cdStart;
        /** entity id of the player whose held item renders right now (-1 = you / inventory) */
        public static int holder = -1;
        /** other players whose shield an axe just disabled: entity id -> {disabled at, until} (ms) */
        public static final java.util.Map<Integer, long[]> DISABLED = new java.util.concurrent.ConcurrentHashMap<>();
        final Setting.Bool others = (Setting.Bool) add(new Setting.Bool("others", "Also colour other players' shields", true)).visibleWhen(() -> this.tintShield.on());
        public static void onShieldBreakNear(double x, double y, double z) {
            var mc = net.minecraft.client.MinecraftClient.getInstance();
            if (mc.world == null) return;
            net.minecraft.entity.player.PlayerEntity best = null; double bd = 4;
            for (var p : mc.world.getPlayers()) { double d = p.squaredDistanceTo(x, y, z); if (d < bd) { bd = d; best = p; } }
            if (best != null && best != mc.player) { long now = System.currentTimeMillis(); DISABLED.put(best.getId(), new long[]{now, now + 5000}); }
        }
        public ShieldStatus() { super("shield", "Shield Status", "Your shield turns green when it works and red when an axe disabled it", true, -2, -80); category = Category.COMBAT; }

        /** tint for a shield being drawn right now (0 = leave it) */
        public static int tint() {
            var mc = net.minecraft.client.MinecraftClient.getInstance();
            if (mc.player == null || OrbitClient.modules() == null) return 0;
            if (!(OrbitClient.modules().get("shield") instanceof ShieldStatus m) || !m.isEnabled() || !m.tintShield.on()) return 0;
            if (holder >= 0 && holder != mc.player.getId()) { // someone else's shield
                if (!m.others.on()) return 0;
                long[] d = DISABLED.get(holder);
                long now = System.currentTimeMillis();
                if (d == null || now >= d[1]) return m.readyColor.argb() | 0xFF000000;
                float left = (d[1] - now) / (float) (d[1] - d[0]);
                return (m.interpolate.on() ? Render2D.lerpColor(m.disabledColor.argb(), m.readyColor.argb(), 1 - left) : m.disabledColor.argb()) | 0xFF000000;
            }
            var cd = mc.player.getItemCooldownManager();
            var st = new net.minecraft.item.ItemStack(net.minecraft.item.Items.SHIELD);
            if (!cd.isCoolingDown(st)) return m.readyColor.argb() | 0xFF000000;
            float left = cd.getCooldownProgress(st, mc.getRenderTickCounter().getTickProgress(false)); // 1 -> 0
            return (m.interpolate.on() ? Render2D.lerpColor(m.disabledColor.argb(), m.readyColor.argb(), 1 - left) : m.disabledColor.argb()) | 0xFF000000;
        }
        @Override public boolean hasContent() { return text.on() && !shield().isEmpty(); }

        private net.minecraft.item.ItemStack shield() {
            if (mc.player == null) return net.minecraft.item.ItemStack.EMPTY;
            for (var hand : new net.minecraft.util.Hand[]{net.minecraft.util.Hand.OFF_HAND, net.minecraft.util.Hand.MAIN_HAND}) {
                var st = mc.player.getStackInHand(hand);
                if (st.isOf(net.minecraft.item.Items.SHIELD)) return st;
            }
            return net.minecraft.item.ItemStack.EMPTY;
        }
        @Override protected List<String> lines(boolean p) {
            var st = shield();
            if (st.isEmpty()) return p ? List.of("§aShield: Ready", "312 / 336") : List.of();
            var cd = mc.player.getItemCooldownManager();
            String status;
            if (cd.isCoolingDown(st)) {
                float left = cd.getCooldownProgress(st, 0); // 1 → 0
                long now = System.currentTimeMillis();
                if (cdStart == 0 || left > 0.97f) cdStart = now;
                long elapsed = now - cdStart;
                float secs = left >= 0.97f || elapsed < 100 ? 5f * left : elapsed / 1000f / Math.max(0.01f, 1 - left) * left;
                status = String.format(java.util.Locale.ROOT, "§cShield: Disabled %.1fs", secs);
            } else { cdStart = 0; status = "§aShield: Ready"; }
            if (!durability.on() || !st.isDamageable()) return List.of(status);
            return List.of(status, (st.getMaxDamage() - st.getDamage()) + " / " + st.getMaxDamage());
        }
    }

    // ================================================================= Hurt cam
    public static final class HurtCam extends Module {
        public HurtCam() { super("hurtcam", "No Hurt Cam", "No screen shake when you take damage", Category.COMBAT, false); }
    }

    // ================================================================= Scoreboard
    public static final class Scoreboard extends Module {
        public final Setting.Bool hide = add(new Setting.Bool("hide", "Hide scoreboard", false));
        public final Setting.Slider scale = (Setting.Slider) add(new Setting.Slider("scale", "Size", 1, 0.5, 2, 0.05, "x")).visibleWhen(() -> !hide.on());
        public final Setting.Slider bgOpacity = (Setting.Slider) add(new Setting.Slider("bg", "Background opacity", 30, 0, 100, 1, "%")).visibleWhen(() -> !hide.on());
        public final Setting.Slider offsetY = (Setting.Slider) add(new Setting.Slider("y", "Move up / down", 0, -150, 150, 1, "")).visibleWhen(() -> !hide.on());
        public final Setting.Slider offsetX = (Setting.Slider) add(new Setting.Slider("x", "Move left", 0, 0, 300, 1, "")).visibleWhen(() -> !hide.on());
        public Scoreboard() { super("scoreboard", "Scoreboard", "Resize, move, recolour or hide the sidebar", Category.HUD, false); }
    }

    // ================================================================= Saturation
    public static final class Saturation extends HudModule {
        final Setting.Bool overlay = add(new Setting.Bool("overlay", "Saturation outline on hunger bar", true));
        final Setting.Bool preview = add(new Setting.Bool("preview", "Preview food in your hand", true));
        final Setting.Bool exhaustionBar = add(new Setting.Bool("exhaustionBar", "Exhaustion bar behind hunger", true));
        final Setting.Bool numbers = add(new Setting.Bool("numbers", "Also show numbers", false));
        final Setting.Bool exhaustion = (Setting.Bool) add(new Setting.Bool("exhaustion", "Numbers: exhaustion too", false)).visibleWhen(() -> numbers.on());
        public Saturation() { super("saturation", "Saturation", "AppleSkin-style saturation, food preview & exhaustion", true, -2, -60); category = Category.COMBAT; }
        @Override public boolean hasContent() { return numbers.on(); }
        @Override protected List<String> lines(boolean p) {
            float sat = mc.player != null ? mc.player.getHungerManager().getSaturationLevel() : 4.6f;
            String s = String.format(java.util.Locale.ROOT, "Saturation: %.1f", sat);
            if (exhaustion.on()) {
                float ex = mc.player != null ? ((dev.orbit.client.mixin.HungerManagerAccessor) mc.player.getHungerManager()).orbit$exhaustion() : 1.2f;
                return List.of(s, String.format(java.util.Locale.ROOT, "Exhaustion: %.1f", ex));
            }
            return List.of(s);
        }
        /**
         * Draws over the vanilla hunger bar the way AppleSkin does: a gold outline on each shank for
         * the saturation you have, flashing "ghost" shanks + outline for the food you are holding,
         * and a faint exhaustion bar. The shank shape is read from the current food icon texture,
         * so it lines up with vanilla and with resource packs.
         */
        private boolean visible() {
            return isEnabled() && mc.player != null && mc.interactionManager != null && mc.interactionManager.hasStatusBars() && !mc.options.hudHidden
                    && !(mc.player.getVehicle() instanceof net.minecraft.entity.LivingEntity); // mount health replaces the hunger bar
        }

        /** Exhaustion bar, drawn under the hunger icons. Returns true if anything was drawn. */
        public boolean renderUnderlay(DrawContext c, int sw, int sh) {
            if (!visible() || !exhaustionBar.on()) return false;
            float ex = Math.min(4f, ((dev.orbit.client.mixin.HungerManagerAccessor) mc.player.getHungerManager()).orbit$exhaustion());
            int w = Math.round(81 * ex / 4f), right = sw / 2 + 91, y = sh - 39;
            if (w <= 0) return false;
            c.fill(right - w, y, right, y + 9, 0x55FFFFFF);
            return true;
        }

        public void renderOverlay(DrawContext c, int sw, int sh) {
            if (!visible()) return;
            boolean[] mask = dev.orbit.client.gui.FoodIcon.mask();
            var hm = mc.player.getHungerManager();
            int food = hm.getFoodLevel();
            float sat = hm.getSaturationLevel();
            int right = sw / 2 + 91, y = sh - 39;

            float[] held = preview.on() ? heldFood() : null;            float flash = (float) (0.5 + 0.35 * Math.sin(System.currentTimeMillis() / 160.0));
            if (held != null) {
                int newFood = Math.min(20, food + (int) held[0]);
                for (int i = food / 2; i < (newFood + 1) / 2; i++) {
                    int x = right - i * 8 - 9;
                    boolean half = newFood < (i + 1) * 2; // the food only fills half of this shank
                    ghost(c, mask, x, y, half ? flash * 0.6f : flash);
                }
            }
            if (overlay.on()) {
                drawSaturation(c, mask, right, y, 0f, sat, 0xFFFFC93C, 1f);
                if (held != null) {
                    float newSat = Math.min(Math.min(20, food + held[0]), sat + held[1]);
                    drawSaturation(c, mask, right, y, sat, newSat, 0xFFFFC93C, flash);
                }
            }
        }

        /** Gold outline on the shank pixels' edge for saturation between from and to (2 points per shank). */
        private static void drawSaturation(DrawContext c, boolean[] mask, int right, int y, float from, float to, int color, float alpha) {
            int col = Render2D.alpha(color, alpha);
            for (int i = 0; i < 10; i++) {
                float lo = Math.max(from, i * 2f), hi = Math.min(to, i * 2f + 2f);
                if (hi <= lo) continue;
                int x = right - i * 8 - 9;
                // which part of the 9px icon this saturation covers, filled from the right like AppleSkin
                int x0 = Math.round(9 * (1 - (hi - i * 2f) / 2f)), x1 = Math.round(9 * (1 - (lo - i * 2f) / 2f));
                for (int py = 0; py < 9; py++) for (int px = x0; px < x1; px++)
                    if (edge(mask, px, py)) c.fill(x + px, y + py, x + px + 1, y + py + 1, col);
            }
        }

        private static void ghost(DrawContext c, boolean[] mask, int x, int y, float a) {
            for (int py = 0; py < 9; py++) for (int px = 0; px < 9; px++) {
                if (!mask[py * 9 + px]) continue;
                int col = edge(mask, px, py) ? 0xFF4A2A12 : py < 5 ? 0xFFD98A3A : 0xFFE9DFC9;
                c.fill(x + px, y + py, x + px + 1, y + py + 1, Render2D.alpha(col, a));
            }
        }

        private static boolean edge(boolean[] m, int x, int y) {
            if (!m[y * 9 + x]) return false;
            return x == 0 || y == 0 || x == 8 || y == 8 || !m[y * 9 + x - 1] || !m[y * 9 + x + 1] || !m[(y - 1) * 9 + x] || !m[(y + 1) * 9 + x];
        }

        /** {hunger, saturation} the item in your hand would give, or null. */
        private float[] heldFood() {
            for (var hand : net.minecraft.util.Hand.values()) {
                float[] f = dev.orbit.client.compat.Compat.food(mc.player.getStackInHand(hand));
                if (f != null) return f;
            }
            return null;
        }
    }
}
