package dev.orbit.client.module;

import dev.orbit.client.OrbitClient;
import dev.orbit.client.gui.Render2D;
import dev.orbit.client.gui.WorldProjection;
import dev.orbit.client.module.setting.Setting;
import dev.orbit.client.pvp.Combat;
import dev.orbit.client.pvp.KillEffects;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.*;

/** Orbit's PvP toolkit and a few extras: target HUD, combos, hit timing, pearls, stats, damage numbers, trails… */
public final class PvpModules {
    private PvpModules() {}

    private static float ease(float t) { t = Math.max(0, Math.min(1, t)); return 1 - (float) Math.pow(1 - t, 3); }

    // ================================================================= Kill Effect Editor
    public static final class KillEffectsModule extends Module {
        public final Setting.Mode effect = add(new Setting.Mode("effect", "Effect", "Firework", KillEffects.EFFECTS));
        public final Setting.Mode sound = add(new Setting.Mode("sound", "Sound", "Level up", KillEffects.SOUNDS));
        public final Setting.Slider volume = add(new Setting.Slider("volume", "Volume", 0.8, 0, 1, 0.05, ""));
        public final Setting.Slider pitch = add(new Setting.Slider("pitch", "Pitch", 1, 0.5, 2, 0.05, "x"));
        public final Setting.Slider particles = add(new Setting.Slider("particles", "Particle amount", 30, 5, 80, 1, ""));
        public final Setting.Bool popup = add(new Setting.Bool("popup", "\"Killed …\" popup on screen", true));
        public final Setting.Action preview = add(new Setting.Action("preview", "Try it", "Preview", KillEffects::preview));
        public KillEffectsModule() { super("killeffects", "Kill Effects", "Your own particles, sound and popup when you kill someone", Category.COMBAT, false); }
        public void renderPopup(DrawContext c, int sw, int sh) {
            if (!isEnabled() || !popup.on()) return;
            long t = System.currentTimeMillis() - KillEffects.popupAt;
            if (t > 2200) return;
            float in = ease(t / 250f), out = t > 1700 ? 1 - (t - 1700) / 500f : 1;
            float a = Math.max(0, in * out);
            String s = "☠  Killed " + KillEffects.popupName;
            float k = 1 + (1 - in) * 0.6f;
            int w = Render2D.boldWidth(s);
            dev.orbit.client.compat.Compat.push(c);
            dev.orbit.client.compat.Compat.translate(c, sw / 2f, sh * 0.28f);
            dev.orbit.client.compat.Compat.scale(c, k);
            Render2D.round(c, -w / 2f - 10, -6, w + 20, 18, 9, Render2D.withAlpha(0x120A1E, (int) (200 * a)));
            Render2D.round(c, -w / 2f - 10, 11, (w + 20) * Math.max(0, 1 - t / 2200f), 1, 1, Render2D.withAlpha(Render2D.accent(), (int) (255 * a)));
            Render2D.bold(c, s, -w / 2f, -1, Render2D.withAlpha(0xFFE27A, (int) (255 * a)), false);
            dev.orbit.client.compat.Compat.pop(c);
        }
    }

    // ================================================================= Smart Target HUD
    public static final class TargetHud extends HudModule {
        final Setting.Bool armor = add(new Setting.Bool("armor", "Armor & durability (opponent info)", true));
        final Setting.Bool weapon = add(new Setting.Bool("weapon", "Held item", true));
        private float shown = -1, trail = -1, appear;
        private int lastId = -1;
        private long lastFrame = System.nanoTime();
        public TargetHud() { super("targethud", "Target HUD", "Animated card of who you're fighting: health, armor, distance, weapon", false, HudModule.CENTER, -90); category = Category.COMBAT; }
        @Override protected boolean outerBackground() { return false; }
        @Override protected int pad() { return 0; }
        @Override public boolean hasContent() { return Combat.target() != null || appear > 0.02f; }
        @Override public int contentWidth(boolean p) { return 156; }
        @Override public int contentHeight(boolean p) { return armor.on() ? 54 : 42; }
        @Override protected void renderContent(DrawContext c, boolean preview) {
            long now = System.nanoTime(); float dt = Math.min(0.1f, (now - lastFrame) / 1e9f); lastFrame = now;
            LivingEntity t = Combat.target();
            if (t == null && preview && mc.player != null) t = mc.player;
            appear = Render2D.approach(appear, t != null ? 1 : 0, 10, dt);
            if (t == null) return;
            if (t.getId() != lastId) { lastId = t.getId(); shown = trail = t.getHealth(); }
            float max = Math.max(1, t.getMaxHealth()), hp = Math.max(0, t.getHealth()) + t.getAbsorptionAmount();
            shown = Render2D.approach(shown, hp, 14, dt);
            trail = trail < shown ? shown : Render2D.approach(trail, shown, 3, dt);
            float a = appear;
            int W = contentWidth(false), H = contentHeight(false);
            Render2D.round(c, 0, 0, W, H, 9, Render2D.withAlpha(0x0E0B1A, (int) (215 * a)));
            Render2D.round(c, 0, 0, 3, H, 2, Render2D.withAlpha(Render2D.accent(), (int) (255 * a)));
            // face / icon
            if (t instanceof net.minecraft.client.network.AbstractClientPlayerEntity pl) net.minecraft.client.gui.PlayerSkinDrawer.draw(c, pl.getSkin(), 8, 7, 28);
            else { Render2D.round(c, 8, 7, 28, 28, 6, 0x22FFFFFF); Render2D.item(c, iconFor(t), 14, 13, 16); }
            Render2D.bold(c, Render2D.fit(t.getName().getString(), 80, true), 42, 7, Render2D.withAlpha(0xFFFFFF, (int) (255 * a)), false);
            String dist = String.format(Locale.ROOT, "%.1fm", mc.player == null ? 0 : mc.player.distanceTo(t));
            Render2D.text(c, dist, W - 8 - Render2D.width(dist), 7, Render2D.withAlpha(0xB8B0D8, (int) (255 * a)), false);
            // health bar: green -> yellow -> red, with a white "just lost" trail
            float frac = Math.min(1, shown / max), tf = Math.min(1, trail / max);
            int bx = 42, bw = W - 50, by = 20;
            Render2D.round(c, bx, by, bw, 6, 3, Render2D.withAlpha(0xFFFFFF, (int) (30 * a)));
            if (tf > frac) Render2D.round(c, bx, by, bw * tf, 6, 3, Render2D.withAlpha(0xFFFFFF, (int) (170 * a)));
            int col = frac > 0.5f ? Render2D.lerpColor(0xFFFFD84D, 0xFF4ADE80, (frac - 0.5f) * 2) : Render2D.lerpColor(0xFFFF4D5E, 0xFFFFD84D, frac * 2);
            Render2D.round(c, bx, by, Math.max(6, bw * frac), 6, 3, Render2D.alpha(col, a));
            String hpS = String.format(Locale.ROOT, "%.1f ❤", hp / 2f);
            Render2D.text(c, hpS, bx, by + 9, Render2D.alpha(col, a), false);
            if (weapon.on()) {
                ItemStack held = t.getMainHandStack();
                if (!held.isEmpty()) { Render2D.item(c, held.getItem(), W - 24, by + 6, 16); }
            }
            if (armor.on()) {
                EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
                for (int i = 0; i < 4; i++) {
                    ItemStack st = t.getEquippedStack(slots[i]);
                    int ix = 8 + i * 22, iy = 38;
                    if (st.isEmpty()) { Render2D.round(c, ix, iy, 18, 12, 3, Render2D.withAlpha(0xFFFFFF, (int) (14 * a))); continue; }
                    Render2D.item(c, st.getItem(), ix + 1, iy - 3, 12);
                    if (st.isDamageable()) {
                        float d = 1 - st.getDamage() / (float) Math.max(1, st.getMaxDamage());
                        Render2D.round(c, ix + 13, iy + 1, 3, 10, 1, Render2D.withAlpha(0xFFFFFF, (int) (30 * a)));
                        Render2D.round(c, ix + 13, iy + 1 + 10 * (1 - d), 3, 10 * d, 1, Render2D.alpha(d > 0.5f ? 0xFF4ADE80 : d > 0.2f ? 0xFFFFD84D : 0xFFFF4D5E, a));
                    }
                }
                String extra = t instanceof PlayerEntity && t.isBlocking() ? "Blocking" : t.hurtTime > 0 ? "Hit!" : "";
                if (!extra.isEmpty()) Render2D.text(c, extra, W - 8 - Render2D.width(extra), 40, Render2D.withAlpha(0xFF8C8C, (int) (255 * a)), false);
            }
        }
        private static Item iconFor(LivingEntity e) {
            var id = net.minecraft.registry.Registries.ENTITY_TYPE.getId(e.getType());
            Item egg = net.minecraft.registry.Registries.ITEM.get(net.minecraft.util.Identifier.of(id.getNamespace(), id.getPath() + "_spawn_egg"));
            return egg == Items.AIR ? Items.NAME_TAG : egg;
        }
    }

    // ================================================================= Combo counter
    public static final class ComboCounter extends HudModule {
        private int last; private long popAt;
        public ComboCounter() { super("combo", "Combo Counter", "Counts your hits in a row before you get hit", false, HudModule.CENTER, 40); category = Category.COMBAT; }
        private boolean active() { return Combat.combo > 1 && System.currentTimeMillis() - Combat.comboAt < 3000; }
        @Override public boolean hasContent() { return active(); }
        @Override public int contentWidth(boolean p) { return Math.max(40, Render2D.boldWidth((p && !active() ? 5 : Combat.combo) + "") * 2 + Render2D.width(" COMBO") + 4); }
        @Override public int contentHeight(boolean p) { return 18; }
        @Override protected void renderContent(DrawContext c, boolean preview) {
            int n = preview && !active() ? 5 : Combat.combo;
            if (n != last) { last = n; popAt = System.currentTimeMillis(); }
            float pop = 1 + 0.35f * Math.max(0, 1 - (System.currentTimeMillis() - popAt) / 180f);
            String num = String.valueOf(n);
            dev.orbit.client.compat.Compat.push(c);
            dev.orbit.client.compat.Compat.translate(c, 1, 1);
            dev.orbit.client.compat.Compat.scale(c, 2 * pop);
            Render2D.bold(c, num, 0, 0, n >= 10 ? 0xFFFF7BD0 : n >= 5 ? 0xFFFFD84D : 0xFFFFFFFF, shadow.on());
            dev.orbit.client.compat.Compat.pop(c);
            Render2D.text(c, "COMBO", Render2D.boldWidth(num) * 2 + 4, 9, Render2D.accent(), shadow.on());
        }
    }

    // ================================================================= Hit delay indicator
    public static final class HitDelay extends HudModule {
        public HitDelay() { super("hitdelay", "Hit Delay Indicator", "Attack cooldown bar and time between your hits", false, HudModule.CENTER, -70); category = Category.COMBAT; }
        @Override public boolean hasContent() { return mc.player != null; }
        @Override public int contentWidth(boolean p) { return 80; }
        @Override public int contentHeight(boolean p) { return 16; }
        @Override protected void renderContent(DrawContext c, boolean preview) {
            float cd = mc.player == null ? 1 : mc.player.getAttackCooldownProgress(0);
            Render2D.round(c, 0, 0, 80, 5, 2, 0x40FFFFFF);
            int col = cd >= 1 ? 0xFF4ADE80 : Render2D.lerpColor(0xFFFF4D5E, 0xFFFFD84D, cd);
            Render2D.round(c, 0, 0, 80 * cd, 5, 2, col);
            String s = Combat.lastHitInterval > 0 && System.currentTimeMillis() - Combat.lastHitAt < 5000 ? Combat.lastHitInterval + " ms between hits" : cd >= 1 ? "Ready" : Math.round(cd * 100) + "%";
            Render2D.text(c, s, 0, 7, textColor.argb(), shadow.on());
        }
    }

    // ================================================================= Pearl tracker
    public static final class PearlTracker extends HudModule {
        public PearlTracker() { super("pearls", "Pearl Tracker", "Ender pearl cooldown, how many you have and your pearls in the air", false, -2, -140); category = Category.COMBAT; }
        private int count() { if (mc.player == null) return 0; var inv = mc.player.getInventory(); int n = 0; for (int i = 0; i < inv.size(); i++) if (inv.getStack(i).isOf(Items.ENDER_PEARL)) n += inv.getStack(i).getCount(); return n; }
        @Override public boolean hasContent() { return mc.player != null && count() > 0; }
        @Override protected List<String> lines(boolean p) {
            if (mc.player == null) return p ? List.of("Pearls x16  ·  Ready") : List.of();
            var cdm = mc.player.getItemCooldownManager();
            ItemStack pearl = new ItemStack(Items.ENDER_PEARL);
            String st = cdm.isCoolingDown(pearl) ? String.format(Locale.ROOT, "§c%.1fs", cdm.getCooldownProgress(pearl, 0) * 1.0f) : "§aReady";
            List<String> out = new ArrayList<>();
            out.add("Pearls x" + count() + "  ·  " + st);
            if (mc.world != null) for (var e : mc.world.getEntities())
                if (e instanceof net.minecraft.entity.projectile.thrown.EnderPearlEntity ep && ep.getOwner() == mc.player)
                    out.add(String.format(Locale.ROOT, "§7✈ in the air %.1fs · %.0fm", ep.age / 20f, ep.distanceTo(mc.player)));
            return out;
        }
    }

    // ================================================================= W-tap counter
    public static final class WTapCounter extends HudModule {
        public WTapCounter() { super("wtap", "W-Tap Counter", "Counts your W-taps (letting go of W after a hit)", false, 2, -100); category = Category.COMBAT; }
        @Override protected List<String> lines(boolean p) {
            int pct = Combat.hits == 0 ? 0 : Math.round(Combat.wtaps * 100f / Combat.hits);
            return List.of("W-Taps " + Combat.wtaps + "  (" + pct + "% of hits)");
        }
    }

    // ================================================================= Fight statistics
    public static final class FightStats extends HudModule {
        final Setting.Action reset = add(new Setting.Action("reset", "Start counting again", "Reset", () -> Combat.reset(Combat.sessionPlace)));
        public FightStats() { super("fightstats", "Fight Statistics", "Hits, misses, accuracy, combos, damage, kills", false, 2, 220); category = Category.COMBAT; }
        @Override protected List<String> lines(boolean p) {
            int total = Combat.hits + Combat.misses, acc = total == 0 ? 0 : Math.round(Combat.hits * 100f / total);
            return List.of(
                    "§lFight stats",
                    "Hits §a" + Combat.hits + "§r  Misses §c" + Combat.misses + "§r  (" + acc + "%)",
                    "Best combo §e" + Combat.bestCombo,
                    String.format(Locale.ROOT, "Damage §a%.1f§r dealt · §c%.1f§r taken", Combat.damageDealt / 2f, Combat.damageTaken / 2f),
                    "Kills §a" + Combat.kills + "§r  Deaths §c" + Combat.deaths + "§r  W-taps " + Combat.wtaps);
        }
    }

    // ================================================================= Replay HUD (fight timeline)
    public static final class FightTimeline extends HudModule {
        final Setting.Slider window = add(new Setting.Slider("window", "Seconds shown", 15, 5, 30, 1, "s"));
        final Setting.Bool always = add(new Setting.Bool("always", "Show outside of fights too", false));
        public FightTimeline() { super("timeline", "Replay HUD", "Your last fight as a mini timeline of hits given and taken", false, HudModule.CENTER, -120); category = Category.COMBAT; }
        @Override public boolean hasContent() { return always.on() || System.currentTimeMillis() - Math.max(Combat.lastHitAt, Combat.lastHurtAt) < 12_000; }
        @Override public int contentWidth(boolean p) { return 170; }
        @Override public int contentHeight(boolean p) { return 30; }
        @Override protected void renderContent(DrawContext c, boolean preview) {
            long now = System.currentTimeMillis(), span = (long) (window.get() * 1000);
            int W = 170;
            List<Combat.Event> ev;
            synchronized (Combat.TIMELINE) { ev = new ArrayList<>(Combat.TIMELINE); }
            if (preview && ev.isEmpty()) for (int i = 0; i < 9; i++) ev.add(new Combat.Event(now - 1200L * i - 300, i % 4 == 3 ? 1 : i == 0 ? 2 : 0, 2 + i % 3));
            int dealt = 0, taken = 0;
            for (var e : ev) if (now - e.at() < span) { if (e.type() == 0) dealt++; else if (e.type() == 1) taken++; }
            Render2D.text(c, "Last fight  §a" + dealt + " hits§r  ·  §c" + taken + " taken", 0, 0, textColor.argb(), shadow.on());
            int by = 12;
            Render2D.round(c, 0, by + 8, W, 1, 1, 0x50FFFFFF);
            for (int k = 0; k <= 4; k++) Render2D.round(c, W * k / 4f, by + 6, 1, 5, 1, 0x30FFFFFF);
            for (var e : ev) {
                long age = now - e.at();
                if (age > span) continue;
                float x = W * (1 - age / (float) span);
                float h = Math.min(8, 2 + e.amount());
                if (e.type() == 2) Render2D.text(c, "☠", x - 3, by, 0xFFFFE27A, true);
                else if (e.type() == 0) Render2D.round(c, x - 1, by + 8 - h, 2, h, 1, 0xFF4ADE80);
                else Render2D.round(c, x - 1, by + 9, 2, h, 1, 0xFFFF4D5E);
            }
            Render2D.text(c, "now", W - Render2D.width("now"), by + 11, 0x80FFFFFF, false);
        }
    }

    // ================================================================= Damage numbers
    public static final class DamageNumbers extends Module {
        final Setting.Mode style = add(new Setting.Mode("style", "Animation", "Pop & rise", "Pop & rise", "Bounce", "Simple"));
        final Setting.Bool hearts = add(new Setting.Bool("hearts", "Show as hearts (÷2)", true));
        final Setting.Bool heals = add(new Setting.Bool("heals", "Show healing too", true));
        final Setting.Slider size = add(new Setting.Slider("size", "Size", 1, 0.5, 2, 0.05, "x"));
        public DamageNumbers() { super("damagenumbers", "Damage Numbers", "Floating, animated damage numbers over everything that gets hurt", Category.COMBAT, false); }
        public void render(DrawContext c, int sw, int sh) {
            if (!isEnabled() || mc.world == null) return;
            long now = System.currentTimeMillis();
            HitColors hc = OrbitClient.modules().get("hitcolors") instanceof HitColors h && h.isEnabled() ? h : null;
            List<Combat.DamageNumber> list;
            synchronized (Combat.NUMBERS) { list = new ArrayList<>(Combat.NUMBERS); }
            for (var n : list) {
                if (n.heal() && !heals.on()) continue;
                float t = (now - n.at()) / 1400f;
                double rise = style.is("Bounce") ? Math.abs(Math.sin(t * Math.PI * 2.2)) * 0.4 * (1 - t) + t * 0.3 : t * 0.9;
                float[] p = WorldProjection.project(n.x() + n.drift() * t, n.y() + rise, n.z(), sw, sh);
                if (p == null || p[2] > 40) continue;
                float a = t < 0.7f ? 1 : 1 - (t - 0.7f) / 0.3f;
                float pop = style.is("Simple") ? 1 : 1 + 0.6f * Math.max(0, 1 - t * 6);
                float k = size.f() * pop * (float) Math.max(0.6, Math.min(1.4, 8 / Math.max(2, p[2])));
                float v = hearts.on() ? n.amount() / 2f : n.amount();
                String s = (n.heal() ? "+" : "") + (v == Math.round(v) ? String.valueOf((int) v) : String.format(Locale.ROOT, "%.1f", v)) + (hearts.on() ? "❤" : "");
                int col = n.heal() ? 0xFF6CFF8A : n.crit() ? (hc != null ? hc.critColor.argb() : 0xFFFFC23D) : (hc != null ? hc.numberColor.argb() : 0xFFFF5A5A);
                dev.orbit.client.compat.Compat.push(c);
                dev.orbit.client.compat.Compat.translate(c, p[0], p[1]);
                dev.orbit.client.compat.Compat.scale(c, k);
                Render2D.bold(c, s, -Render2D.boldWidth(s) / 2f, -4, Render2D.alpha(col, a), true);
                if (n.crit()) Render2D.text(c, "CRIT", -Render2D.width("CRIT") / 2f, -13, Render2D.alpha(0xFFFFE27A, a), true);
                dev.orbit.client.compat.Compat.pop(c);
            }
        }
    }

    // ================================================================= Hit colour editor
    public static final class HitColors extends Module {
        public final Setting.Color hurt = add(new Setting.Color("hurt", "Hurt flash on entities", 0xB3FF0000));
        public final Setting.Color numberColor = add(new Setting.Color("number", "Damage number colour", 0xFFFF5A5A));
        public final Setting.Color critColor = add(new Setting.Color("crit", "Critical hit colour", 0xFFFFC23D));
        private int applied = 0;
        public HitColors() { super("hitcolors", "Hit Colours", "Recolour the red hurt flash and the damage numbers", Category.COMBAT, false); }
        @Override public void tick() { if (applied != hurt.argb()) { apply(hurt.argb()); applied = hurt.argb(); } }
        @Override protected void onDisable() { apply(0xB3FF0000); applied = 0; }
        private void apply(int argb) {
            try {
                var tex = ((dev.orbit.client.mixin.OverlayTextureAccessor) mc.gameRenderer.getOverlayTexture()).orbit$texture();
                var img = tex.getImage();
                if (img == null) return;
                for (int y = 0; y < 8; y++) for (int x = 0; x < 16; x++) img.setColorArgb(x, y, argb);
                tex.upload();
            } catch (Throwable ignored) { }
        }
    }

    // ================================================================= Projectile predictor
    public static final class ProjectilePredictor extends Module {
        final Setting.Color color = add(new Setting.Color("color", "Line colour", 0xFF9AD8FF));
        final Setting.Bool landing = add(new Setting.Bool("landing", "Show where it lands", true));
        public ProjectilePredictor() { super("trajectories", "Projectile Predictor", "See where pearls, snowballs, eggs, potions, arrows and tridents will fly", Category.COMBAT, false); }
        /** {speed, gravity, drag} for what you're holding, or null */
        private float[] physics(ItemStack st) {
            if (st.isOf(Items.ENDER_PEARL) || st.isOf(Items.SNOWBALL) || st.isOf(Items.EGG) || st.isOf(Items.BLUE_EGG) || st.isOf(Items.BROWN_EGG)) return new float[]{1.5f, 0.03f, 0.99f};
            if (st.isOf(Items.SPLASH_POTION) || st.isOf(Items.LINGERING_POTION)) return new float[]{0.5f, 0.05f, 0.99f};
            if (st.isOf(Items.EXPERIENCE_BOTTLE)) return new float[]{0.7f, 0.07f, 0.99f};
            if (st.isOf(Items.WIND_CHARGE)) return new float[]{1.5f, 0f, 1f};
            if (st.isOf(Items.BOW) && mc.player.isUsingItem() && mc.player.getActiveItem() == st) {
                float pull = net.minecraft.item.BowItem.getPullProgress(mc.player.getItemUseTime());
                return pull < 0.1f ? null : new float[]{pull * 3, 0.05f, 0.99f};
            }
            if (st.isOf(Items.CROSSBOW) && net.minecraft.item.CrossbowItem.isCharged(st)) return new float[]{3.15f, 0.05f, 0.99f};
            if (st.isOf(Items.TRIDENT) && mc.player.isUsingItem() && mc.player.getActiveItem() == st) return new float[]{2.5f, 0.05f, 0.99f};
            return null;
        }
        public void render(DrawContext c, int sw, int sh) {
            if (!isEnabled() || mc.player == null || mc.world == null) return;
            float[] ph = physics(mc.player.getMainHandStack());
            if (ph == null) ph = physics(mc.player.getOffHandStack());
            if (ph == null) return;
            float td = mc.getRenderTickCounter().getTickProgress(false);
            Vec3d pos = mc.player.getLerpedPos(td).add(0, mc.player.getStandingEyeHeight() - 0.1, 0);
            Vec3d vel = mc.player.getRotationVec(td).multiply(ph[0]);
            Vec3d pv = mc.player.getVelocity();
            vel = vel.add(pv.x, mc.player.isOnGround() ? 0 : pv.y, pv.z);
            List<Vec3d> pts = new ArrayList<>();
            pts.add(pos);
            Vec3d hit = null;
            for (int i = 0; i < 240; i++) {
                Vec3d next = pos.add(vel);
                var res = mc.world.raycast(new RaycastContext(pos, next, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
                if (res.getType() != net.minecraft.util.hit.HitResult.Type.MISS) { hit = res.getPos(); pts.add(hit); break; }
                pos = next; pts.add(pos);
                boolean water = mc.world.getFluidState(net.minecraft.util.math.BlockPos.ofFloored(pos)).isIn(net.minecraft.registry.tag.FluidTags.WATER);
                vel = vel.multiply(water ? 0.8 : ph[2]).add(0, -ph[1], 0);
                if (pos.y < mc.world.getBottomY() - 8) break;
            }
            int col = color.argb();
            float[] prev = null;
            for (int i = 1; i < pts.size(); i++) {
                Vec3d p = pts.get(i);
                if (p.squaredDistanceTo(mc.player.getEyePos()) < 1.2) continue;
                float[] s = WorldProjection.project(p.x, p.y, p.z, sw, sh);
                if (s == null) { prev = null; continue; }
                if (prev != null) {
                    float dx = s[0] - prev[0], dy = s[1] - prev[1], len = (float) Math.hypot(dx, dy);
                    int n = Math.max(1, (int) (len / 2));
                    for (int k = 0; k < n; k++) Render2D.disc(c, prev[0] + dx * k / n, prev[1] + dy * k / n, 0.9f, Render2D.withAlpha(col, 200));
                }
                prev = s;
            }
            if (landing.on() && hit != null) {
                float[] s = WorldProjection.project(hit.x, hit.y, hit.z, sw, sh);
                if (s != null) {
                    Render2D.disc(c, s[0], s[1], 5, Render2D.withAlpha(col, 70));
                    Render2D.disc(c, s[0], s[1], 2.4f, 0xFFFFFFFF);
                    String d = Math.round(hit.distanceTo(mc.player.getEyePos())) + "m";
                    Render2D.text(c, d, s[0] - Render2D.width(d) / 2f, s[1] + 7, 0xFFFFFFFF, true);
                }
            }
        }
    }

    // ================================================================= Movement trails
    public static final class MovementTrails extends Module {
        final Setting.Mode style = add(new Setting.Mode("style", "Trail", "Rainbow", "Rainbow", "Flames", "Souls", "Hearts", "Clouds", "Stars", "Notes", "Snow", "Sparkles"));
        final Setting.Slider density = add(new Setting.Slider("density", "Density", 2, 1, 6, 1, ""));
        final Setting.Bool sprintOnly = add(new Setting.Bool("sprint", "Only while sprinting", false));
        private int tick;
        public MovementTrails() { super("trails", "Movement Trails", "A trail of particles behind you as you move (only you see it)", Category.RENDER, false); }
        @Override public void tick() {
            var p = mc.player;
            if (p == null || mc.world == null) return;
            tick++;
            Vec3d v = p.getVelocity();
            if (v.horizontalLengthSquared() < 0.0009 || (sprintOnly.on() && !p.isSprinting())) return;
            for (int i = 0; i < density.i(); i++) {
                double x = p.getX() - v.x * 1.5 + (Math.random() - 0.5) * 0.4, y = p.getY() + 0.15 + Math.random() * 0.3, z = p.getZ() - v.z * 1.5 + (Math.random() - 0.5) * 0.4;
                switch (style.get()) {
                    case "Rainbow" -> mc.world.addParticleClient(new DustParticleEffect(0xFFFFFF & java.awt.Color.HSBtoRGB((tick % 60) / 60f, 0.8f, 1f), 1.1f), x, y, z, 0, 0, 0);
                    case "Flames" -> mc.world.addParticleClient(ParticleTypes.FLAME, x, y, z, 0, 0.01, 0);
                    case "Souls" -> mc.world.addParticleClient(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 0, 0.01, 0);
                    case "Hearts" -> { if (tick % 3 == 0) mc.world.addParticleClient(ParticleTypes.HEART, x, y + 0.3, z, 0, 0, 0); }
                    case "Clouds" -> mc.world.addParticleClient(ParticleTypes.CLOUD, x, y, z, 0, 0, 0);
                    case "Stars" -> mc.world.addParticleClient(ParticleTypes.END_ROD, x, y, z, 0, 0.01, 0);
                    case "Notes" -> { if (tick % 2 == 0) mc.world.addParticleClient(ParticleTypes.NOTE, x, y + 0.4, z, Math.random(), 0, 0); }
                    case "Snow" -> mc.world.addParticleClient(ParticleTypes.SNOWFLAKE, x, y, z, 0, 0, 0);
                    default -> mc.world.addParticleClient(ParticleTypes.ELECTRIC_SPARK, x, y, z, 0, 0.02, 0);
                }
            }
        }
    }

    // ================================================================= Chat bubbles
    public static final class ChatBubbles extends Module {
        public final Setting.Slider seconds = add(new Setting.Slider("seconds", "Show for", 6, 2, 15, 1, "s"));
        public final Setting.Bool mine = add(new Setting.Bool("mine", "Show my own (third person)", true));
        public record Bubble(List<String> lines, long at) {}
        public static final Map<UUID, Bubble> BUBBLES = new java.util.concurrent.ConcurrentHashMap<>();
        public ChatBubbles() { super("chatbubbles", "Chat Bubbles", "Chat messages pop up above the player who sent them", Category.PLAYER, false); }
        public static void said(UUID who, String msg) {
            if (who == null || msg == null || msg.isBlank()) return;
            List<String> lines = new ArrayList<>();
            StringBuilder cur = new StringBuilder();
            for (String w : msg.trim().split("\\s+")) {
                if (cur.length() + w.length() > 28 && cur.length() > 0) { lines.add(cur.toString()); cur.setLength(0); }
                if (cur.length() > 0) cur.append(' ');
                cur.append(w.length() > 40 ? w.substring(0, 40) + "…" : w);
                if (lines.size() == 3) break;
            }
            if (cur.length() > 0 && lines.size() < 3) lines.add(cur.toString());
            BUBBLES.put(who, new Bubble(lines, System.currentTimeMillis()));
        }
        /** for servers that send chat as plain text: find "Name » msg", "<Name> msg", "Name: msg" */
        public static void parse(String plain) {
            var mc = net.minecraft.client.MinecraftClient.getInstance();
            if (mc.getNetworkHandler() == null) return;
            for (var e : mc.getNetworkHandler().getPlayerList()) {
                String n = e.getProfile().name();
                int i = plain.indexOf(n);
                if (i < 0) continue;
                String rest = plain.substring(i + n.length());
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("^\\s*(?:>|»|:|→|\\||⇒)\\s*(.+)$").matcher(rest);
                if (m.find()) { said(e.getProfile().id(), m.group(1)); return; }
            }
        }
        public List<String> linesFor(UUID who) {
            Bubble b = BUBBLES.get(who);
            if (b == null) return null;
            if (System.currentTimeMillis() - b.at() > seconds.get() * 1000) { BUBBLES.remove(who); return null; }
            return b.lines();
        }
    }

    // ================================================================= Inventory profiles
    public static final class InventoryProfiles extends Module {
        final Setting.Mode slot = add(new Setting.Mode("slot", "Profile", "1", "1", "2", "3", "Per server"));
        final Setting.Action save = add(new Setting.Action("save", "Save my hotbar & offhand to this profile", "Save", () -> saveProfile(true)));
        final Setting.Action load = add(new Setting.Action("load", "Sort my inventory into this profile", "Apply", this::apply));
        final Setting.Bool auto = add(new Setting.Bool("auto", "Apply the server's profile automatically when I join", true));
        private static final java.nio.file.Path FILE = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("orbit-inventory.json");
        private static Map<String, String[]> data;
        private long applyAt;
        public InventoryProfiles() { super("invprofiles", "Inventory Profiles", "Save hotbar layouts and switch between them (per server too)", Category.PLAYER, false); }
        private static Map<String, String[]> data() {
            if (data == null) {
                try { if (java.nio.file.Files.exists(FILE)) data = new com.google.gson.Gson().fromJson(java.nio.file.Files.readString(FILE), new com.google.gson.reflect.TypeToken<HashMap<String, String[]>>() {}.getType()); } catch (Exception ignored) { }
                if (data == null) data = new HashMap<>();
            }
            return data;
        }
        private String key() {
            if (!slot.is("Per server")) return "slot" + slot.get();
            var s = mc.getCurrentServerEntry();
            return "server:" + (s != null ? s.address.toLowerCase() : mc.getServer() != null ? "single" : "unknown");
        }
        private void saveProfile(boolean say) {
            if (mc.player == null) return;
            var inv = mc.player.getInventory();
            String[] ids = new String[10];
            for (int i = 0; i < 9; i++) ids[i] = id(inv.getStack(i));
            ids[9] = id(mc.player.getOffHandStack());
            data().put(key(), ids);
            try { java.nio.file.Files.writeString(FILE, new com.google.gson.Gson().toJson(data())); } catch (Exception ignored) { }
            if (say) mc.player.sendMessage(net.minecraft.text.Text.literal("§d[Orbit] §fSaved inventory profile §e" + slot.get()), true);
        }
        private static String id(ItemStack st) { return st.isEmpty() ? "" : net.minecraft.registry.Registries.ITEM.getId(st.getItem()).toString(); }
        public void apply() {
            String[] want = data().get(key());
            if (want == null || mc.player == null || mc.interactionManager == null) { if (mc.player != null) mc.player.sendMessage(net.minecraft.text.Text.literal("§d[Orbit] §7No saved profile here yet — press Save first"), true); return; }
            if (mc.currentScreen != null && !(mc.currentScreen instanceof net.minecraft.client.gui.screen.ingame.InventoryScreen)) return;
            var h = mc.player.playerScreenHandler;
            int moves = 0;
            for (int i = 0; i < 10; i++) {
                if (want[i] == null || want[i].isEmpty()) continue;
                int target = i == 9 ? 45 : 36 + i;
                if (id(h.getSlot(target).getStack()).equals(want[i])) continue;
                for (int k = 9; k <= 44; k++) {
                    if (k == target) continue;
                    if (k >= 36 && k - 36 < i && i < 9 && want[k - 36] != null && want[k - 36].equals(id(h.getSlot(k).getStack()))) continue; // already placed
                    if (id(h.getSlot(k).getStack()).equals(want[i])) {
                        mc.interactionManager.clickSlot(h.syncId, k, i == 9 ? 40 : i, net.minecraft.screen.slot.SlotActionType.SWAP, mc.player);
                        moves++;
                        break;
                    }
                }
            }
            mc.player.sendMessage(net.minecraft.text.Text.literal("§d[Orbit] §fApplied inventory profile §7(" + moves + " moves)"), true);
        }
        public void onJoin() { if (isEnabled() && auto.on() && slot.is("Per server")) applyAt = System.currentTimeMillis() + 2500; }
        @Override public void tick() { if (applyAt > 0 && System.currentTimeMillis() > applyAt) { applyAt = 0; if (data().containsKey(key())) apply(); } }
    }

    // ================================================================= Session recap
    public static final class SessionRecap extends Module {
        public final Setting.Slider minMinutes = add(new Setting.Slider("min", "Only after playing at least", 2, 0, 30, 1, " min"));
        public SessionRecap() { super("recap", "Session Recap", "A summary of your session (kills, hits, accuracy, distance…) when you leave", Category.CLIENT, true); }
    }
}
