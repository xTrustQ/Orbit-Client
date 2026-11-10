package dev.orbit.client.module;

import dev.orbit.client.compat.Compat;
import dev.orbit.client.gui.Render2D;
import dev.orbit.client.module.setting.Setting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** All built-in HUD elements. */
public final class HudModules {
    private HudModules() {}

    // ================================================================= FPS
    public static final class Fps extends HudModule {
        final Setting.Mode style = add(new Setting.Mode("style", "Style", "FPS: 144", "FPS: 144", "144 FPS", "144"));
        public Fps() { super("fps", "FPS", "Frames per second", true, 2, 2); }
        @Override protected List<String> lines(boolean p) {
            int f = mc.getCurrentFps();
            return List.of(switch (style.get()) { case "144 FPS" -> f + " FPS"; case "144" -> String.valueOf(f); default -> "FPS: " + f; });
        }
    }

    // ================================================================= CPS
    public static final class Cps extends HudModule {
        private static final Deque<Long> LEFT = new ArrayDeque<>(), RIGHT = new ArrayDeque<>();
        final Setting.Bool showRight = add(new Setting.Bool("right", "Show right clicks", true));
        public Cps() { super("cps", "CPS", "Clicks per second", true, 2, 21); }
        public static void click(int button) {
            long now = System.currentTimeMillis();
            if (button == 0) LEFT.addLast(now); else if (button == 1) RIGHT.addLast(now);
        }
        public static int left() { prune(LEFT); return LEFT.size(); }
        public static int right() { prune(RIGHT); return RIGHT.size(); }
        private static void prune(Deque<Long> d) { long cut = System.currentTimeMillis() - 1000; while (!d.isEmpty() && d.peekFirst() < cut) d.pollFirst(); }
        @Override protected List<String> lines(boolean p) {
            return List.of(showRight.on() ? "CPS: " + left() + " | " + right() : "CPS: " + left());
        }
    }

    // ================================================================= Coordinates
    public static final class Coords extends HudModule {
        final Setting.Mode layout = add(new Setting.Mode("layout", "Layout", "Vertical", "Vertical", "Single line"));
        final Setting.Bool biome = add(new Setting.Bool("biome", "Show biome", true));
        final Setting.Bool facing = add(new Setting.Bool("facing", "Show facing", true));
        public Coords() { super("coords", "Coordinates", "XYZ, facing & biome", true, 2, 40); }
        @Override protected List<String> lines(boolean p) {
            if (mc.player == null) return p ? List.of("X: 128", "Y: 64", "Z: -256") : List.of();
            BlockPos b = mc.player.getBlockPos();
            List<String> out = new ArrayList<>();
            String dir = facing.on() ? " " + Direction.cardinal(mc.player.getYaw()) : "";
            if (layout.is("Single line")) out.add(b.getX() + ", " + b.getY() + ", " + b.getZ() + dir);
            else { out.add("X: " + b.getX() + dir); out.add("Y: " + b.getY()); out.add("Z: " + b.getZ()); }
            if (biome.on() && mc.world != null) out.add(mc.world.getBiome(b).getKey().map(k -> pretty(k.getValue().getPath())).orElse("?"));
            return out;
        }
        @Override protected void renderContent(DrawContext c, boolean preview) {
            int y = 0;
            for (String l : lines(preview)) { Render2D.chromaText(c, wrap(l), 0, y, themedText(), chroma.on(), shadow.on() && !theme.is("Minimal")); y += 10; }
        }
        private static String pretty(String s) {
            StringBuilder b = new StringBuilder();
            for (String w : s.split("_")) if (!w.isEmpty()) b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
            return b.toString().trim();
        }
    }

    // ================================================================= Direction
    public static final class Direction extends HudModule {
        final Setting.Bool degrees = add(new Setting.Bool("degrees", "Show degrees", true));
        public Direction() { super("direction", "Direction", "Compass heading", false, CENTER, 2); }
        static String cardinal(float yaw) {
            String[] n = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
            return n[Math.floorMod(Math.round(MathHelper.wrapDegrees(yaw) / 45f), 8)];
        }
        @Override protected List<String> lines(boolean p) {
            float yaw = mc.player != null ? MathHelper.wrapDegrees(mc.player.getYaw()) : 0;
            return List.of(cardinal(yaw) + (degrees.on() ? "  " + Math.round(yaw) + "°" : ""));
        }
    }

    // ================================================================= Keystrokes
    public static final class Keystrokes extends HudModule {
        final Setting.Bool mouse = add(new Setting.Bool("mouse", "Mouse buttons", true));
        final Setting.Bool space = add(new Setting.Bool("space", "Space bar", true));
        final Setting.Bool cps = add(new Setting.Bool("cps", "CPS on mouse", true));
        final Setting.Color pressed = add(new Setting.Color("pressed", "Pressed color", 0xFFFFFFFF));
        private final float[] anim = new float[7];
        private long last = System.nanoTime();
        private static final int K = 22, G = 2;
        public Keystrokes() { super("keystrokes", "Keystrokes", "WASD, mouse & space", true, 2, -2); }
        @Override protected boolean outerBackground() { return false; }
        @Override protected int pad() { return 0; }
        @Override public int contentWidth(boolean p) { return K * 3 + G * 2; }
        @Override public int contentHeight(boolean p) { return K * 2 + G + (mouse.on() ? K + G : 0) + (space.on() ? 12 + G : 0); }
        @Override public boolean hasContent() { return true; }
        @Override protected void renderContent(DrawContext c, boolean preview) {
            long now = System.nanoTime(); float dt = Math.min(0.1f, (now - last) / 1e9f); last = now;
            var o = mc.options;
            KeyBinding[] keys = {o.forwardKey, o.leftKey, o.backKey, o.rightKey, o.attackKey, o.useKey, o.jumpKey};
            for (int i = 0; i < keys.length; i++) anim[i] = Render2D.approach(anim[i], keys[i].isPressed() ? 1 : 0, 18, dt);
            key(c, K + G, 0, K, K, "W", anim[0]);
            int y = K + G;
            key(c, 0, y, K, K, "A", anim[1]); key(c, K + G, y, K, K, "S", anim[2]); key(c, (K + G) * 2, y, K, K, "D", anim[3]);
            y += K + G;
            int half = (K * 3 + G * 2 - G) / 2;
            if (mouse.on()) {
                key(c, 0, y, half, K, cps.on() ? "LMB " + Cps.left() : "LMB", anim[4]);
                key(c, half + G, y, K * 3 + G * 2 - half - G, K, cps.on() ? "RMB " + Cps.right() : "RMB", anim[5]);
                y += K + G;
            }
            if (space.on()) {
                int sw = K * 3 + G * 2;
                key(c, 0, y, sw, 12, "", anim[6]);
                c.fill(sw / 2 - 10, y + 5, sw / 2 + 10, y + 7, Render2D.lerpColor(themedText(), 0xFF101010, anim[6]));
            }
        }
        private void key(DrawContext c, int x, int y, int w, int h, String label, float a) {
            if (themed()) { // Glass / Minimal / Outline / Neon / Pill (chosen in the HUD Designer)
                int tc = themeBox(c, x, y, w, h, a);
                float tw = Render2D.width(label);
                if (chroma.on() && a < 0.5f) Render2D.chromaText(c, label, x + (w - tw) / 2f, y + (h - 8) / 2f, tc, true, false);
                else Render2D.text(c, label, x + (w - tw) / 2f, y + (h - 8) / 2f + 0.5f, tc, false);
                return;
            }
            int base = background.on() ? Render2D.withAlpha(bgColor.rgb(), (int) (bgOpacity.get() * 2.55)) : 0x00000000;
            int fill = Render2D.lerpColor(base, Render2D.withAlpha(pressed.rgb(), 200), a);
            Render2D.round(c, x, y, w, h, rounded.on() ? 3 : 0, fill);
            int tc = Render2D.lerpColor(textColor.argb(), 0xFF101010, a);
            float tw = Render2D.width(label);
            if (chroma.on() && a < 0.5f) Render2D.chromaText(c, label, x + (w - tw) / 2f, y + (h - 8) / 2f, tc, true, shadow.on());
            else Render2D.text(c, label, x + (w - tw) / 2f, y + (h - 8) / 2f + 0.5f, tc, shadow.on() && a < 0.5f);
        }
    }

    // ================================================================= Armor
    public static final class Armor extends HudModule {
        final Setting.Mode layout = add(new Setting.Mode("layout", "Layout", "Vertical", "Vertical", "Horizontal"));
        final Setting.Mode durability = add(new Setting.Mode("durability", "Durability", "Value", "Value", "Percent", "Hidden"));
        final Setting.Bool hand = add(new Setting.Bool("hand", "Show held item", true));
        private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND};
        public Armor() { super("armor", "Armor Status", "Armor & held item durability", true, -2, -2); background.set(false); }
        private List<ItemStack> stacks(boolean preview) {
            List<ItemStack> out = new ArrayList<>();
            if (mc.player == null) {
                if (preview) { out.add(new ItemStack(Items.DIAMOND_HELMET)); out.add(new ItemStack(Items.DIAMOND_CHESTPLATE)); out.add(new ItemStack(Items.DIAMOND_LEGGINGS)); out.add(new ItemStack(Items.DIAMOND_BOOTS)); out.add(new ItemStack(Items.DIAMOND_SWORD)); }
                return out;
            }
            for (EquipmentSlot s : SLOTS) {
                if (s == EquipmentSlot.MAINHAND && !hand.on()) continue;
                ItemStack st = mc.player.getEquippedStack(s);
                if (!st.isEmpty()) out.add(st);
            }
            if (out.isEmpty() && preview) out.add(new ItemStack(Items.DIAMOND_HELMET));
            return out;
        }
        private String dura(ItemStack s) {
            if (durability.is("Hidden") || !s.isDamageable()) return s.getCount() > 1 ? String.valueOf(s.getCount()) : "";
            int left = s.getMaxDamage() - s.getDamage();
            return durability.is("Percent") ? Math.round(left * 100f / s.getMaxDamage()) + "%" : String.valueOf(left);
        }
        @Override public boolean hasContent() { return !stacks(false).isEmpty(); }
        @Override public int contentWidth(boolean p) {
            List<ItemStack> st = stacks(p);
            if (layout.is("Horizontal")) return Math.max(16, st.size() * 18 - 2);
            int w = 0; for (ItemStack s : st) w = Math.max(w, Render2D.width(dura(s)));
            return 16 + (w > 0 ? w + 3 : 0);
        }
        @Override public int contentHeight(boolean p) {
            int n = stacks(p).size();
            return layout.is("Horizontal") ? (durability.is("Hidden") ? 16 : 26) : Math.max(16, n * 17 - 1);
        }
        @Override protected void renderContent(DrawContext c, boolean preview) {
            int i = 0;
            for (ItemStack s : stacks(preview)) {
                String d = dura(s);
                int col = textColor.argb();
                if (s.isDamageable() && !durability.is("Hidden")) col = Render2D.lerpColor(0xFFFF5555, textColor.argb(), 1f - s.getDamage() / (float) s.getMaxDamage());
                if (layout.is("Horizontal")) {
                    int x = i * 18;
                    c.drawItem(s, x, 0);
                    if (!d.isEmpty()) Render2D.chromaText(c, d, x + 8 - Render2D.width(d) / 2f, 18, col, chroma.on(), shadow.on());
                } else {
                    int y = i * 17;
                    c.drawItem(s, 0, y);
                    if (!d.isEmpty()) Render2D.chromaText(c, d, 19, y + 4, col, chroma.on(), shadow.on());
                }
                i++;
            }
        }
    }

    // ================================================================= Potions
    public static final class Potions extends HudModule {
        final Setting.Mode style = add(new Setting.Mode("style", "Style", "Tiles", "Tiles", "Classic", "Compact"));
        final Setting.Bool vertical = (Setting.Bool) add(new Setting.Bool("vertical", "Stack tiles vertically", false)).visibleWhen(() -> style.is("Tiles"));
        final Setting.Bool icons = (Setting.Bool) add(new Setting.Bool("icons", "Show icons", true)).visibleWhen(() -> style.is("Compact"));
        public Potions() { super("potions", "Potion Effects", "Active effects & timers", false, -2, 2); }
        private boolean classic() { return style.is("Classic"); }
        private boolean tiles() { return style.is("Tiles"); }
        @Override protected boolean outerBackground() { return !classic() && !tiles() && super.outerBackground(); }
        @Override protected int pad() { return classic() || tiles() ? 0 : super.pad(); }
        private static final int TILE = 26, TGAP = 3;
        /** "7m", "45s", "∞" */
        private static String shortTime(StatusEffectInstance e) {
            if (e.isInfinite()) return "∞";
            int s = e.getDuration() / 20;
            return s >= 3600 ? (s / 3600) + "h" : s >= 60 ? (s / 60) + "m" : s + "s";
        }
        private static final int BOX_W = 120, BOX_H = 32, GAP = 1;
        private static final net.minecraft.util.Identifier BOX = Compat.vanilla("container/inventory/effect_background");
        private static final net.minecraft.util.Identifier BOX_AMBIENT = Compat.vanilla("container/inventory/effect_background_ambient");
        /** "Speed II" like the inventory screen. */
        private static String classicName(StatusEffectInstance e) {
            String n = Compat.effectName(e);
            if (e.getAmplifier() > 0 && e.getAmplifier() < 10) n += " " + net.minecraft.text.Text.translatable("enchantment.level." + (e.getAmplifier() + 1)).getString();
            return n;
        }
        private static String duration(StatusEffectInstance e) {
            if (e.isInfinite()) return "∞";
            int s = e.getDuration() / 20;
            return s >= 3600 ? (s / 3600) + ":" + String.format("%02d:%02d", s / 60 % 60, s % 60) : (s / 60) + ":" + String.format("%02d", s % 60);
        }
        private List<StatusEffectInstance> effects(boolean preview) {
            List<StatusEffectInstance> l = mc.player == null ? new ArrayList<>() : new ArrayList<>(mc.player.getStatusEffects());
            if (l.isEmpty() && preview) l.add(new StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.SPEED, 20 * 95, 1));
            return l;
        }
        private static String label(StatusEffectInstance e) {
            String n = Compat.effectName(e);
            if (e.getAmplifier() > 0) n += " " + (e.getAmplifier() + 1);
            if (e.isInfinite()) return n + "  ∞";
            int s = e.getDuration() / 20;
            return n + "  " + (s / 60) + ":" + String.format("%02d", s % 60);
        }
        @Override public boolean hasContent() { return mc.player != null && !mc.player.getStatusEffects().isEmpty(); }
        @Override public int contentWidth(boolean p) {
            if (tiles()) { int n = effects(p).size(); return vertical.on() ? TILE : Math.max(0, n * (TILE + TGAP) - TGAP); }
            if (classic()) return BOX_W;
            int w = 0; for (var e : effects(p)) w = Math.max(w, Render2D.width(label(e))); return w + (icons.on() ? 12 : 0);
        }
        @Override public int contentHeight(boolean p) {
            int n = effects(p).size();
            if (tiles()) return vertical.on() ? Math.max(0, n * (TILE + TGAP) - TGAP) : (n == 0 ? 0 : TILE);
            return classic() ? Math.max(0, n * (BOX_H + GAP) - GAP) : Math.max(0, n * 12 - 2);
        }
        @Override protected void renderContent(DrawContext c, boolean preview) {
            if (tiles()) {
                int i = 0;
                for (var e : effects(preview)) {
                    int x = vertical.on() ? 0 : i * (TILE + TGAP), y = vertical.on() ? i * (TILE + TGAP) : 0;
                    boolean ending = e.getDuration() < 200 && !e.isInfinite();
                    int a = (int) (bgOpacity.get() * 2.55 * 1.6);
                    Render2D.round(c, x, y, TILE, TILE, 5, Render2D.withAlpha(0x1A1A22, Math.min(235, Math.max(150, a))));
                    Render2D.round(c, x + 0.5f, y + 0.5f, TILE - 1, TILE - 1, 5, 0x18FFFFFF);
                    if (border.on()) Render2D.round(c, x, y, TILE, 1.2f, 1, Render2D.accent());
                    Compat.effectIcon(c, e, x + 6, y + 2, 14);
                    String t = shortTime(e);
                    int col = ending && (System.currentTimeMillis() / 300) % 2 == 0 ? 0xFFFF6B6B : 0xFFE6E6EE;
                    Render2D.text(c, t, x + TILE / 2f - Render2D.width(t) / 2f, y + TILE - 10, col, true);
                    if (e.getAmplifier() > 0) { String amp = String.valueOf(e.getAmplifier() + 1); Render2D.text(c, amp, x + TILE - Render2D.width(amp) - 2, y + 2, 0xFFFFE27A, true); }
                    i++;
                }
                return;
            }
            if (classic()) {
                int y = 0;
                for (var e : effects(preview)) {
                    Compat.guiSprite(c, e.isAmbient() ? BOX_AMBIENT : BOX, 0, y, BOX_W, BOX_H);
                    Compat.effectIcon(c, e, 7, y + 7, 18);
                    boolean ending = e.getDuration() < 200 && !e.isInfinite();
                    Render2D.text(c, Render2D.fit(classicName(e), BOX_W - 36, false), 32, y + 7, 0xFFFFFFFF, true);
                    int dc = ending && (System.currentTimeMillis() / 400) % 2 == 0 ? 0xFFFF6B6B : 0xFF7F7F7F;
                    Render2D.text(c, duration(e), 32, y + 17, dc, true);
                    y += BOX_H + GAP;
                }
                return;
            }
            int y = 0;
            for (var e : effects(preview)) {
                int x = 0;
                if (icons.on()) {
                    Compat.effectIcon(c, e, 0, y, 9);
                    x = 12;
                }
                int col = e.getDuration() < 200 && !e.isInfinite() && (System.currentTimeMillis() / 300) % 2 == 0 ? 0xFFFF6B6B : textColor.argb();
                Render2D.chromaText(c, label(e), x, y + 1, col, chroma.on(), shadow.on());
                y += 12;
            }
        }
    }

    // ================================================================= Ping
    public static final class Ping extends HudModule {
        public Ping() { super("ping", "Ping", "Latency to the server", false, 2, 92); }
        @Override protected List<String> lines(boolean p) {
            int ms = 0;
            if (mc.getNetworkHandler() != null && mc.player != null) {
                PlayerListEntry e = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
                if (e != null) ms = e.getLatency();
            }
            return List.of("Ping: " + ms + " ms");
        }
    }

    // ================================================================= Clock
    public static final class Clock extends HudModule {
        final Setting.Mode format = add(new Setting.Mode("format", "Format", "24h", "24h", "12h"));
        final Setting.Bool seconds = add(new Setting.Bool("seconds", "Show seconds", false));
        public Clock() { super("clock", "Clock", "Real-world time", false, CENTER, 21); }
        @Override protected List<String> lines(boolean p) {
            String pat = (format.is("24h") ? "HH:mm" : "h:mm") + (seconds.on() ? ":ss" : "") + (format.is("12h") ? " a" : "");
            return List.of(LocalTime.now().format(DateTimeFormatter.ofPattern(pat)));
        }
    }

    // ================================================================= Memory
    public static final class Memory extends HudModule {
        public Memory() { super("memory", "Memory", "RAM usage", false, 2, 111); }
        @Override protected List<String> lines(boolean p) {
            Runtime r = Runtime.getRuntime();
            long used = r.totalMemory() - r.freeMemory(), max = r.maxMemory();
            return List.of(String.format(java.util.Locale.ROOT, "Mem: %d%% %d/%dMB", used * 100 / max, used >> 20, max >> 20));
        }
    }

    // ================================================================= Speed
    public static final class Speed extends HudModule {
        private double smoothed, lx, lz; private boolean init;
        public Speed() { super("speed", "Speed", "Blocks per second", false, 2, 130); }
        @Override public void tick() {
            if (mc.player == null) { init = false; return; }
            double x = mc.player.getX(), z = mc.player.getZ();
            if (init) { double bps = Math.hypot(x - lx, z - lz) * 20; smoothed += (bps - smoothed) * 0.35; }
            lx = x; lz = z; init = true;
        }
        @Override protected List<String> lines(boolean p) { return List.of(String.format(java.util.Locale.ROOT, "%.2f m/s", smoothed)); }
    }

    // ================================================================= Server
    public static final class Server extends HudModule {
        public Server() { super("server", "Server Address", "Shows the server you're on", false, CENTER, 40); }
        @Override protected List<String> lines(boolean p) {
            ServerInfo s = mc.getCurrentServerEntry();
            return List.of(s != null ? s.address : mc.isInSingleplayer() ? "Singleplayer" : "Menu");
        }
    }

    // ================================================================= Day counter
    public static final class Day extends HudModule {
        public Day() { super("day", "Day Counter", "In-game day number", false, 2, 149); }
        @Override protected List<String> lines(boolean p) {
            long t = mc.world != null ? mc.world.getTimeOfDay() : 0;
            return List.of("Day " + (t / 24000L + 1));
        }
    }
}
