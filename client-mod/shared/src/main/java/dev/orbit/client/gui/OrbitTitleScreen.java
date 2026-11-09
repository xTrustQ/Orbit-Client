package dev.orbit.client.gui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import dev.orbit.client.OrbitClient;
import dev.orbit.client.bridge.AccountSwitcher;
import dev.orbit.client.bridge.OrbitBridge;
import dev.orbit.client.compat.Compat;
import dev.orbit.client.cosmetics.NameSkins;
import dev.orbit.client.emote.Emote;
import dev.orbit.client.emote.Emotes;
import dev.orbit.client.mixin.MinecraftClientAccessor;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.item.Item;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static dev.orbit.client.gui.Render2D.*;

/**
 * Orbit's title screen: a tab bar (Home, Locker, Screenshots, Accounts), your 3D skin, and a
 * switch back to Minecraft's classic menu. Locker and Accounts talk to the launcher (OrbitBridge).
 */
public final class OrbitTitleScreen extends OrbitBaseScreen {
    /** Set to let the next vanilla TitleScreen through once. */
    public static boolean allowVanillaOnce;

    private enum Tab { HOME("Home"), LOCKER("Locker"), SHOTS("Screenshots"), ACCOUNTS("Accounts"), FRIENDS("Friends");
        final String title; Tab(String t) { title = t; } }
    private enum Sub { SKINS("Skins"), CAPES("Capes"), COSMETICS("Cosmetics"), EMOTES("Emotes");
        final String title; Sub(String t) { title = t; } }

    private static Tab tab = Tab.HOME;
    private static Sub sub = Sub.SKINS;

    /** half = may share a row with the next half button */
    private record Button(String label, Runnable action, boolean half) { Button(String l, Runnable a) { this(l, a, false); } }
    private long openedAt = System.currentTimeMillis();
    // frame timing (smooth animations at any FPS)
    private long lastFrameN = System.nanoTime();
    private float frameDt = 1 / 60f;
    // sliding white pill under the active tab
    private static float gliderX = -1, gliderW;
    // launch intro (once per game start)
    private static boolean introPlayed;
    private long introStart = -1;
    private boolean introSound;
    private static final float INTRO = 2.7f, INTRO_FADE = 0.75f;
    private final List<Button> buttons = new ArrayList<>();
    private final java.util.Map<String, Float> hover = new java.util.HashMap<>();
    private PlayerPreview skin;

    // text inputs
    private String input = "", inputFor = "";
    private String status = "";
    private int statusColor = MUTED;
    private long statusAt;

    // launcher data (filled async)
    private volatile List<JsonObject> accounts, skins, capes;
    private boolean loadingAccounts, loadingSkins, loadingCapes;

    // screenshots
    private List<File> shots;
    private File preview;
    private float scroll, scrollTarget;

    // locker preview: shown on the 3D model before you apply it
    private SkinTextures previewSkin;
    private String previewName;       // username being previewed (skin loads async)
    private Identifier previewCape;
    private String previewLabel = "";
    private Runnable previewApply;
    // "click again to confirm" for deletes
    private String confirmKey = "";
    private long confirmAt;
    private boolean confirm(String key) {
        long now = System.currentTimeMillis();
        if (key.equals(confirmKey) && now - confirmAt < 3000) { confirmKey = ""; return true; }
        confirmKey = key; confirmAt = now;
        return false;
    }
    private boolean confirming(String key) { return key.equals(confirmKey) && System.currentTimeMillis() - confirmAt < 3000; }
    private void clearPreview() {
        previewSkin = null; previewName = null; previewCape = null; previewLabel = ""; previewApply = null;
        if (skin != null) skin.showBack(false);
    }

    /** true = opened with Esc inside a world/server (pause menu): Game tab instead of Home, no Accounts tab */
    private final boolean ingame;
    /** lets the next vanilla pause menu through once (Classic button in-game) */
    public static boolean allowVanillaPause;
    // tab transition: slide + fade like the launcher
    private static long switchedAt;
    private static int switchDir;

    public OrbitTitleScreen() { this(false); }
    public OrbitTitleScreen(boolean ingame) { super(Text.literal("Orbit")); this.ingame = ingame; }
    /** the Orbit pause menu */
    public static OrbitTitleScreen pause() { tab = Tab.HOME; return new OrbitTitleScreen(true); }
    private String title(Tab t) { return ingame && t == Tab.HOME ? "Game" : t.title; }
    private List<Tab> tabs() {
        List<Tab> l = new ArrayList<>(List.of(Tab.values()));
        if (ingame) l.remove(Tab.ACCOUNTS);
        return l;
    }
    /** Opens the title screen on a tab: "home", "locker", "shots" or "accounts" (tests / shortcuts). */
    /** Test hook: preview a cape texture on the model. */
    public static net.minecraft.util.Identifier testCape;
    public static OrbitTitleScreen onTab(String name) {
        tab = switch (name) { case "locker", "cosmetics", "emotes" -> Tab.LOCKER; case "shots" -> Tab.SHOTS; case "accounts" -> Tab.ACCOUNTS; case "friends" -> Tab.FRIENDS; default -> Tab.HOME; };
        sub = name.equals("cosmetics") ? Sub.COSMETICS : name.equals("emotes") ? Sub.EMOTES : Sub.SKINS;
        return new OrbitTitleScreen();
    }
    /** Friends tab with a chat open (tests / "message" shortcuts) */
    public static OrbitTitleScreen onFriend(String uuid) { OrbitTitleScreen s = onTab("friends"); s.selectedFriend = uuid; return s; }
    /** pause menu opened on a tab (e.g. "emotes" from the emote wheel) */
    public static OrbitTitleScreen pauseOn(String name) { onTab(name); return new OrbitTitleScreen(true); }
    @Override public boolean shouldCloseOnEsc() { return false; }

    // ------------------------------------------------------------------ init
    @Override
    protected void init() {
        buttons.clear();
        if (ingame && tab == Tab.ACCOUNTS) tab = Tab.HOME;
        if (ingame && client.player != null) {
            buttons.add(new Button("Back to game", () -> client.setScreen(null)));
            buttons.add(new Button("Advancements", () -> client.setScreen(new net.minecraft.client.gui.screen.advancement.AdvancementsScreen(client.player.networkHandler.getAdvancementHandler(), this))));
            buttons.add(new Button("Statistics", () -> client.setScreen(new net.minecraft.client.gui.screen.StatsScreen(this, client.player.getStatHandler()))));
            if (client.isIntegratedServerRunning() && client.getServer() != null && !client.getServer().isRemote())
                buttons.add(new Button("Open to LAN", () -> client.setScreen(new net.minecraft.client.gui.screen.OpenToLanScreen(this))));
            buttons.add(new Button("Orbit Mods", () -> client.setScreen(new OrbitScreen(null))));
        } else {
            buttons.add(new Button("Singleplayer", () -> client.setScreen(new OrbitWorldsScreen(this))));
            buttons.add(new Button("Multiplayer", () -> client.setScreen(new OrbitServersScreen(this))));
            buttons.add(new Button("Orbit Mods", () -> client.setScreen(new OrbitScreen(null))));
        }
        if (FabricLoader.getInstance().isModLoaded("modmenu")) buttons.add(new Button("Mod Menu", this::openModMenu));
        // pause menu: everything after "Back to game" goes two per row so it all fits
        if (ingame) for (int i = 1; i < buttons.size(); i++) buttons.set(i, new Button(buttons.get(i).label(), buttons.get(i).action(), true));
        skin = null;
        tileSupplier = skinSupplier();
        if ((tab == Tab.HOME || tab == Tab.LOCKER || (tab == Tab.ACCOUNTS && width > 520)) && width > 360) {
            int h = Math.min(170, height - 120), w = Math.round(h * 0.6f);
            skin = new PlayerPreview(skinCenterX() - w / 2, Math.max(48, height / 2 - h / 2 - 6), w, h, skinSupplier());
            skin.autoSpin = tab == Tab.HOME && OrbitClient.modules().ui().menuSpin.on();
            if (testCape != null) skin.setSpin(160);
        }
    }

    private int skinCenterX() {
        if (ingame && tab == Tab.HOME) return (pX() + pW() + width) / 2; // centred in the space next to the pause panel
        return tab == Tab.HOME ? Math.round(width * 0.74f) : Math.round(width * 0.8f);
    }

    private Supplier<SkinTextures> skinSupplier() {
        var session = client.getSession();
        GameProfile profile;
        var future = ((MinecraftClientAccessor) client).orbit$gameProfileFuture();
        profile = future != null && future.isDone() && future.getNow(null) != null ? future.getNow(null).profile() : new GameProfile(session.getUuidOrNull(), session.getUsername());
        Supplier<SkinTextures> base = client.getSkinProvider().supplySkinTextures(profile, false);
        String name = session.getUsername();
        boolean hasTextures = !profile.properties().get("textures").isEmpty();
        return () -> {
            SkinTextures b = base.get();
            if (!hasTextures) { SkinTextures n = NameSkins.get(name); if (n != null) b = n; }
            if (previewSkin != null) b = new SkinTextures(previewSkin.body(), b.cape(), b.elytra(), previewSkin.model(), false);
            Identifier capeTex = previewCape != null ? previewCape : testCape;
            if (capeTex != null) {
                Identifier previewCape = capeTex;
                var asset = new net.minecraft.util.AssetInfo.TextureAssetInfo(previewCape, previewCape);
                b = new SkinTextures(b.body(), asset, asset, b.model(), b.secure());
            }
            return b;
        };
    }

    private void openModMenu() {
        try {
            Class<?> c = Class.forName("com.terraformersmc.modmenu.gui.ModsScreen");
            client.setScreen((Screen) c.getConstructor(Screen.class).newInstance(this));
        } catch (ReflectiveOperationException e) { OrbitClient.LOG.warn("Could not open Mod Menu", e); }
    }

    private void switchTab(Tab t) {
        if (t == tab) return;
        switchDir = Integer.compare(t.ordinal(), tab.ordinal());
        switchedAt = System.currentTimeMillis();
        tab = t; input = ""; inputFor = ""; preview = null; scroll = scrollTarget = 0; clearPreview(); clearHover();
        Sounds.tab();
        clearAndInit();
    }

    private void say(String s, boolean ok) { status = s; statusColor = ok ? 0xFF8CF5B4 : 0xFFFF8C8C; statusAt = System.currentTimeMillis(); }

    @Override
    public void renderBackground(DrawContext c, int mouseX, int mouseY, float delta) {
        if (ingame && client.world != null) { // the world stays visible behind the menu
            super.renderBackground(c, mouseX, mouseY, delta);
            // lighter on the Game tab so the world stays visible next to the panel
            if (tab == Tab.HOME) c.fillGradient(0, 0, width, height, 0x55050310, 0x99050310);
            else c.fillGradient(0, 0, width, height, 0x88050310, 0xCC050310);
            return;
        }
        SpaceBackground.draw(c, width, height, mouseX, mouseY, true);
        if (tab != Tab.HOME) c.fillGradient(0, 0, width, height, 0x66050310, 0x99050310);
    }

    // ------------------------------------------------------------------ render
    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        if (!ingame && dev.orbit.client.pvp.Combat.pendingRecap != null) { // just left a world/server: show the recap first
            var snap = dev.orbit.client.pvp.Combat.pendingRecap;
            dev.orbit.client.pvp.Combat.pendingRecap = null;
            client.setScreen(new SessionRecapScreen(this, snap));
            return;
        }
        long nowN = System.nanoTime();
        frameDt = Math.min(0.1f, (nowN - lastFrameN) / 1e9f);
        lastFrameN = nowN;
        // launch intro: logo + name on black, then the menu fades in underneath
        float ie = -1;
        if (!introPlayed && !ingame) {
            long now = System.currentTimeMillis();
            if (introStart < 0) { introStart = now; openedAt = now + (long) ((INTRO - INTRO_FADE) * 1000); }
            ie = (now - introStart) / 1000f;
            if (ie >= INTRO) { introPlayed = true; ie = -1; }
            else if (ie < INTRO - INTRO_FADE) { super.render(c, mx, my, delta); drawIntro(c, ie); return; }
        }
        super.render(c, mx, my, delta);
        float intro = easeOut((System.currentTimeMillis() - openedAt) / 600f);
        topBar(c, mx, my, intro);
        // new tab slides in from the side it was clicked on and fades in
        float tp = easeOut((System.currentTimeMillis() - switchedAt) / 340f);
        float off = switchDir * (1 - tp) * 36;
        if (skin != null && (tab == Tab.HOME || tab == Tab.LOCKER || tab == Tab.ACCOUNTS)) { int ox = Math.round(off); skin.x += ox; skin.render(c); skin.x -= ox; }
        boolean anim = tp < 1;
        if (anim) { Render2D.fade = 0.1f + 0.9f * tp; Compat.push(c); Compat.translate(c, off, 0); }
        try {
            switch (tab) {
                case HOME -> home(c, mx, my, intro);
                case LOCKER -> locker(c, mx, my);
                case SHOTS -> screenshots(c, mx, my);
                case ACCOUNTS -> accountsTab(c, mx, my);
                case FRIENDS -> friendsTab(c, mx, my);
            }
        } finally {
            if (anim) { Compat.pop(c); Render2D.fade = 1; }
        }
        if (!status.isEmpty() && System.currentTimeMillis() - statusAt < 5000) {
            int w = width(status) + 20;
            round(c, width / 2f - w / 2f, height - 30, w, 16, 8, 0xE0120E24);
            centered(c, status, width / 2f, height - 26, statusColor, false);
        }
        if (!ingame) appearanceButton(c, mx, my);
        if (appearanceOpen) appearancePanel(c, mx, my);
        if (ie >= 0) drawIntro(c, ie);
        String ver = FabricLoader.getInstance().getModContainer("minecraft").map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("");
        if (!ingame && tab != Tab.FRIENDS && !(tab == Tab.HOME && height < 320)) text(c, "Orbit 1.3  ·  Minecraft " + ver, 8, height - 16, withAlpha(0xFFFFFF, 110), false);
    }

    private float hov(String key, boolean on) {
        float v = approach(hover.getOrDefault(key, 0f), on ? 1 : 0, 14, frameDt);
        hover.put(key, v);
        return v;
    }

    private void topBar(DrawContext c, int mx, int my, float intro) {
        logo(c, 18, 19, 5f);
        bold(c, "Orbit", 30, 15, alpha(0xFFFFFFFF, intro), false);
        int[] xs = tabXs();
        List<Tab> vis = tabs();
        Tab last = vis.get(vis.size() - 1);
        int barX = xs[vis.get(0).ordinal()] - 3, barW = xs[last.ordinal()] + tabW(last) + 3 - barX;
        round(c, barX, 8, barW, 22, 11, 0x26FFFFFF);
        // the white pill glides to the active tab (and stretches to its width)
        float gx = xs[tab.ordinal()], gw = tabW(tab);
        if (gliderX < 0) { gliderX = gx; gliderW = gw; }
        gliderX = approach(gliderX, gx, 16, frameDt);
        gliderW = approach(gliderW, gw, 16, frameDt);
        round(c, gliderX, 11, gliderW, 16, 8, 0xF2FFFFFF);
        for (Tab t : vis) {
            int x = xs[t.ordinal()], w = tabW(t);
            boolean active = t == tab, h = in(mx, my, x, 8, w, 22);
            float hv = hov("tabh:" + t, h && !active);
            // how much of the pill is under this label: the text turns dark exactly as the pill arrives
            float cover = Math.max(0, Math.min(1, (Math.min(gliderX + gliderW, x + w) - Math.max(gliderX, x)) / (float) w));
            if (hv > 0.01f) round(c, x, 11, w, 16, 8, withAlpha(0xFFFFFF, (int) (28 * hv * (1 - cover))));
            centered(c, title(t), x + w / 2f, 15, lerpColor(lerpColor(MUTED, TEXT, hv), 0xFF140A28, cover), false);
            if (t == Tab.FRIENDS && dev.orbit.client.friends.Friends.unreadTotal() > 0) disc(c, x + w - 5, 13, 3, 0xFFE5484D);
        }
        String cl = "Classic";
        int cw = width(cl) + 16, cx = width - cw - 8;
        float chv = hov("classic", in(mx, my, cx, 9, cw, 20));
        round(c, cx, 9, cw, 20, 10, lerpColor(0x1AFFFFFF, 0x36FFFFFF, chv));
        text(c, cl, cx + 8, 15, lerpColor(MUTED, TEXT, chv), false);
    }
    private int tabW(Tab t) { return width(title(t)) + (width < 470 ? 12 : 18); }
    private int[] tabXs() {
        int total = 0;
        for (Tab t : tabs()) total += tabW(t) + 2;
        // centred, but never under the logo or the Classic button
        int left = 64, right = width - (width("Classic") + 16) - 14 - (ingame ? 0 : 26);
        int x = Math.max(left, Math.min(width / 2 - total / 2, right - total));
        int[] xs = new int[Tab.values().length];
        for (Tab t : tabs()) { xs[t.ordinal()] = x; x += tabW(t) + 2; }
        return xs;
    }

    // ------------------------------------------------------------------ launch intro
    private static float clamp01(float v) { return Math.max(0, Math.min(1, v)); }
    private static float easeOutBack(float t) { t = clamp01(t); float c1 = 1.70158f, c3 = c1 + 1; return 1 + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2); }

    private void drawIntro(DrawContext c, float e) {
        float out = clamp01((e - (INTRO - INTRO_FADE)) / INTRO_FADE);   // 0..1 while fading to the menu
        float a = 1 - easeOut(out);
        int cx = width / 2, cy = height / 2 - 10, acc = accent();
        c.fill(0, 0, width, height, withAlpha(0x05030D, (int) (255 * a)));
        if (a <= 0.01f) return;
        float zoom = 1 + out * 0.25f;
        // shock ring that runs out from the planet
        float rp = clamp01((e - 0.3f) / 1.1f);
        if (rp > 0 && rp < 1) {
            float rr = easeOut(rp) * Math.max(width, height) * 0.55f;
            int dots = 120;
            for (int i = 0; i < dots; i++) {
                double t = i * Math.PI * 2 / dots;
                disc(c, cx + (float) Math.cos(t) * rr, cy - 14 + (float) Math.sin(t) * rr * 0.92f, 1.1f, withAlpha(acc, (int) (200 * (1 - rp) * a)));
            }
        }
        // planet pops in with a glow
        float pop = easeOutBack(e / 0.65f);
        if (e > 0.3f && !introSound) { introSound = true; Sounds.success(); }
        float r = 16 * pop * zoom;
        float pulse = 0.5f + 0.5f * (float) Math.sin(e * 5);
        if (r > 0.5f) {
            for (int g = 6; g >= 1; g--) // soft glow: many faint layers
                disc(c, cx, cy - 14 * zoom, r * (1.1f + g * (0.28f + 0.03f * pulse)), withAlpha(acc, (int) (9 * a)));
            Render2D.fade = a;
            logo(c, cx, cy - 14 * zoom, r);
            Render2D.fade = 1;
        }
        // ORBIT, letter by letter
        String name = "ORBIT";
        float k = Render2D.pixelScale(3.2f) * zoom;
        float gap = 3 * zoom, total = gap * (name.length() - 1);
        for (int i = 0; i < name.length(); i++) total += boldWidth(String.valueOf(name.charAt(i))) * k;
        float x = cx - total / 2f, ty = cy + 14 * zoom;
        for (int i = 0; i < name.length(); i++) {
            String ch = String.valueOf(name.charAt(i));
            float lp = easeOut(clamp01((e - 0.65f - i * 0.09f) / 0.35f));
            if (lp > 0) {
                Compat.push(c);
                Compat.translate(c, Render2D.snap(x), Render2D.snap(ty + (1 - lp) * 10));
                Compat.scale(c, k);
                bold(c, ch, 0, 0, withAlpha(0xFFFFFF, (int) (255 * lp * a)), false);
                Compat.pop(c);
            }
            x += boldWidth(ch) * k + gap;
        }
        float sub = easeOut(clamp01((e - 1.3f) / 0.45f));
        centered(c, "C L I E N T", cx, ty + 9 * k + 6, withAlpha(lerpColor(0xFFFFFF, acc, 0.4f), (int) (220 * sub * a)), false);
        // thin loading line
        float lw = 90 * zoom, lineP = easeOut(clamp01((e - 0.4f) / (INTRO - INTRO_FADE - 0.4f)));
        round(c, cx - lw / 2, ty + 9 * k + 22, lw, 2, 1, withAlpha(0xFFFFFF, (int) (30 * a)));
        if (lineP > 0.02f) round(c, cx - lw / 2, ty + 9 * k + 22, lw * lineP, 2, 1, withAlpha(acc, (int) (230 * a)));
    }
    private boolean skipIntro() {
        if (introPlayed || ingame || introStart < 0) return false;
        introPlayed = true;
        openedAt = System.currentTimeMillis();
        return true;
    }

    // ------------------------------------------------------------------ home

    // ------------------------------------------------------------------ appearance (main menu look)
    private boolean appearanceOpen;
    public void testAppearance(boolean open) { appearanceOpen = open; }
    private int apW() { return Math.min(330, width - 30); }
    private int apX() { return (width - apW()) / 2; }
    private int apH() { List<int[]> ch = accentChipsAt(0); return ch.get(ch.size() - 1)[1] + 18 + 34 + 22; }
    private List<int[]> accentChips() { return accentChipsAt(apY()); }
    /** {x, y, w} for each accent chip (panel top = y0), wrapping onto a new row when needed */
    private List<int[]> accentChipsAt(int y0) {
        List<int[]> out = new ArrayList<>();
        int x0 = apX() + 14, maxX = apX() + apW() - 14, ay = y0 + 52 + 2 * 46 + 4, cx = x0, cy = ay + 12;
        for (String n : dev.orbit.client.module.FeatureModules.Interface.ACCENTS) {
            int cw = width(n) + 22;
            if (cx + cw > maxX && cx > x0) { cx = x0; cy += 22; }
            out.add(new int[]{cx, cy, cw});
            cx += cw + 4;
        }
        return out;
    }
    private int apY() { return Math.max(34, (height - apH()) / 2); }
    /** a round palette button in the top bar, left of "Classic" */
    private int apBtnX() { return width - (width("Classic") + 16) - 8 - 6 - 20; }
    private void appearanceButton(DrawContext c, int mx, int my) {
        int x = apBtnX(), y = 9;
        float h = hov("apbtn", in(mx, my, x, y, 20, 20) || appearanceOpen);
        round(c, x, y, 20, 20, 10, lerpColor(0x1AFFFFFF, withAlpha(accent(), 190), h));
        for (int i = 0; i < 3; i++) { double a = -Math.PI / 2 + i * Math.PI * 2 / 3; disc(c, x + 10 + (float) Math.cos(a) * 4, y + 10 + (float) Math.sin(a) * 4, 2.2f, new int[]{accent(), accent2(), 0xFFFFFFFF}[i]); }
        if (h > 0.5f && !appearanceOpen) { int tw = width("Appearance") + 10; round(c, x + 10 - tw / 2f, y + 24, tw, 13, 6, 0xE0120E24); centered(c, "Appearance", x + 10, y + 27, TEXT, false); }
    }
    private void appearancePanel(DrawContext c, int mx, int my) {
        var ui = OrbitClient.modules().ui();
        c.fill(0, 0, width, height, 0x70050310);
        int x = apX(), y = apY(), w = apW(), h = apH();
        round(c, x - 1, y - 1, w + 2, h + 2, 15, withAlpha(accent(), 90));
        round(c, x, y, w, h, 14, 0xF2100C1E);
        bold(c, "Appearance", x + 14, y + 12, 0xFFFFFFFF, false);
        text(c, "How Orbit's menus look", x + 14, y + 24, MUTED, false);
        text(c, "×", x + w - 16, y + 11, in(mx, my, x + w - 22, y + 6, 18, 18) ? 0xFFFFFFFF : MUTED, false);
        // backgrounds
        text(c, "Background", x + 14, y + 40, FAINT, false);
        int cols = 5, sw = (w - 28 - (cols - 1) * 6) / cols;
        for (int i = 0; i < SpaceBackground.NAMES.length; i++) {
            int sx = x + 14 + (i % cols) * (sw + 6), sy = y + 52 + (i / cols) * 46;
            int[] p = SpaceBackground.paletteOf(i);
            boolean sel = ui.menuSky.is(SpaceBackground.NAMES[i]), hv = in(mx, my, sx, sy, sw, 42);
            if (sel) round(c, sx - 2, sy - 2, sw + 4, 32, 9, accent());
            round(c, sx, sy, sw, 28, 7, p[1]);
            c.fillGradient(sx + 3, sy + 3, sx + sw - 3, sy + 25, withAlpha(p[2], 120), withAlpha(p[3], 60));
            disc(c, sx + sw - 9, sy + 18, 5, p[5]);
            disc(c, sx + sw - 9.5f, sy + 18.5f, 4.2f, lerpColor(p[5], p[6], 0.55f));
            for (int k = 0; k < 4; k++) disc(c, sx + 5 + k * 6, sy + 6 + (k % 2) * 5, 0.7f, 0xCCFFFFFF);
            centered(c, fit(SpaceBackground.NAMES[i], sw, false), sx + sw / 2f, sy + 32, sel ? 0xFFFFFFFF : hv ? TEXT : MUTED, false);
        }
        // accent colour
        int ay = y + 52 + 2 * 46 + 4;
        text(c, "Accent colour", x + 14, ay, FAINT, false);
        List<int[]> chips = accentChips();
        for (int i = 0; i < chips.size(); i++) {
            String n = dev.orbit.client.module.FeatureModules.Interface.ACCENTS[i];
            int[] r = chips.get(i);
            boolean sel = ui.theme.is(n), hv = in(mx, my, r[0], r[1], r[2], 18);
            round(c, r[0], r[1], r[2], 18, 9, sel ? 0xF2FFFFFF : hv ? 0x30FFFFFF : 0x16FFFFFF);
            disc(c, r[0] + 9, r[1] + 9, 3.5f, 0xFF000000 | dev.orbit.client.module.FeatureModules.Interface.accentOf(i));
            text(c, n, r[0] + 16, r[1] + 5, sel ? 0xFF140A28 : TEXT, false);
        }
        // switches
        int ty = chips.get(chips.size() - 1)[1] + 26;
        String[] labels = {"Planet", "Hills", "Spin player", "Particles"};
        boolean[] on = {ui.menuPlanet.on(), ui.menuHills.on(), ui.menuSpin.on(), ui.menuParticles.on()};
        int tx = x + 14;
        for (int i = 0; i < 4; i++) {
            if (tx + width(labels[i]) + 34 > x + w - 10) { tx = x + 14; ty += 22; }
            int tw = width(labels[i]) + 34;
            boolean hv = in(mx, my, tx, ty, tw, 18);
            round(c, tx, ty, tw, 18, 9, on[i] ? withAlpha(accent(), hv ? 220 : 160) : hv ? 0x30FFFFFF : 0x16FFFFFF);
            text(c, labels[i], tx + 9, ty + 5, 0xFFFFFFFF, false);
            round(c, tx + tw - 21, ty + 5, 14, 8, 4, on[i] ? 0xFFFFFFFF : 0x40FFFFFF);
            disc(c, on[i] ? tx + tw - 11 : tx + tw - 17, ty + 9, 3, on[i] ? accent() : 0xFFB0A8CC);
            tx += tw + 6;
        }
    }
    private boolean clickAppearance(double mx, double my) {
        var ui = OrbitClient.modules().ui();
        int x = apX(), y = apY(), w = apW();
        if (!in(mx, my, x, y, w, apH()) || in(mx, my, x + w - 22, y + 6, 18, 18)) { appearanceOpen = false; Sounds.click(); return true; }
        int cols = 5, sw = (w - 28 - (cols - 1) * 6) / cols;
        for (int i = 0; i < SpaceBackground.NAMES.length; i++) {
            int sx = x + 14 + (i % cols) * (sw + 6), sy = y + 52 + (i / cols) * 46;
            if (in(mx, my, sx, sy, sw, 42)) { ui.menuSky.set(SpaceBackground.NAMES[i]); OrbitClient.modules().save(); Sounds.tab(); return true; }
        }
        List<int[]> chips = accentChips();
        for (int i = 0; i < chips.size(); i++) {
            int[] r = chips.get(i);
            if (in(mx, my, r[0], r[1], r[2], 18)) { ui.theme.set(dev.orbit.client.module.FeatureModules.Interface.ACCENTS[i]); OrbitClient.modules().save(); Sounds.tab(); return true; }
        }
        int ty = chips.get(chips.size() - 1)[1] + 26, tx = x + 14;
        String[] labels = {"Planet", "Hills", "Spin player", "Particles"};
        for (int i = 0; i < 4; i++) {
            int tw = width(labels[i]) + 34;
            if (tx + tw > x + w - 10) { tx = x + 14; ty += 22; }
            if (in(mx, my, tx, ty, tw, 18)) {
                var st = i == 0 ? ui.menuPlanet : i == 1 ? ui.menuHills : i == 2 ? ui.menuSpin : ui.menuParticles;
                st.set(!st.on()); OrbitClient.modules().save(); Sounds.toggle(st.on());
                if (i == 2 && skin != null) skin.autoSpin = st.on();
                return true;
            }
            tx += tw + 6;
        }
        return true;
    }
    // ------------------------------------------------------------------ pause menu (in a world / on a server)
    private static Item tileIcon(String label) {
        return switch (label) {
            case "Advancements" -> net.minecraft.item.Items.KNOWLEDGE_BOOK;
            case "Statistics" -> net.minecraft.item.Items.FILLED_MAP;
            case "Open to LAN" -> net.minecraft.item.Items.ENDER_PEARL;
            case "Orbit Mods" -> net.minecraft.item.Items.ENDER_EYE;
            case "Mod Menu" -> net.minecraft.item.Items.BOOKSHELF;
            case "Options" -> net.minecraft.item.Items.COMPARATOR;
            default -> net.minecraft.item.Items.PAPER;
        };
    }
    // pause menu = a slim glass panel on the left; the game (and your player) stay visible on the right
    private int pX() { return 12; }
    private int pW() { return Math.max(150, Math.min(196, Math.round(width * 0.26f))); }
    private int pTop() { return pTight() ? 36 : 40; }
    private int pBottom() { return height - (pTight() ? 8 : 12); }
    /** rows: 0 = Back to game, then the other buttons, then Options */
    private int pRows() { return buttons.size() + 1; }
    private String pLabel(int i) { return i < buttons.size() ? buttons.get(i).label() : "Options"; }
    /** small window: one-line header and slimmer rows */
    private boolean pTight() { return height - 52 < 66 + pRows() * 25 + 40; }
    private int pRowH() { return pTight() ? 17 : 21; }
    private int pRowsTop() { return pTop() + (pTight() ? 30 : 60); }
    private int pRowY(int i) { return pRowsTop() + i * (pRowH() + (pTight() ? 2 : 4)) + (i > 0 ? (pTight() ? 3 : 7) : 0); }
    private int pQuitY() {
        int afterRows = pRowY(pRows() - 1) + pRowH() + (pTight() ? 6 : 10);
        return Math.min(Math.max(afterRows, pBottom() - 10 - pRowH()), pBottom() - 6 - pRowH());
    }

    private void gameHome(DrawContext c, int mx, int my, float intro) {
        long since = System.currentTimeMillis() - openedAt;
        float slide = easeOut(since / 420f);
        int acc = accent();
        int x = pX() + Math.round(-(1 - slide) * 22), w = pW(), top = pTop(), bot = pBottom();
        // glass panel (two layers = a soft edge)
        round(c, x - 1, top - 1, w + 2, bot - top + 2, 15, withAlpha(0xFFFFFF, (int) (14 * slide)));
        round(c, x, top, w, bot - top, 14, withAlpha(0x0B0818, (int) (205 * slide)));

        // header: title + where you are
        int hx = x + 14, hy = top + (pTight() ? 10 : 13);
        String where = client.isInSingleplayer() ? "Singleplayer" : client.getCurrentServerEntry() != null ? client.getCurrentServerEntry().address : "Multiplayer";
        if (pTight()) { // one line: "Paused  • server"
            bold(c, "Paused", hx, hy, alpha(0xFFFFFFFF, slide), false);
            int wx = hx + boldWidth("Paused") + 8;
            disc(c, wx + 2.5f, hy + 4.5f, 2.2f, withAlpha(0x4ADE80, (int) (255 * slide)));
            text(c, fit(where, x + w - 14 - wx - 9, false), wx + 9, hy + 1, alpha(MUTED, slide), false);
        } else {
            bold(c, "Game paused", hx, hy, alpha(0xFFFFFFFF, slide), false);
            disc(c, hx + 2.5f, hy + 18.5f, 2.2f, withAlpha(0x4ADE80, (int) (255 * slide)));
            text(c, fit(where, w - 36, false), hx + 9, hy + 15, alpha(TEXT, slide), false);
        }
        if (!pTight() && client.getNetworkHandler() != null && !client.isInSingleplayer()) {
            String info = client.getNetworkHandler().getPlayerList().size() + " online";
            var me = client.player == null ? null : client.getNetworkHandler().getPlayerListEntry(client.player.getUuid());
            if (me != null) info += "  ·  " + me.getLatency() + " ms";
            text(c, fit(info, w - 36, false), hx + 9, hy + 26, alpha(FAINT, slide), false);
        }
        c.fill(hx, pRowsTop() - (pTight() ? 6 : 9), x + w - 14, pRowsTop() - (pTight() ? 5 : 8), withAlpha(0xFFFFFF, (int) (20 * slide)));

        // rows
        int rh = pRowH(), rw = w - 16;
        for (int i = 0; i < pRows(); i++) {
            float appear = easeOut((since - 120 - i * 35) / 300f);
            if (appear <= 0) continue;
            int ry = pRowY(i), rx = x + 8 + Math.round(-(1 - appear) * 10);
            float h = hov("pr" + i, in(mx, my, x + 8, ry, rw, rh));
            String label = pLabel(i);
            int ty = ry + (rh - 8) / 2 + 1;
            if (i == 0) { // Back to game: the one accent button
                round(c, rx, ry, rw, rh, rh / 2, withAlpha(lerpColor(acc, 0xFFFFFF, 0.18f * h), (int) (235 * appear)));
                centered(c, label, rx + rw / 2f, ty, alpha(0xFF0E0A1C, appear), false);
                continue;
            }
            round(c, rx, ry, rw, rh, 8, withAlpha(0xFFFFFF, (int) ((8 + 22 * h) * appear)));
            if (h > 0.01f) round(c, rx + 2, ry + 4, 2, rh - 8, 1, withAlpha(acc, (int) (255 * h * appear)));
            item(c, tileIcon(label), rx + 7, ry + (rh - 16) / 2, 16);
            text(c, fit(label, rw - 34, false), rx + 28 + h * 2, ty, alpha(lerpColor(MUTED, 0xFFFFFFFF, h), appear), false);
        }
        // leave, pinned to the bottom of the panel
        float qa = easeOut((since - 120 - pRows() * 35) / 300f);
        if (qa > 0) {
            int qy = pQuitY(), qx = x + 8;
            String quit = client.isInSingleplayer() ? "Save & Quit" : "Disconnect";
            float qh = hov("quitg", in(mx, my, qx, qy, rw, rh));
            round(c, qx, qy, rw, rh, rh / 2, withAlpha(0xE5484D, (int) ((40 + 180 * qh) * qa)));
            centered(c, quit, qx + rw / 2f, qy + (rh - 8) / 2 + 1, alpha(lerpColor(0xFFFF9C9C, 0xFFFFFFFF, qh), qa), false);
        }
        // your character in the free space on the right
        if (skin != null) {
            String user = client.getSession().getUsername();
            int sx = skinCenterX(), sy = skin.y + skin.h + 6;
            centered(c, user, sx, sy, 0xFFFFFFFF, true);
            centered(c, "Drag to spin", sx, sy + 11, FAINT, false);
            btn(c, "locker", sx - 40, sy + 24, 80, 18, "Locker", mx, my, 2, false);
        }
    }

    private boolean clickGameHome(double mx, double my) {
        int rw = pW() - 16, rh = pRowH(), rx = pX() + 8;
        for (int i = 0; i < pRows(); i++) {
            if (!in(mx, my, rx, pRowY(i), rw, rh)) continue;
            Sounds.click();
            if (i < buttons.size()) buttons.get(i).action().run();
            else client.setScreen(new OptionsScreen(this, client.options));
            return true;
        }
        if (in(mx, my, rx, pQuitY(), rw, rh)) {
            Sounds.click();
            client.getAbuseReportContext().tryShowDraftScreen(client, this, () -> client.disconnect(net.minecraft.client.world.ClientWorld.QUITTING_MULTIPLAYER_TEXT), true);
            return true;
        }
        if (skin != null) {
            int sx = skinCenterX(), sy = skin.y + skin.h + 6;
            if (in(mx, my, sx - 40, sy + 24, 80, 18)) { switchTab(Tab.LOCKER); return true; }
        }
        return false;
    }
    // ------------------------------------------------------------------ main menu, in the launcher's "Clean" style
    private int hColX() { return Math.max(28, Math.round(width * 0.08f)); }
    private int hColW() { return Math.max(170, Math.min(250, Math.round(width * 0.40f))); }
    /** letter-spaced small caps, like the launcher's eyebrow labels */
    private void spaced(DrawContext c, String s, float x, float y, int col) {
        for (int i = 0; i < s.length(); i++) { String ch = s.substring(i, i + 1); text(c, ch, x, y, col, false); x += width(ch) + 1.2f; }
    }
    private int buttonIndex(String label) { for (int i = 0; i < buttons.size(); i++) if (buttons.get(i).label().equals(label)) return i; return -1; }

    private void homeClean(DrawContext c, int mx, int my) {
        hits.clear();
        long since = System.currentTimeMillis() - openedAt;
        int acc = accent(), x = hColX(), w = hColW();
        String user = client.getSession().getUsername();
        float k = Render2D.pixelScale(2.0f);
        String head1 = "Ready for ", head2 = "liftoff", head3 = "?";
        float headW = boldWidth(head1 + head2 + head3) * k;
        if (headW > w) k *= w / headW;
        boolean compact = height < 320;
        List<String> sub = compact ? List.of() : wrap("Pick a world or a server — Orbit takes care of the rest.", w);
        int tw = (w - 24 - 8) / 2;
        boolean stacked = tw < 96; // narrow: the two tiles go under each other
        int ch = stacked ? 22 + 2 * 24 + 6 : 74;
        int total = 10 + (compact ? 14 : 18) - 10 + Math.round(10 * k) + 8 + sub.size() * 10 + (compact ? 8 : 16) + ch + 10 + 20;
        int y = Math.max(compact ? 40 : 46, (height - total) / 2 + (compact ? 0 : 8));

        // eyebrow
        float a1 = easeOut((since - 60) / 420f);
        int hour = java.time.LocalTime.now().getHour();
        String greet = (hour < 5 ? "GOOD NIGHT" : hour < 12 ? "GOOD MORNING" : hour < 18 ? "GOOD AFTERNOON" : "GOOD EVENING") + ", " + user.toUpperCase();
        spaced(c, fit(greet, w - greet.length(), false), x, y + (1 - a1) * 6, alpha(acc, a1));
        y += compact ? 14 : 18;
        // headline
        float a2 = easeOut((since - 120) / 420f);
        Compat.push(c);
        Compat.translate(c, Render2D.snap(x), Render2D.snap(y + (1 - a2) * 8));
        Compat.scale(c, k);
        bold(c, head1, 0, 0, alpha(0xFFFFFFFF, a2), false);
        bold(c, head2, boldWidth(head1), 0, alpha(acc, a2), false);
        bold(c, head3, boldWidth(head1 + head2), 0, alpha(0xFFFFFFFF, a2), false);
        Compat.pop(c);
        y += Math.round(10 * k) + 8;
        // sub line
        float a3 = easeOut((since - 180) / 420f);
        for (String s : sub) { text(c, s, x, y + (1 - a3) * 6, alpha(MUTED, a3), false); y += 10; }
        y += compact ? 8 : 16;

        // play card: two big tiles (side by side, or stacked when narrow)
        float a4 = easeOut((since - 240) / 460f);
        int cy = y + Math.round((1 - a4) * 10);
        round(c, x - 1, cy - 1, w + 2, ch + 2, 15, withAlpha(0xFFFFFF, (int) (16 * a4)));
        round(c, x, cy, w, ch, 14, withAlpha(0x0B0818, (int) (190 * a4)));
        spaced(c, "PLAY", x + 12, cy + 9, alpha(FAINT, a4));
        int th = stacked ? 22 : 42, tW = stacked ? w - 24 : tw;
        String[][] tiles = {{"Singleplayer", "Your worlds"}, {"Multiplayer", "Servers & friends"}};
        for (int i = 0; i < 2; i++) {
            int tx = stacked ? x + 12 : x + 12 + i * (tw + 8), ty = stacked ? cy + 22 + i * 24 : cy + 22;
            int bi = buttonIndex(tiles[i][0]);
            float hv = hov("ht" + i, in(mx, my, tx, ty, tW, th));
            boolean primary = i == 0;
            int base = primary ? withAlpha(acc, (int) (220 * a4)) : withAlpha(0xFFFFFF, (int) (20 * a4));
            int hot = primary ? withAlpha(lerpColor(acc, 0xFFFFFF, 0.2f), (int) (255 * a4)) : withAlpha(acc, (int) (90 * a4));
            round(c, tx, ty, tW, th, stacked ? 11 : 11, lerpColor(base, hot, hv));
            int tcol = primary ? 0xFF0E0A1C : 0xFFFFFFFF;
            item(c, i == 0 ? net.minecraft.item.Items.GRASS_BLOCK : net.minecraft.item.Items.COMPASS, tx + (stacked ? 6 : 10), ty + (th - 16) / 2, 16);
            if (stacked) {
                bold(c, fit(tiles[i][0], tW - 30, true), tx + 28, ty + 7, alpha(tcol, a4), false);
            } else {
                bold(c, fit(tiles[i][0], tW - 40, true), tx + 32, ty + 11, alpha(tcol, a4), false);
                text(c, fit(tiles[i][1], tW - 40, false), tx + 32, ty + 23, alpha(primary ? withAlpha(0x0E0A1C, 170) : FAINT, a4), false);
            }
            if (bi >= 0) hit(tx, ty, tW, th, () -> { Sounds.click(); buttons.get(bi).action().run(); });
        }
        y += ch + 10;

        // secondary actions: slim glass pills with icons
        List<String> sec = new ArrayList<>();
        if (buttonIndex("Orbit Mods") >= 0) sec.add("Orbit Mods");
        if (buttonIndex("Mod Menu") >= 0) sec.add("Mod Menu");
        sec.add("Options");
        sec.add("Quit");
        // widths follow the labels; icons only when everything fits with them
        int gap = 6, free = w - gap * (sec.size() - 1);
        boolean icons = true;
        int[] ws = new int[sec.size()];
        for (int pass = 0; pass < 2; pass++) {
            int sum = 0;
            for (int i = 0; i < ws.length; i++) { ws[i] = width(sec.get(i)) + 16 + (icons && !sec.get(i).equals("Quit") ? 20 : 0); sum += ws[i]; }
            if (sum <= free) { int extra = (free - sum) / ws.length; for (int i = 0; i < ws.length; i++) ws[i] += extra; break; }
            if (pass == 0) { icons = false; continue; }
            for (int i = 0; i < ws.length; i++) ws[i] = free / ws.length; // still too wide: equal, labels get shortened
        }
        int px = x;
        for (int i = 0; i < sec.size(); i++) {
            String label = sec.get(i);
            int pw = ws[i];
            float ap = easeOut((since - 320 - i * 50) / 380f);
            int py = y + Math.round((1 - ap) * 6);
            boolean quit = label.equals("Quit");
            float hv = hov("hs" + label, in(mx, my, px, py, pw, 20));
            round(c, px, py, pw, 20, 10, lerpColor(withAlpha(0xFFFFFF, (int) (20 * ap)), quit ? withAlpha(0xE5484D, (int) (200 * ap)) : withAlpha(0xFFFFFF, (int) (52 * ap)), hv));
            boolean icon = icons && !quit;
            if (icon) item(c, tileIcon(label), px + 4, py + 2, 16);
            float lx = icon ? px + 22 + (pw - 24 - width(fit(label, pw - 26, false))) / 2f : px + (pw - width(fit(label, pw - 8, false))) / 2f;
            text(c, fit(label, icon ? pw - 26 : pw - 8, false), lx, py + 6, alpha(lerpColor(MUTED, 0xFFFFFFFF, hv), ap), false);
            Runnable r = switch (label) {
                case "Options" -> () -> { Sounds.click(); client.setScreen(new OptionsScreen(this, client.options)); };
                case "Quit" -> () -> client.scheduleStop();
                default -> () -> { Sounds.click(); buttons.get(buttonIndex(label)).action().run(); };
            };
            hit(px, py, pw, 20, r);
            px += pw + gap;
        }

        // you, on the right: name badge above (like the launcher), account + Locker below
        if (skin != null) {
            int sx = skinCenterX();
            float a5 = easeOut((since - 200) / 500f);
            int nw = width(user) + 30, ny = skin.y - 14;
            round(c, sx - nw / 2f, ny, nw, 18, 9, withAlpha(0x0B0818, (int) (190 * a5)));
            logo(c, sx - nw / 2f + 10, ny + 9, 3.2f);
            text(c, user, sx - nw / 2f + 19, ny + 5, alpha(0xFFFFFFFF, a5), false);
            int by0 = skin.y + skin.h + 6;
            centered(c, offline() ? "Offline account" : "Microsoft account", sx, by0, alpha(FAINT, a5), false);
            float lh = hov("locker", in(mx, my, sx - 40, by0 + 14, 80, 18));
            round(c, sx - 40, by0 + 14, 80, 18, 9, lerpColor(withAlpha(0xFFFFFF, (int) (22 * a5)), withAlpha(acc, 200), lh));
            centered(c, "Locker", sx, by0 + 19, alpha(lerpColor(TEXT, 0xFF0E0A1C, lh), a5), false);
            hit(sx - 40, by0 + 14, 80, 18, () -> switchTab(Tab.LOCKER));
        }
    }

    private void home(DrawContext c, int mx, int my, float intro) {
        if (ingame) { gameHome(c, mx, my, intro); return; }
        homeClean(c, mx, my);
    }
    private boolean offline() { String t = client.getSession().getAccessToken(); return t == null || t.length() < 20; }

    private void btn(DrawContext c, String id, int x, int y, int w, int h, String label, int mx, int my, int order, boolean primary) {
        float appear = easeOut(((System.currentTimeMillis() - openedAt) - 100 - order * 50) / 380f);
        if (appear <= 0) return;
        y += Math.round((1 - appear) * 8);
        float hv = hov(id, in(mx, my, x, y, w, h));
        SpaceBackground.button(c, x, y, w, h, label, mx, my, hv, primary);
    }

    // ------------------------------------------------------------------ locker
    private int panelX() { return 24; }
    private int panelW() { return skin != null ? Math.round(width * 0.62f) - 24 : width - 48; }

    private void locker(DrawContext c, int mx, int my) {
        int px = panelX(), pw = panelW(), y = 44;
        display(c, "Locker", px, y, 0xFFFFFFFF, false);
        text(c, "Skins, capes, cosmetics and emotes", px, y + 12, MUTED, false);
        int sx = px;
        for (Sub s : Sub.values()) {
            int w = width(s.title) + 18;
            boolean a = s == sub, h = in(mx, my, sx, y + 28, w, 18);
            round(c, sx, y + 28, w, 18, 9, a ? 0xF2FFFFFF : h ? 0x30FFFFFF : 0x1AFFFFFF);
            text(c, s.title, sx + 9, y + 33, a ? 0xFF140A28 : TEXT, false);
            sx += w + 4;
        }
        int top = y + 54;
        scroll = approach(scroll, scrollTarget, 16, 1 / 60f);
        switch (sub) {
            case SKINS -> lockerSkins(c, mx, my, px, top, pw);
            case CAPES -> lockerCapes(c, mx, my, px, top, pw);
            case COSMETICS -> lockerCosmetics(c, mx, my, px, top, pw);
            case EMOTES -> lockerEmotes(c, mx, my, px, top, pw);
        }
        if (previewName != null && previewSkin == null) { // username preview: skin arrives async
            SkinTextures t = NameSkins.get(previewName);
            if (t != null) previewSkin = t;
        }
        if (skin != null) {
            int scx = skinCenterX(), sy = skin.y + skin.h + 6;
            if (previewApply != null) {
                centered(c, previewLabel, scx, sy, 0xFFFFFFFF, true);
                centered(c, previewName != null && previewSkin == null ? "Loading skin…" : "Preview", scx, sy + 11, FAINT, false);
                btn(c, "papply", scx - 76, sy + 24, 74, 18, sub == Sub.CAPES ? "Equip" : "Wear", mx, my, 0, true);
                btn(c, "pcancel", scx + 2, sy + 24, 74, 18, "Cancel", mx, my, 0, false);
            } else {
                centered(c, client.getSession().getUsername(), scx, sy, 0xFFFFFFFF, true);
                centered(c, "Drag to spin", scx, sy + 11, FAINT, false);
            }
        }
    }

    private void inputBox(DrawContext c, String id, String placeholder, int x, int y, int w, int mx, int my) {
        boolean focus = id.equals(inputFor);
        round(c, x, y, w, 20, 10, focus ? 0x33FFFFFF : 0x1AFFFFFF);
        boolean caret = focus && (System.currentTimeMillis() / 500) % 2 == 0;
        String v = focus ? input : "";
        if (v.isEmpty() && !focus) text(c, placeholder, x + 10, y + 6, FAINT, false);
        else text(c, fit(v + (caret ? "_" : ""), w - 20, false), x + 10, y + 6, TEXT, false);
    }

    /** Scrollable grid area of the locker (between the sub-tabs and the bottom). */
    private int gridTop(int top) { return top + (sub == Sub.SKINS ? 54 : sub == Sub.EMOTES ? 84 : 14); }
    private void clampScroll(int rows, int rowH, int top) {
        scrollTarget = Math.max(0, Math.min(Math.max(0, rows * rowH - (height - 24 - gridTop(top))), scrollTarget));
    }

    private void lockerSkins(DrawContext c, int mx, int my, int px, int top, int pw) {
        text(c, "Wear any player's skin — type their username:", px, top, MUTED, false);
        int iw = Math.min(180, pw - 90);
        inputBox(c, "skinname", "Username…", px, top + 12, iw, mx, my);
        btn(c, "getskin", px + iw + 6, top + 12, 80, 20, "Preview", mx, my, 0, true);
        text(c, "Your skin library — click to preview, × to delete:", px, top + 42, MUTED, false);
        if (skins == null) { loadSkins(); text(c, OrbitBridge.available() ? "Loading…" : "Start Minecraft from the Orbit launcher to see your skins", px, top + 58, FAINT, false); return; }
        if (skins.isEmpty()) { text(c, "No saved skins yet — preview one by username above.", px, top + 58, FAINT, false); return; }
        int cell = 64, gap = 6, cols = Math.max(1, (pw + gap) / (cell + gap)), gt = gridTop(top);
        c.enableScissor(0, gt - 2, width, height - 22);
        for (int i = 0; i < skins.size(); i++) {
            JsonObject s = skins.get(i);
            int x = px + (i % cols) * (cell + gap), y = gt + (i / cols) * (cell + gap) - Math.round(scroll);
            if (y > height || y + cell < gt) continue;
            boolean h = in(mx, my, x, y, cell, cell) && my > gt;
            boolean sel = previewApply != null && ("skin:" + str(s, "id")).equals(previewKey);
            round(c, x, y, cell, cell, 8, sel ? withAlpha(accent(), 110) : h ? 0x33FFFFFF : 0x1AFFFFFF);
            Identifier tex = Tex.fromDataUrl("skin:" + str(s, "id"), str(s, "data"), false);
            if (tex != null) face(c, tex, x + cell / 2f - 12, y + 8, 24);
            centered(c, fit(str(s, "name"), cell - 6, false), x + cell / 2f, y + cell - 14, TEXT, false);
            if (h) {
                String key = "delskin:" + str(s, "id");
                boolean conf = confirming(key);
                boolean xh = in(mx, my, x + cell - 16, y + 2, 14, 14);
                round(c, x + cell - 16, y + 2, 14, 14, 7, conf ? 0xFFE5484D : xh ? 0x66FFFFFF : 0x33FFFFFF);
                centered(c, conf ? "?" : "×", x + cell - 9, y + 5, 0xFFFFFFFF, false);
            }
        }
        c.disableScissor();
        clampScroll((skins.size() + cols - 1) / cols, cell + gap, top);
    }
    private String previewKey = "";
    private boolean draggingSkin, lastHoverBack;
    private void clearHover() { dev.orbit.client.cosmetics.CosmeticsFeature.previewHat = null; dev.orbit.client.cosmetics.CosmeticsFeature.previewWings = null; dev.orbit.client.cosmetics.CosmeticsFeature.previewAura = null; }
    @Override public void removed() { clearHover(); Emotes.PREVIEW.clear(); }
    private Supplier<SkinTextures> tileSupplier;
    private final java.util.Map<Integer, PlayerPreview> tiles = new java.util.HashMap<>();

    @Override protected boolean onDrag(double mx, double my, int button) { if (draggingSkin && skin != null) { skin.drag(mx); return true; } return false; }
    @Override protected boolean onRelease(double mx, double my, int button) { if (draggingSkin && skin != null) skin.release(); draggingSkin = false; return false; }

    /** Draws the face (+ hat layer) of a 64x64 skin texture. */
    private static void face(DrawContext c, Identifier tex, float x, float y, int size) {
        Compat.push(c); Compat.translate(c, x, y);
        Compat.blitTint(c, tex, 0, 0, size, size, 8, 8, 8, 8, 64, 64, 0xFFFFFFFF);
        Compat.blitTint(c, tex, 0, 0, size, size, 40, 8, 8, 8, 64, 64, 0xFFFFFFFF);
        Compat.pop(c);
    }

    // capes: official Minecraft capes on your account + Orbit capes
    private volatile List<JsonObject> mcCapes;
    private boolean loadingMc, mcOffline;
    private String mcError = "";
    private void loadMcCapes() {
        if (loadingMc || !OrbitBridge.available()) return;
        loadingMc = true;
        OrbitBridge.get("capes/mojang").whenComplete((r, e) -> client.execute(() -> {
            loadingMc = false;
            if (e != null || r == null || !r.isJsonObject()) { mcCapes = List.of(); mcError = e != null ? OrbitBridge.message(e) : "No answer from the launcher"; OrbitClient.LOG.warn("Minecraft capes: {}", mcError); return; }
            mcError = "";
            mcOffline = r.getAsJsonObject().has("offline") && r.getAsJsonObject().get("offline").getAsBoolean();
            mcCapes = objs(r.getAsJsonObject().get("capes"));
        }));
    }
    /** {kind (0 header MC, 1 MC cape, 2 MC none, 3 header Orbit, 4 Orbit cape, 5 Orbit none), index, x, y} */
    private static final int CW = 54, CH = 82, CGAP = 6;
    private List<int[]> capeLayout(int px, int pw, int gt) {
        List<int[]> out = new ArrayList<>();
        int cols = Math.max(1, (pw + CGAP) / (CW + CGAP)), y = gt;
        if (OrbitBridge.available()) { // Minecraft capes section is always there (loading / none / error shown inside)
            out.add(new int[]{0, 0, px, y}); y += 14;
            if (mcCapes == null || mcCapes.isEmpty()) { out.add(new int[]{6, 0, px, y}); y += 20; }
            else {
                for (int i = 0; i <= mcCapes.size(); i++) out.add(new int[]{i == mcCapes.size() ? 2 : 1, i, px + (i % cols) * (CW + CGAP), y + (i / cols) * (CH + CGAP)});
                y += ((mcCapes.size() + 1 + cols - 1) / cols) * (CH + CGAP) + 6;
            }
            out.add(new int[]{3, 0, px, y}); y += 14;
        }
        if (capes != null) for (int i = 0; i <= capes.size(); i++) out.add(new int[]{i == capes.size() ? 5 : 4, i, px + (i % cols) * (CW + CGAP), y + (i / cols) * (CH + CGAP)});
        return out;
    }

    private void lockerCapes(DrawContext c, int mx, int my, int px, int top, int pw) {
        text(c, "Click a cape to preview it on your player, then Equip.", px, top, MUTED, false);
        if (mcCapes == null) loadMcCapes();
        if (capes == null) { loadCapes(); text(c, OrbitBridge.available() ? "Loading…" : "Start Minecraft from the Orbit launcher to see your capes", px, top + 16, FAINT, false); return; }
        int gt = gridTop(top);
        List<int[]> layout = capeLayout(px, pw, gt);
        c.enableScissor(0, gt - 2, width, height - 22);
        boolean mcActive = mcCapes != null && mcCapes.stream().anyMatch(o -> o.has("active") && o.get("active").getAsBoolean());
        for (int[] it : layout) {
            int x = it[2], y = it[3] - Math.round(scroll);
            if (it[0] == 6) { // Minecraft capes: status line
                String msg = mcCapes == null ? "Loading your Minecraft capes…" : mcOffline ? "Offline account — Minecraft capes need a Microsoft account"
                        : !mcError.isEmpty() ? "Couldn't load Minecraft capes: " + mcError : "This account has no Minecraft capes";
                text(c, fit(msg, Math.max(60, width - x - 20), false), x, y + 4, mcError.isEmpty() ? FAINT : 0xFFFF9A9A, false);
                continue;
            }
            if (it[0] == 0 || it[0] == 3) {
                if (y < gt - 14 || y > height) continue;
                String hdr = it[0] == 0 ? "Minecraft capes" : "Orbit capes";
                bold(c, hdr, x, y + 1, 0xFFFFFFFF, false);
                text(c, it[0] == 0 ? "on your account · everyone sees them" : "Orbit players see these", x + boldWidth(hdr) + 6, y + 1, FAINT, false);
                continue;
            }
            if (y > height || y + CH < gt) continue;
            boolean mc = it[0] <= 2, none = it[0] == 2 || it[0] == 5;
            JsonObject cape = none ? null : (mc ? mcCapes : capes).get(it[1]);
            boolean h = in(mx, my, x, y, CW, CH) && my > gt;
            boolean equipped = mc ? (none ? !mcActive : cape.has("active") && cape.get("active").getAsBoolean())
                    : (none ? capes.stream().noneMatch(o -> o.has("selected") && o.get("selected").getAsBoolean()) : cape.has("selected") && cape.get("selected").getAsBoolean());
            String key = (mc ? "mccape:" : "cape:") + (none ? "" : str(cape, "id"));
            boolean previewing = !none && previewApply != null && key.equals(previewKey);
            float hv = hov("cape" + key + it[1], h);
            round(c, x, y, CW, CH, 8, previewing ? withAlpha(accent(), 140) : equipped ? withAlpha(accent(), 70) : lerpColor(0x1AFFFFFF, 0x33FFFFFF, hv));
            if (none) centered(c, "None", x + CW / 2f, y + CH / 2f - 8, MUTED, false);
            else {
                Identifier tex = Tex.fromDataUrl(key, str(cape, "data"), !mc);
                if (tex != null) {
                    if (mc) Compat.blitTint(c, tex, x + CW / 2 - 15, y + 6 - Math.round(hv * 2), 30, 48, 1, 1, 10, 16, 64, 32, 0xFFFFFFFF);          // 64x32 Minecraft cape
                    else Compat.blitTint(c, tex, x + CW / 2 - 15, y + 6 - Math.round(hv * 2), 30, 48, 8, 8, 80, 128, 512, 256, 0xFFFFFFFF);    // 512x256 Orbit cape
                }
            }
            centered(c, fit(none ? (mc ? "No MC cape" : "No cape") : str(cape, "name"), CW - 4, false), x + CW / 2f, y + CH - 14, equipped ? 0xFF8CF5B4 : TEXT, false);
        }
        c.disableScissor();
        int contentH = layout.isEmpty() ? 0 : layout.get(layout.size() - 1)[3] + CH - gt;
        clampScroll((contentH + CGAP + CH) / (CH + CGAP), CH + CGAP, top);
    }
    private void lockerCosmetics(DrawContext c, int mx, int my, int px, int top, int pw) {
        var cos = (dev.orbit.client.module.NewModules.Cosmetics) OrbitClient.modules().get("cosmetics");
        text(c, fit("Hover to preview, click to wear (F5 in-game).", pw, false), px, top, MUTED, false);
        int gt = gridTop(top), y = gt - Math.round(scroll);
        c.enableScissor(0, gt - 2, width, height - 22);
        String[][] groups = {dev.orbit.client.module.NewModules.Cosmetics.HATS, dev.orbit.client.module.NewModules.Cosmetics.WINGS, dev.orbit.client.module.NewModules.Cosmetics.AURAS};
        String[] titles = {"Hats", "Wings", "Auras"};
        String hovHat = null, hovWings = null, hovAura = null;
        for (int gi = 0; gi < 3; gi++) {
            text(c, titles[gi], px, y, TEXT, false);
            y += 12;
            var setting = gi == 0 ? cos.hat : gi == 1 ? cos.wings : cos.aura;
            int cell = 62, ch = 62, gap = 6, cols = Math.max(1, (pw + gap) / (cell + gap));
            for (int i = 0; i < groups[gi].length; i++) {
                String name = groups[gi][i];
                int x = px + (i % cols) * (cell + gap), yy = y + (i / cols) * (ch + gap);
                boolean sel = setting.is(name), h = in(mx, my, x, yy, cell, ch) && my > gt && my < height - 22;
                if (h) { if (gi == 0) hovHat = name; else if (gi == 1) hovWings = name; else hovAura = name; }
                round(c, x, yy, cell, ch, 10, sel ? withAlpha(accent(), 110) : h ? 0x33FFFFFF : 0x1AFFFFFF);
                if (yy + ch > gt && yy < height - 22) tileArt(c, gi, i, name, x + 4, yy + 3, cell - 8, ch - 18);
                centered(c, fit(name, cell - 4, false), x + cell / 2f, yy + ch - 12, sel ? 0xFFFFFFFF : TEXT, false);
            }
            y += ((groups[gi].length + cols - 1) / cols) * (ch + gap) + 8;
        }
        c.disableScissor();
        scrollTarget = Math.max(0, Math.min(Math.max(0, y + Math.round(scroll) - (height - 24)), scrollTarget));
        // hovering previews the cosmetic on the big model; wings turn it round
        dev.orbit.client.cosmetics.CosmeticsFeature.previewHat = hovHat;
        dev.orbit.client.cosmetics.CosmeticsFeature.previewWings = hovWings;
        dev.orbit.client.cosmetics.CosmeticsFeature.previewAura = hovAura;
        boolean back = hovWings != null && !hovWings.equals("None");
        if (skin != null && back != lastHoverBack) { skin.showBack(back); lastHoverBack = back; }
    }

    /** The picture on a cosmetics tile: a little 3D player wearing it (hats: head close-up, wings: from behind). */
    private void tileArt(DrawContext c, int group, int index, String name, int x, int y, int w, int h) {
        if (tileSupplier == null) return;
        int id = dev.orbit.client.cosmetics.CosmeticsFeature.PREVIEW_ID - 1 - group * 32 - index;
        dev.orbit.client.cosmetics.CosmeticsFeature.OVERRIDES.put(id, group == 0 ? new String[]{name, "None", "None"} : group == 1 ? new String[]{"None", name, "None"} : new String[]{"None", "None", name});
        PlayerPreview p = tiles.computeIfAbsent(id, k -> {
            PlayerPreview pp = new PlayerPreview(x, y, w, h, tileSupplier);
            pp.id = k;
            pp.animate = false;
            pp.expand = false;
            if (group == 0) { pp.focusY = 1.72f; pp.zoom = 2.0f; pp.setSpin(-28); }
            else if (group == 1) { pp.focusY = 1.25f; pp.zoom = 1.25f; pp.setSpin(155); }
            else { pp.focusY = 0.95f; pp.zoom = 0.95f; pp.setSpin(-20); pp.animate = true; } // auras: whole body
            return pp;
        });
        p.x = x; p.y = y; p.w = w; p.h = h;
        p.render(c);
    }

    // emotes: pick what goes on the round wheel (3 pages x 6), every emote shown as you doing it
    private static int wheelPage;
    private int chipW(int pw) { return Math.max(30, (pw - 5 * 4) / 6); }
    private void lockerEmotes(DrawContext c, int mx, int my, int px, int top, int pw) {
        text(c, fit("Your emote wheel (B in-game) — click an emote to put it on the page", pw, false), px, top, MUTED, false);
        int x = px;
        for (int p = 0; p < dev.orbit.client.emote.EmoteWheel.PAGES; p++) {
            String l = "Page " + (p + 1) + "  " + dev.orbit.client.emote.EmoteWheel.count(p) + "/6";
            int w = width(l) + 14;
            boolean a = p == wheelPage, h = in(mx, my, x, top + 13, w, 16);
            round(c, x, top + 13, w, 16, 8, a ? withAlpha(accent(), 230) : h ? 0x30FFFFFF : 0x1AFFFFFF);
            text(c, l, x + 7, top + 17, a ? 0xFF140A28 : TEXT, false);
            x += w + 4;
        }
        int cw = chipW(pw);
        for (int s = 0; s < dev.orbit.client.emote.EmoteWheel.SLOTS; s++) {
            Emote e = dev.orbit.client.emote.EmoteWheel.get(wheelPage, s);
            int cx = px + s * (cw + 4), cy = top + 34;
            boolean h = in(mx, my, cx, cy, cw, 22);
            round(c, cx, cy, cw, 22, 11, e == null ? 0x10FFFFFF : h ? withAlpha(0xFF6B6B, 70) : 0x24FFFFFF);
            if (e == null) centered(c, "+", cx + cw / 2f, cy + 7, FAINT, false);
            else centered(c, fit(h ? "× " + e.title : e.title, cw - 6, false), cx + cw / 2f, cy + 7, h ? 0xFFFFB4B4 : TEXT, false);
        }
        // category filter chips (right side of the page row)
        int chipX = px;
        text(c, "Show:", chipX, top + 64, FAINT, false);
        chipX += width("Show:") + 6;
        for (int ci = 0; ci < EMOTE_CATS.length; ci++) {
            int w = width(EMOTE_CATS[ci]) + 14;
            boolean a = ci == emoteCat, h = in(mx, my, chipX, top + 60, w, 16);
            round(c, chipX, top + 60, w, 16, 8, a ? 0xF2FFFFFF : h ? 0x30FFFFFF : 0x14FFFFFF);
            text(c, EMOTE_CATS[ci], chipX + 7, top + 64, a ? 0xFF140A28 : MUTED, false);
            chipX += w + 3;
        }
        int gt = gridTop(top), cell = EMOTE_CELL;
        Emote[] all = Emote.values();
        List<int[]> layout = emoteLayout(px, pw, gt);
        c.enableScissor(0, gt - 2, width, height - 22);
        for (int[] it : layout) {
            int ty = it[2] - Math.round(scroll), tx = it[1];
            if (it[0] < 0) { // section header
                if (ty < gt - 14 || ty > height) continue;
                bold(c, EMOTE_CATS[it[3]], tx, ty + 1, 0xFFFFFFFF, false);
                String n = String.valueOf(it[4]);
                text(c, n, tx + boldWidth(EMOTE_CATS[it[3]]) + 5, ty + 1, FAINT, false);
                round(c, tx + boldWidth(EMOTE_CATS[it[3]]) + width(n) + 11, ty + 5, pw - boldWidth(EMOTE_CATS[it[3]]) - width(n) - 11, 1, 1, 0x18FFFFFF);
                continue;
            }
            if (ty > height || ty + cell < gt) continue;
            Emote e = all[it[0]];
            int on = dev.orbit.client.emote.EmoteWheel.pageOf(e);
            boolean h = in(mx, my, tx, ty, cell, cell);
            float hv = hov("em" + it[0], h);
            round(c, tx, ty, cell, cell, 10, on == wheelPage ? withAlpha(accent(), 110) : lerpColor(0x1AFFFFFF, 0x2EFFFFFF, hv));
            if (on == wheelPage) round(c, tx + 1.5f, ty + 1.5f, cell - 3, cell - 3, 9, 0xE0141026);
            int id = dev.orbit.client.cosmetics.CosmeticsFeature.PREVIEW_ID - 800 - it[0];
            dev.orbit.client.cosmetics.CosmeticsFeature.OVERRIDES.put(id, new String[3]);
            Emotes.PREVIEW.put(id, e);
            if (tileSupplier != null) {
                PlayerPreview pv = tiles.computeIfAbsent(id, k -> { PlayerPreview pp = new PlayerPreview(0, 0, 1, 1, tileSupplier); pp.id = k; pp.animate = false; pp.expand = false; pp.setSpin(-18); return pp; });
                pv.x = tx + 5; pv.y = ty + 2 - Math.round(hv * 2); pv.w = cell - 10; pv.h = cell - 16;
                pv.render(c);
            }
            centered(c, fit(e.title, cell - 4, false), tx + cell / 2f, ty + cell - 11, TEXT, true);
            if (on >= 0) { String b = "P" + (on + 1); round(c, tx + cell - width(b) - 8, ty + 3, width(b) + 6, 11, 5, on == wheelPage ? accent() : 0x55FFFFFF); text(c, b, tx + cell - width(b) - 5, ty + 5, on == wheelPage ? 0xFF140A28 : 0xFFFFFFFF, false); }
        }
        c.disableScissor();
        int contentH = layout.isEmpty() ? 0 : layout.get(layout.size() - 1)[2] + cell - gt;
        clampScroll((contentH + EMOTE_GAP + cell) / (cell + EMOTE_GAP), cell + EMOTE_GAP, top);
    }

    // emote groups: 1 gestures, 2 dances, 3 with props
    private static int emoteCat = 0;
    private static final String[] EMOTE_CATS = {"All", "Gestures", "Dances", "With props"};
    private static final int EMOTE_CELL = 58, EMOTE_GAP = 6;
    private static int catOf(Emote e) { return e.hasProp() ? 3 : e.premium ? 2 : 1; }
    /** {emote ordinal (-1 = header), x, y, category, count} in content coordinates */
    private List<int[]> emoteLayout(int px, int pw, int gt) {
        List<int[]> out = new ArrayList<>();
        int cols = Math.max(1, (pw + EMOTE_GAP) / (EMOTE_CELL + EMOTE_GAP)), y = gt;
        for (int cat = 1; cat <= 3; cat++) {
            if (emoteCat != 0 && emoteCat != cat) continue;
            List<Emote> es = new ArrayList<>();
            for (Emote e : Emote.values()) if (catOf(e) == cat) es.add(e);
            if (es.isEmpty()) continue;
            out.add(new int[]{-1, px, y, cat, es.size()});
            y += 14;
            for (int k = 0; k < es.size(); k++) out.add(new int[]{es.get(k).ordinal(), px + (k % cols) * (EMOTE_CELL + EMOTE_GAP), y + (k / cols) * (EMOTE_CELL + EMOTE_GAP), cat, 0});
            y += ((es.size() + cols - 1) / cols) * (EMOTE_CELL + EMOTE_GAP) + 6;
        }
        return out;
    }
    private boolean clickEmotes(double mx, double my, int px, int top, int pw) {
        int x = px;
        for (int p = 0; p < dev.orbit.client.emote.EmoteWheel.PAGES; p++) {
            String l = "Page " + (p + 1) + "  " + dev.orbit.client.emote.EmoteWheel.count(p) + "/6";
            int w = width(l) + 14;
            if (in(mx, my, x, top + 13, w, 16)) { wheelPage = p; Sounds.tab(); return true; }
            x += w + 4;
        }
        int cw = chipW(pw);
        for (int s = 0; s < dev.orbit.client.emote.EmoteWheel.SLOTS; s++)
            if (in(mx, my, px + s * (cw + 4), top + 34, cw, 22) && dev.orbit.client.emote.EmoteWheel.get(wheelPage, s) != null) {
                dev.orbit.client.emote.EmoteWheel.clear(wheelPage, s); Sounds.toggle(false); return true;
            }
        int chipX = px + width("Show:") + 6;
        for (int ci = 0; ci < EMOTE_CATS.length; ci++) {
            int w = width(EMOTE_CATS[ci]) + 14;
            if (in(mx, my, chipX, top + 60, w, 16)) { if (emoteCat != ci) { emoteCat = ci; scroll = scrollTarget = 0; Sounds.tab(); } return true; }
            chipX += w + 3;
        }
        int gt = gridTop(top);
        if (my < gt) return false;
        Emote[] all = Emote.values();
        for (int[] it : emoteLayout(px, pw, gt)) {
            if (it[0] < 0) continue;
            int tx = it[1], ty = it[2] - Math.round(scroll);
            if (!in(mx, my, tx, ty, EMOTE_CELL, EMOTE_CELL)) continue;
            Emote e = all[it[0]];
            if (dev.orbit.client.emote.EmoteWheel.pageOf(e) == wheelPage) { dev.orbit.client.emote.EmoteWheel.remove(e); Sounds.toggle(false); say(e.title + " removed from page " + (wheelPage + 1), true); }
            else if (dev.orbit.client.emote.EmoteWheel.add(wheelPage, e)) { Sounds.toggle(true); say(e.title + " is on page " + (wheelPage + 1), true); }
            else say("Page " + (wheelPage + 1) + " is full — click a slot above to free one", false);
            return true;
        }
        return false;
    }
    // ------------------------------------------------------------------ screenshots
    private File shotsDir() { return new File(client.runDirectory, "screenshots"); }
    private void loadShots() {
        File[] files = shotsDir().listFiles((d, n) -> n.toLowerCase().endsWith(".png"));
        shots = new ArrayList<>();
        if (files != null) { java.util.Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified())); shots.addAll(List.of(files)); }
    }
    private static final java.util.Set<String> LOADING = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private Identifier thumb(File f) {
        String key = "shot:" + f.getName() + ":" + f.lastModified();
        Identifier id = Tex.get(key);
        if (id != null || !LOADING.add(key)) return id;
        Util.getIoWorkerExecutor().execute(() -> {
            try {
                java.awt.image.BufferedImage src = javax.imageio.ImageIO.read(f);
                if (src == null) return;
                int w = 480, h = Math.max(1, Math.round(480f * src.getHeight() / src.getWidth()));
                java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                var g = img.createGraphics();
                g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(src, 0, 0, w, h, null); g.dispose();
                var out = new java.io.ByteArrayOutputStream();
                javax.imageio.ImageIO.write(img, "png", out);
                byte[] png = out.toByteArray();
                client.execute(() -> Tex.fromPng(key, png, true));
            } catch (Exception e) { OrbitClient.LOG.debug("Thumbnail failed for {}", f, e); }
        });
        return null;
    }

    private void screenshots(DrawContext c, int mx, int my) {
        if (shots == null) loadShots();
        int px = 24, pw = width - 48, y = 44;
        display(c, "Screenshots", px, y, 0xFFFFFFFF, false);
        text(c, shots.size() + " in your profile  ·  F2 takes one in-game", px, y + 12, MUTED, false);
        btn(c, "folder", px + pw - 100, y, 100, 20, "Open folder", mx, my, 0, false);
        int top = y + 32;
        if (shots.isEmpty()) { centered(c, "No screenshots yet", width / 2f, top + 40, FAINT, false); return; }
        int cols = Math.max(1, pw / 150), gap = 8, cw = (pw - (cols - 1) * gap) / cols, chh = Math.round(cw * 9 / 16f);
        scroll = approach(scroll, scrollTarget, 16, 1 / 60f);
        c.enableScissor(0, top, width, height - 18);
        for (int i = 0; i < shots.size(); i++) {
            int x = px + (i % cols) * (cw + gap), yy = top + (i / cols) * (chh + gap) - Math.round(scroll);
            if (yy > height || yy + chh < top) continue;
            boolean h = in(mx, my, x, yy, cw, chh) && preview == null;
            round(c, x - 1, yy - 1, cw + 2, chh + 2, 6, h ? 0x66FFFFFF : 0x1AFFFFFF);
            Identifier t = thumb(shots.get(i));
            if (t != null) Compat.blitTint(c, t, x, yy, cw, chh, 0, 0, 480, 270, 480, 270, 0xFFFFFFFF);
            else round(c, x, yy, cw, chh, 5, 0x22000000);
        }
        c.disableScissor();
        int rows = (shots.size() + cols - 1) / cols;
        scrollTarget = Math.max(0, Math.min(Math.max(0, rows * (chh + gap) - (height - 18 - top)), scrollTarget));
        if (preview != null) {
            c.fill(0, 0, width, height, 0xD0050310);
            int w = Math.min(width - 60, Math.round((height - 90) * 16 / 9f)), h = Math.round(w * 9 / 16f);
            int x = (width - w) / 2, yy = 30;
            Identifier t = thumb(preview);
            if (t != null) Compat.blitTint(c, t, x, yy, w, h, 0, 0, 480, 270, 480, 270, 0xFFFFFFFF);
            centered(c, preview.getName(), width / 2f, yy + h + 8, MUTED, false);
            int bx0 = width / 2 - 188;
            btn(c, "popen", bx0, yy + h + 22, 90, 20, "Open", mx, my, 0, true);
            btn(c, "pcopy", bx0 + 94, yy + h + 22, 90, 20, "Copy", mx, my, 0, false);
            btn(c, "pdel", bx0 + 188, yy + h + 22, 90, 20, confirming("delshot") ? "Really delete?" : "Delete", mx, my, 0, false);
            btn(c, "pclose", bx0 + 282, yy + h + 22, 90, 20, "Close", mx, my, 0, false);
        }
    }

    // ------------------------------------------------------------------ accounts
    private void accountsTab(DrawContext c, int mx, int my) {
        hits.clear();
        long since = System.currentTimeMillis() - switchedAt;
        int acc = accent();
        int px = 24, pw = Math.min(skin != null ? panelW() : width - 48, 460), y = 44;
        display(c, "Accounts", px, y, 0xFFFFFFFF, false);
        text(c, "Switch accounts without restarting Minecraft", px, y + 12, MUTED, false);
        String current = client.getSession().getUsername();
        boolean bridge = OrbitBridge.available();
        if (accounts == null && bridge) loadAccounts();

        // current account: big card
        int cy = y + 30;
        float a0 = easeOut((since - 40) / 300f);
        int cyA = cy + Math.round((1 - a0) * 6);
        round(c, px, cyA, pw, 46, 12, withAlpha(acc, (int) (60 * a0)));
        round(c, px + 1, cyA + 1, pw - 2, 44, 11, withAlpha(0x0B0818, (int) (150 * a0)));
        headOf(c, current, px + 10, cyA + 8, 30);
        statusDot(c, px + 38, cyA + 36, true);
        bold(c, current, px + 50, cyA + 10, 0xFFFFFFFF, false);
        String type = offline() ? "Offline account" : "Microsoft account";
        int tw = width(type) + 12;
        round(c, px + 50, cyA + 23, tw, 13, 6, offline() ? 0x26FFFFFF : 0x332F80ED);
        text(c, type, px + 56, cyA + 26, offline() ? MUTED : 0xFF9CC8FF, false);
        String act = "Playing now";
        int aw = width(act) + 18;
        round(c, px + pw - aw - 10, cyA + 15, aw, 16, 8, 0x334ADE80);
        disc(c, px + pw - aw - 2, cyA + 23, 2.2f, 0xFF4ADE80);
        text(c, act, px + pw - aw + 4, cyA + 19, 0xFF8CF5B4, false);

        int ry = cy + 58;
        if (!bridge) { // started without the launcher: explain instead of an empty page
            round(c, px, ry, pw, 52, 12, 0x1AFFFFFF);
            item(c, net.minecraft.item.Items.COMPASS, px + 12, ry + 18, 16);
            bold(c, "Open Minecraft from the Orbit launcher", px + 38, ry + 12, 0xFFFFFFFF, false);
            text(c, fit("Your saved accounts live in the launcher — start the game from there to switch here.", pw - 50, false), px + 38, ry + 25, MUTED, false);
            return;
        }
        if (accounts == null) {
            for (int i = 0; i < 2; i++) { // loading shimmer
                float s = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 220.0 + i);
                round(c, px, ry + 16 + i * 40, pw, 34, 10, withAlpha(0xFFFFFF, (int) (14 + 14 * s)));
            }
            text(c, "YOUR ACCOUNTS", px + 2, ry, FAINT, false);
            return;
        }

        // other accounts: grid of cards
        var others = accounts.stream().filter(a -> !str(a, "name").equalsIgnoreCase(current)).toList();
        text(c, "YOUR ACCOUNTS — " + others.size(), px + 2, ry, FAINT, false);
        ry += 14;
        int cols = pw >= 320 ? 2 : 1, gap = 8, cw = (pw - (cols - 1) * gap) / cols, ch = 36;
        if (others.isEmpty()) {
            round(c, px, ry, pw, 30, 10, 0x10FFFFFF);
            centered(c, "No other accounts yet — add one below", px + pw / 2f, ry + 11, FAINT, false);
            ry += 38;
        }
        for (int i = 0; i < others.size(); i++) {
            JsonObject a = others.get(i);
            String name = str(a, "name"), id = str(a, "id");
            boolean msa = "msa".equals(str(a, "type"));
            float ap = easeOut((since - 90 - i * 40) / 300f);
            int x = px + (i % cols) * (cw + gap), yy = ry + (i / cols) * (ch + gap) + Math.round((1 - ap) * 6);
            boolean h = in(mx, my, x, yy, cw, ch);
            float hv = hov("acc:" + id, h);
            round(c, x, yy, cw, ch, 10, lerpColor(withAlpha(0xFFFFFF, (int) (18 * ap)), withAlpha(acc, 70), hv));
            headOf(c, name, x + 9, yy + 8, 20);
            bold(c, fit(name, cw - 100, true), x + 36, yy + 8, 0xFFFFFFFF, false);
            text(c, msa ? "Microsoft" : "Offline", x + 36, yy + 20, msa ? 0xFF9CC8FF : FAINT, false);
            hit(x, yy, cw, ch, () -> { Sounds.click(); switchAccount(a); });
            if (hv > 0.02f) {
                int sw2 = width("Switch") + 14;
                round(c, x + cw - sw2 - 26, yy + 10, sw2, 16, 8, withAlpha(acc, (int) (235 * hv)));
                text(c, "Switch", x + cw - sw2 - 19, yy + 14, alpha(0xFF0E0A1C, hv), false);
                boolean conf = confirming("delacc:" + id);
                pill(c, "rmacc:" + id, conf ? "?" : "×", x + cw - 22, yy + 11, 14, 14, mx, my, conf ? 0xFFE5484D : 0x33FFFFFF, 0xFFE5484D, 0xFFFFFFFF, () -> {
                    Sounds.click();
                    if (!confirm("delacc:" + id)) return;
                    OrbitBridge.post("accounts/remove", OrbitBridge.obj("id", id)).whenComplete((r, e) -> client.execute(() -> {
                        if (e != null) say(OrbitBridge.message(e), false); else { accounts = objs(r); say("Removed " + name, true); }
                    }));
                });
            }
        }
        if (!others.isEmpty()) ry += ((others.size() + cols - 1) / cols) * (ch + gap) + 4;

        // add an account: two cards side by side (stacked when narrow)
        text(c, "ADD AN ACCOUNT", px + 2, ry, FAINT, false);
        ry += 14;
        boolean side = pw >= 340;
        int bw2 = side ? (pw - gap) / 2 : pw, bh = 54;
        // Microsoft
        round(c, px, ry, bw2, bh, 12, 0x1AFFFFFF);
        round(c, px + 10, ry + 10, 14, 14, 3, 0xFF2F80ED);
        c.fill(px + 12, ry + 12, px + 16, ry + 16, 0xFFF25022); c.fill(px + 18, ry + 12, px + 22, ry + 16, 0xFF7FBA00);
        c.fill(px + 12, ry + 18, px + 16, ry + 22, 0xFF00A4EF); c.fill(px + 18, ry + 18, px + 22, ry + 22, 0xFFFFB900);
        bold(c, "Microsoft", px + 30, ry + 11, 0xFFFFFFFF, false);
        text(c, fit("Sign in opens in the launcher", bw2 - 40, false), px + 30, ry + 22, FAINT, false);
        pill(c, "addms", "Sign in", px + 10, ry + 34, bw2 - 20, 15, mx, my, withAlpha(0x2F80ED, 170), 0xFF2F80ED, 0xFFFFFFFF, () -> {
            Sounds.click();
            say("Sign in with Microsoft in the Orbit launcher window…", true);
            OrbitBridge.post("accounts/microsoft", null, 600).whenComplete((r, e) -> client.execute(() -> {
                if (e != null) say(OrbitBridge.message(e), false);
                else { accounts = objs(r); Sounds.success(); say("Microsoft account added — click it to switch", true); }
            }));
        });
        // Offline
        int ox = side ? px + bw2 + gap : px, oy = side ? ry : ry + bh + gap;
        round(c, ox, oy, bw2, bh, 12, 0x1AFFFFFF);
        item(c, net.minecraft.item.Items.PLAYER_HEAD, ox + 9, oy + 9, 16);
        bold(c, "Offline", ox + 30, oy + 11, 0xFFFFFFFF, false);
        text(c, fit("Skin comes from the username", bw2 - 40, false), ox + 30, oy + 22, FAINT, false);
        inputBox(c, "offline", "Username…", ox + 10, oy + 32, bw2 - 56, mx, my);
        hit(ox + 10, oy + 32, bw2 - 56, 20, () -> inputFor = "offline");
        pill(c, "addoff", "Add", ox + bw2 - 42, oy + 34, 32, 16, mx, my, withAlpha(acc, 200), acc, 0xFF0E0A1C, () -> { inputFor = "offline"; Sounds.click(); submitInput(); });

        // the preview on the right shows who you're playing as
        if (skin != null) {
            int scx = skinCenterX(), sy = skin.y + skin.h + 6;
            centered(c, current, scx, sy, 0xFFFFFFFF, true);
            centered(c, "Drag to spin", scx, sy + 11, FAINT, false);
        }
    }

    // ------------------------------------------------------------------ clickable regions (Friends / Accounts)
    /** drawn buttons register here while rendering, so clicks always hit exactly what is on screen */
    private record Hit(int x, int y, int w, int h, Runnable action) {}
    private final List<Hit> hits = new ArrayList<>();
    private void hit(int x, int y, int w, int h, Runnable r) { hits.add(new Hit(x, y, w, h, r)); }
    private boolean clickHits(double mx, double my) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (in(mx, my, h.x(), h.y(), h.w(), h.h())) { h.action().run(); return true; }
        }
        return false;
    }
    private static final java.time.format.DateTimeFormatter HHMM = java.time.format.DateTimeFormatter.ofPattern("HH:mm").withZone(java.time.ZoneId.systemDefault());
    /** a player's face (or a soft placeholder while the skin loads) */
    private void headOf(DrawContext c, String name, int x, int y, int size) {
        SkinTextures st = NameSkins.get(name);
        if (st != null) face(c, st.body().texturePath(), x, y, size);
        else { round(c, x, y, size, size, Math.max(3, size / 5), 0x33FFFFFF); centered(c, name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(), x + size / 2f, y + size / 2f - 4, MUTED, false); }
    }
    /** status dot with a dark ring, bottom-right of a face */
    private void statusDot(DrawContext c, float x, float y, boolean on) {
        disc(c, x, y, 3.6f, 0xFF0B0818);
        disc(c, x, y, 2.5f, on ? 0xFF4ADE80 : 0xFF6C6390);
    }
    /** small pill button, registers its click */
    private void pill(DrawContext c, String id, String label, int x, int y, int w, int h, int mx, int my, int base, int hoverCol, int textCol, Runnable r) {
        float hv = hov(id, in(mx, my, x, y, w, h));
        round(c, x, y, w, h, h / 2, lerpColor(base, hoverCol, hv));
        centered(c, label, x + w / 2f, y + (h - 8) / 2f + 1, textCol, false);
        hit(x, y, w, h, r);
    }
    /** round "send" button with a paper-plane triangle */
    private void sendButton(DrawContext c, int x, int y, int s, int mx, int my, Runnable r) {
        float hv = hov("send", in(mx, my, x, y, s, s));
        round(c, x, y, s, s, s / 2, lerpColor(accent(), 0xFFFFFFFF, 0.2f * hv));
        int tx = x + s / 2 - 3, ty = y + s / 2 - 5;
        for (int i = 0; i < 6; i++) c.fill(tx + i, ty + i, tx + i + 1, ty + 11 - i, 0xFF0E0A1C);
        hit(x, y, s, s, r);
    }

    // ------------------------------------------------------------------ friends
    private String selectedFriend;
    private int friendFilter; // 0 all, 1 online, 2 requests
    private float chatScroll, chatScrollTarget, friendScroll, friendScrollTarget;
    private int chatX0, chatY0, chatX1, chatY1, listY0, listY1; // scroll areas (set while drawing)
    private int fSideW() { return Math.max(150, Math.min(214, Math.round(width * 0.34f))); }

    private void friendsTab(DrawContext c, int mx, int my) {
        hits.clear();
        long since = System.currentTimeMillis() - switchedAt;
        float appear = easeOut(since / 380f);
        int acc = accent();
        int sx = 16, top = 40, bottom = height - 14, sw = fSideW();
        var all = new ArrayList<>(dev.orbit.client.friends.Friends.list());
        var requests = new ArrayList<>(dev.orbit.client.friends.Friends.REQUESTS.entrySet());
        java.util.function.Predicate<dev.orbit.client.friends.Friends.Friend> isOn = f -> dev.orbit.client.friends.Friends.online(f.uuid);
        long onlineCount = all.stream().filter(isOn).count();

        // ---------- sidebar
        round(c, sx, top, sw, bottom - top, 14, withAlpha(0x0B0818, (int) (200 * appear)));
        bold(c, "Friends", sx + 12, top + 11, 0xFFFFFFFF, false);
        String cnt = onlineCount + " online";
        text(c, cnt, sx + sw - 12 - width(cnt), top + 11, onlineCount > 0 ? 0xFF8CF5B4 : FAINT, false);
        // add by name
        int ay = top + 26;
        inputBox(c, "friendname", "Add by username…", sx + 10, ay, sw - 44, mx, my);
        hit(sx + 10, ay, sw - 44, 20, () -> inputFor = "friendname");
        pill(c, "addf", "+", sx + sw - 30, ay, 20, 20, mx, my, withAlpha(acc, 200), acc, 0xFF0E0A1C, () -> { inputFor = "friendname"; Sounds.click(); submitInput(); });
        // filter: All / Online / Requests
        String[] fl = {"All", "Online", "Invites" + (requests.isEmpty() ? "" : " " + requests.size())};
        int fx = sx + 10, fy = ay + 26, fw = (sw - 20 - 8) / 3;
        round(c, fx - 2, fy - 2, sw - 16, 20, 10, 0x14FFFFFF);
        for (int i = 0; i < 3; i++) {
            int bx = fx + i * (fw + 4);
            boolean a = friendFilter == i, h = in(mx, my, bx, fy, fw, 16);
            float hv = hov("ff" + i, h && !a);
            if (a) round(c, bx, fy, fw, 16, 8, 0xF2FFFFFF);
            else if (hv > 0.01f) round(c, bx, fy, fw, 16, 8, withAlpha(0xFFFFFF, (int) (30 * hv)));
            centered(c, fit(fl[i], fw - 4, false), bx + fw / 2f, fy + 4, a ? 0xFF140A28 : (i == 2 && !requests.isEmpty() ? 0xFFFFB86B : MUTED), false);
            final int k = i;
            hit(bx, fy, fw, 16, () -> { friendFilter = k; friendScrollTarget = 0; Sounds.tab(); });
        }
        int ly = fy + 26;
        if (dev.orbit.client.emote.EmoteSync.configured() && !dev.orbit.client.friends.Friends.connected()) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 250.0);
            disc(c, sx + 15, ly + 4, 2.5f, withAlpha(0xFFB020, (int) (120 + 135 * pulse)));
            text(c, "Connecting…", sx + 22, ly, FAINT, false);
            ly += 14;
        }
        // list (scrollable)
        listY0 = ly; listY1 = bottom - 8;
        friendScroll = approach(friendScroll, friendScrollTarget, 16, frameDt);
        c.enableScissor(sx, listY0, sx + sw, listY1);
        int y = ly - Math.round(friendScroll), rowH = 30, n = 0;
        if (friendFilter == 2 || (friendFilter == 0 && !requests.isEmpty())) {
            y = sectionHead(c, "REQUESTS", requests.size(), sx + 12, y);
            if (requests.isEmpty()) { text(c, "No requests", sx + 14, y, FAINT, false); y += 16; }
            for (var r : requests) {
                float ap = easeOut((since - 60 - n++ * 30) / 260f);
                int ry = y + Math.round((1 - ap) * 6);
                round(c, sx + 8, ry, sw - 16, rowH - 2, 9, withAlpha(acc, (int) (46 * ap)));
                headOf(c, r.getValue(), sx + 14, ry + 5, 18);
                boolean wide = sw >= 190;
                int aw = wide ? 42 : 16, textW = sw - 46 - aw - 26;
                bold(c, fit(r.getValue(), textW, true), sx + 38, ry + 5, 0xFFFFFFFF, false);
                text(c, fit("wants to chat", textW, false), sx + 38, ry + 16, MUTED, false);
                if (ry + rowH > listY0 && ry < listY1) {
                    pill(c, "acc" + r.getKey(), wide ? "Accept" : "+", sx + sw - 34 - aw, ry + 6, aw, 16, mx, my, 0x4D4ADE80, 0xFF4ADE80, 0xFFFFFFFF,
                            () -> { dev.orbit.client.friends.Friends.addDirect(r.getKey(), r.getValue()); dev.orbit.client.friends.Friends.REQUESTS.remove(r.getKey()); selectedFriend = r.getKey(); friendFilter = 0; Sounds.success(); });
                    pill(c, "dec" + r.getKey(), "×", sx + sw - 30, ry + 6, 16, 16, mx, my, 0x22FFFFFF, 0xFFE5484D, 0xFFFFFFFF,
                            () -> { dev.orbit.client.friends.Friends.REQUESTS.remove(r.getKey()); Sounds.click(); });
                }
                y += rowH + 2;
            }
            y += 4;
        }
        if (friendFilter != 2) {
            var on = all.stream().filter(isOn).toList();
            var off = friendFilter == 1 ? List.<dev.orbit.client.friends.Friends.Friend>of() : all.stream().filter(isOn.negate()).toList();
            if (all.isEmpty()) {
                text(c, "No friends yet", sx + 14, y + 2, MUTED, false);
                text(c, fit("Type a username above and press +", sw - 28, false), sx + 14, y + 14, FAINT, false);
            }
            if (!on.isEmpty() || friendFilter == 1) y = sectionHead(c, "ONLINE", on.size(), sx + 12, y);
            if (on.isEmpty() && friendFilter == 1) { text(c, "Nobody's online right now", sx + 14, y, FAINT, false); y += 16; }
            for (var f : on) { y = friendRow(c, f, true, sx, y, sw, rowH, mx, my, since, n++); }
            if (!off.isEmpty()) { y += 4; y = sectionHead(c, "OFFLINE", off.size(), sx + 12, y); }
            for (var f : off) { y = friendRow(c, f, false, sx, y, sw, rowH, mx, my, since, n++); }
        }
        c.disableScissor();
        int listH = y + Math.round(friendScroll) - ly;
        friendScrollTarget = Math.max(0, Math.min(friendScrollTarget, Math.max(0, listH - (listY1 - listY0))));

        // ---------- chat
        int cx = sx + sw + 10, cw = width - 16 - cx;
        round(c, cx, top, cw, bottom - top, 14, withAlpha(0x0B0818, (int) (170 * appear)));
        var friend = all.stream().filter(f -> f.uuid.equals(selectedFriend)).findFirst().orElse(null);
        if (friend == null) {
            float bob = (float) Math.sin(System.currentTimeMillis() / 600.0) * 2;
            int ex = cx + cw / 2, ey = (top + bottom) / 2 - 20;
            disc(c, ex, ey + bob, 22, withAlpha(acc, 40));
            disc(c, ex, ey + bob, 14, withAlpha(acc, 70));
            item(c, net.minecraft.item.Items.PLAYER_HEAD, ex - 8, Math.round(ey + bob) - 8, 16);
            centered(c, all.isEmpty() ? "Your friends will show up here" : "Pick a friend to start chatting", ex, ey + 32, TEXT, false);
            centered(c, all.isEmpty() ? "Add Orbit players by their Minecraft name" : "Messages are sent through Orbit — no server needed", ex, ey + 44, FAINT, false);
            return;
        }
        dev.orbit.client.friends.Friends.UNREAD.remove(friend.uuid);
        boolean on = dev.orbit.client.friends.Friends.online(friend.uuid);
        // header
        headOf(c, friend.name, cx + 12, top + 9, 22);
        statusDot(c, cx + 32, top + 29, on);
        bold(c, friend.name, cx + 42, top + 10, 0xFFFFFFFF, false);
        boolean conf = confirming("delfriend:" + friend.uuid);
        String rl0 = conf ? "Really remove?" : "Remove";
        text(c, fit(on ? "Online" : "Offline — messages only arrive while they're online", cw - 70 - width(rl0) - 16, false), cx + 42, top + 21, on ? 0xFF8CF5B4 : FAINT, false);
        String rl = conf ? "Really remove?" : "Remove";
        int rw = width(rl) + 16;
        pill(c, "rmfriend", rl, cx + cw - rw - 10, top + 12, rw, 16, mx, my, conf ? 0xCCE5484D : 0x1AFFFFFF, 0xFFE5484D, conf ? 0xFFFFFFFF : MUTED,
                () -> { if (confirm("delfriend:" + friend.uuid)) { dev.orbit.client.friends.Friends.remove(friend.uuid); selectedFriend = null; } Sounds.click(); });
        c.fill(cx + 10, top + 38, cx + cw - 10, top + 39, 0x18FFFFFF);

        // messages: newest at the bottom, grouped by sender, time under each group
        int inputY = bottom - 30;
        chatX0 = cx; chatX1 = cx + cw; chatY0 = top + 40; chatY1 = inputY - 6;
        chatScroll = approach(chatScroll, chatScrollTarget, 16, frameDt);
        var msgs = dev.orbit.client.friends.Friends.chat(friend.uuid);
        int maxBubble = Math.round(cw * 0.66f), yb = chatY1 - 4 + Math.round(chatScroll);
        c.enableScissor(chatX0, chatY0, chatX1, chatY1);
        for (int i = msgs.size() - 1; i >= 0; i--) {
            var m = msgs.get(i);
            var next = i + 1 < msgs.size() ? msgs.get(i + 1) : null;
            var prev = i > 0 ? msgs.get(i - 1) : null;
            boolean lastOfGroup = next == null || next.mine != m.mine || next.at - m.at > 300_000;
            boolean firstOfGroup = prev == null || prev.mine != m.mine || m.at - prev.at > 300_000;
            if (lastOfGroup) { // time below the group
                yb -= 10;
                String t = HHMM.format(java.time.Instant.ofEpochMilli(m.at));
                if (m.mine) text(c, t, cx + cw - 14 - width(t), yb + 1, FAINT, false); else text(c, t, cx + 34, yb + 1, FAINT, false);
            }
            List<String> lines = wrap(m.text, maxBubble - 18);
            int bw = 0; for (String l : lines) bw = Math.max(bw, width(l));
            bw += 18;
            int bh = lines.size() * 10 + 9;
            yb -= bh;
            int bx = m.mine ? cx + cw - 12 - bw : cx + 32;
            if (yb + bh > chatY0 && yb < chatY1) {
                round(c, bx, yb, bw, bh, 9, m.mine ? acc : 0x2EFFFFFF);
                for (int k = 0; k < lines.size(); k++) text(c, lines.get(k), bx + 9, yb + 5 + k * 10, m.mine ? 0xFF0E0A1C : TEXT, false);
                if (!m.mine && lastOfGroup) headOf(c, friend.name, cx + 12, yb + bh - 14, 14);
            }
            yb -= firstOfGroup ? 8 : 3;
        }
        c.disableScissor();
        int contentH = (chatY1 - 4 + Math.round(chatScroll)) - yb;
        chatScrollTarget = Math.max(0, Math.min(chatScrollTarget, Math.max(0, contentH - (chatY1 - chatY0))));
        if (msgs.isEmpty()) {
            headOf(c, friend.name, cx + cw / 2 - 12, (chatY0 + chatY1) / 2 - 26, 24);
            centered(c, "This is the start of your chat with " + friend.name, cx + cw / 2f, (chatY0 + chatY1) / 2f + 4, TEXT, false);
            centered(c, "Say hi!", cx + cw / 2f, (chatY0 + chatY1) / 2f + 16, FAINT, false);
        }
        if (chatScroll > 30) { // jump back to the newest message
            int jw = 60, jx = cx + cw / 2 - jw / 2;
            pill(c, "jump", "Newest", jx, chatY1 - 20, jw, 16, mx, my, 0xCC1A1530, acc, TEXT, () -> chatScrollTarget = 0);
        }
        // input bar
        round(c, cx + 8, inputY - 2, cw - 16, 24, 12, 0x14FFFFFF);
        inputBox(c, "chat", on ? "Message " + friend.name + "…" : "Message " + friend.name + " (offline)…", cx + 10, inputY, cw - 46, mx, my);
        hit(cx + 10, inputY, cw - 46, 20, () -> inputFor = "chat");
        sendButton(c, cx + cw - 32, inputY, 20, mx, my, () -> { inputFor = "chat"; submitInput(); chatScrollTarget = 0; });
    }

    /** "ONLINE — 3" style group heading; returns the y below it */
    private int sectionHead(DrawContext c, String title, int count, int x, int y) {
        text(c, title + " — " + count, x, y + 2, FAINT, false);
        return y + 14;
    }

    private int friendRow(DrawContext c, dev.orbit.client.friends.Friends.Friend f, boolean on, int sx, int y, int sw, int rowH, int mx, int my, long since, int n) {
        float ap = easeOut((since - 60 - n * 30) / 260f);
        int ry = y + Math.round((1 - ap) * 6), x = sx + 8, w = sw - 16;
        boolean sel = f.uuid.equals(selectedFriend), h = in(mx, my, x, ry, w, rowH - 2) && my >= listY0 && my < listY1;
        float hv = hov("fr" + f.uuid, h);
        int acc = accent();
        round(c, x, ry, w, rowH - 2, 9, sel ? withAlpha(acc, 70) : withAlpha(0xFFFFFF, (int) (34 * hv)));
        if (sel) round(c, x + 1, ry + 7, 2, rowH - 16, 1, acc);
        headOf(c, f.name, x + 6, ry + 5, 18);
        if (!on) round(c, x + 6, ry + 5, 18, 18, 3, 0x66000000); // offline faces are dimmed
        statusDot(c, x + 23, ry + 22, on);
        int unread = dev.orbit.client.friends.Friends.UNREAD.getOrDefault(f.uuid, 0);
        int right = unread > 0 ? 26 : h ? 22 : 6;
        bold(c, fit(f.name, w - 38 - right, true), x + 30, ry + 5, on ? 0xFFFFFFFF : lerpColor(MUTED, 0xFFFFFFFF, hv), false);
        var msgs = dev.orbit.client.friends.Friends.chat(f.uuid);
        String sub = msgs.isEmpty() ? (on ? "Online" : "Offline") : (msgs.get(msgs.size() - 1).mine ? "You: " : "") + msgs.get(msgs.size() - 1).text;
        text(c, fit(sub, w - 38 - right, false), x + 30, ry + 16, unread > 0 ? TEXT : FAINT, false);
        if (ry + rowH > listY0 && ry < listY1) {
            hit(x, Math.max(ry, listY0), w, Math.min(rowH - 2, listY1 - Math.max(ry, listY0)), () -> { selectedFriend = f.uuid; input = ""; inputFor = "chat"; chatScroll = chatScrollTarget = 0; Sounds.click(); });
            if (unread > 0) { round(c, x + w - 24, ry + 8, 18, 12, 6, 0xFFE5484D); centered(c, String.valueOf(Math.min(99, unread)), x + w - 15, ry + 10, 0xFFFFFFFF, false); }
            else if (h) {
                boolean conf = confirming("delfriend:" + f.uuid);
                pill(c, "rmf" + f.uuid, conf ? "?" : "×", x + w - 20, ry + 7, 14, 14, mx, my, conf ? 0xFFE5484D : 0x22FFFFFF, 0xFFE5484D, 0xFFFFFFFF,
                        () -> { if (confirm("delfriend:" + f.uuid)) { dev.orbit.client.friends.Friends.remove(f.uuid); if (f.uuid.equals(selectedFriend)) selectedFriend = null; } Sounds.click(); });
            }
        }
        return y + rowH + 2;
    }

    /** word-wraps text to a pixel width */
    private static List<String> wrap(String s, int max) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : s.split(" ")) {
            String test = line.length() == 0 ? word : line + " " + word;
            if (width(test) <= max) { line.setLength(0); line.append(test); continue; }
            if (line.length() > 0) out.add(line.toString());
            line.setLength(0);
            while (width(word) > max && word.length() > 1) { int n = word.length(); while (n > 1 && width(word.substring(0, n)) > max) n--; out.add(word.substring(0, n)); word = word.substring(n); }
            line.append(word);
        }
        if (line.length() > 0) out.add(line.toString());
        return out.isEmpty() ? List.of("") : out;
    }

    private boolean clickFriends(double mx, double my) { return clickHits(mx, my); }

    // ------------------------------------------------------------------ bridge loading
    private static String str(JsonObject o, String k) { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : ""; }
    private static List<JsonObject> objs(JsonElement e) {
        List<JsonObject> l = new ArrayList<>();
        for (JsonElement x : OrbitBridge.arr(e)) if (x.isJsonObject()) l.add(x.getAsJsonObject());
        return l;
    }
    private void loadAccounts() {
        if (loadingAccounts || !OrbitBridge.available()) return;
        loadingAccounts = true;
        OrbitBridge.get("accounts").whenComplete((r, e) -> client.execute(() -> {
            loadingAccounts = false;
            if (e != null) { say(OrbitBridge.message(e), false); accounts = List.of(); } else accounts = objs(r);
        }));
    }
    private void loadSkins() {
        if (loadingSkins || !OrbitBridge.available()) return;
        loadingSkins = true;
        OrbitBridge.get("skins").whenComplete((r, e) -> client.execute(() -> { loadingSkins = false; skins = e != null ? List.of() : objs(r); }));
    }
    private void loadCapes() {
        if (loadingCapes || !OrbitBridge.available()) return;
        loadingCapes = true;
        OrbitBridge.get("capes").whenComplete((r, e) -> client.execute(() -> { loadingCapes = false; capes = e != null ? List.of() : objs(r); }));
    }

    private void switchAccount(JsonObject a) {
        say("Switching to " + str(a, "name") + "…", true);
        OrbitBridge.post("accounts/switch", OrbitBridge.obj("id", str(a, "id"))).whenComplete((r, e) -> client.execute(() -> {
            if (e != null) { say(OrbitBridge.message(e), false); return; }
            JsonObject o = r.getAsJsonObject();
            AccountSwitcher.apply(str(o, "name"), AccountSwitcher.uuid(str(o, "uuid")), str(o, "token"), "msa".equals(str(o, "type")));
            Sounds.success();
            say("Signed in as " + str(o, "name"), true);
            accounts = null;
            clearAndInit();
        }));
    }

    private void submitInput() {
        String v = input.trim();
        if (v.isEmpty()) return;
        if (inputFor.equals("skinname")) {
            if (!v.matches("[A-Za-z0-9_]{2,16}")) { say("That isn't a Minecraft username", false); return; }
            clearPreview();
            previewName = v; previewLabel = v + "'s skin"; previewKey = "name:" + v;
            previewApply = () -> {
                say("Getting " + v + "'s skin…", true);
                OrbitBridge.post("skins/username", OrbitBridge.obj("name", v)).whenComplete((r, e) -> client.execute(() -> {
                    if (e != null) { say(OrbitBridge.message(e), false); return; }
                    Sounds.success();
                    say("Now wearing " + v + "'s skin — others see it after you rejoin a server", true);
                    NameSkins.refresh(client.getSession().getUsername());
                    skins = null; input = ""; clearPreview();
                }));
            };
        } else if (inputFor.equals("friendname")) {
            if (!v.matches("[A-Za-z0-9_]{2,16}")) { say("That isn't a Minecraft username", false); return; }
            say("Looking up " + v + "…", true);
            dev.orbit.client.friends.Friends.add(v).whenComplete((f, e) -> client.execute(() -> {
                if (e != null) say(OrbitBridge.message(e), false);
                else { selectedFriend = f.uuid; input = ""; Sounds.success(); say("Added " + f.name + " — they need Orbit to chat", true); }
            }));
        } else if (inputFor.equals("chat")) {
            if (selectedFriend == null) return;
            if (dev.orbit.client.friends.Friends.send(selectedFriend, v)) { input = ""; Sounds.click(); }
            else say("Not connected yet — check your internet", false);
        } else if (inputFor.equals("offline")) {
            if (!v.matches("[A-Za-z0-9_]{3,16}")) { say("Usernames are 3–16 letters, numbers or _", false); return; }
            OrbitBridge.post("accounts/offline", OrbitBridge.obj("name", v)).whenComplete((r, e) -> client.execute(() -> {
                if (e != null) { say(OrbitBridge.message(e), false); return; }
                accounts = objs(r); input = ""; say("Added " + v, true);
            }));
        }
    }

    // ------------------------------------------------------------------ input
    private static boolean in(double mx, double my, double x, double y, double w, double h) { return mx >= x && my >= y && mx < x + w && my < y + h; }

    @Override
    protected boolean onClick(double mx, double my, int button) {
        if (skipIntro()) return true;
        if (button != 0) return false;
        if (appearanceOpen) return clickAppearance(mx, my);
        if (!ingame && in(mx, my, apBtnX(), 9, 20, 20)) { appearanceOpen = true; Sounds.click(); return true; }
        if (skin != null && (tab == Tab.HOME || tab == Tab.LOCKER) && skin.contains(mx, my)) { skin.drag(mx); draggingSkin = true; return true; }
        int[] xs = tabXs();
        for (Tab t : tabs()) if (in(mx, my, xs[t.ordinal()], 8, tabW(t), 22)) { switchTab(t); return true; }
        String cl = "Classic";
        int cw = width(cl) + 16;
        if (ingame && in(mx, my, width - cw - 8, 9, cw, 20)) {
            Sounds.click();
            allowVanillaPause = true;
            client.setScreen(new net.minecraft.client.gui.screen.GameMenuScreen(true));
            return true;
        }
        if (in(mx, my, width - cw - 8, 9, cw, 20)) {
            OrbitClient.modules().ui().spaceMenu.set(false);
            OrbitClient.modules().save();
            Sounds.click();
            allowVanillaOnce = true;
            client.setScreen(new TitleScreen());
            return true;
        }
        inputFor = "";
        switch (tab) {
            case HOME -> { return clickHome(mx, my); }
            case LOCKER -> { return clickLocker(mx, my); }
            case SHOTS -> { return clickShots(mx, my); }
            case ACCOUNTS -> { return clickAccounts(mx, my); }
            case FRIENDS -> { return clickFriends(mx, my); }
        }
        return false;
    }

    private boolean clickHome(double mx, double my) {
        if (ingame) return clickGameHome(mx, my);
        return clickHits(mx, my); // the launcher-style menu registers its own buttons
    }

    private boolean clickLocker(double mx, double my) {
        int px = panelX(), pw = panelW(), y = 44, sx = px;
        for (Sub s : Sub.values()) {
            int w = width(s.title) + 18;
            if (in(mx, my, sx, y + 28, w, 18)) { if (sub != s) { Sounds.tab(); clearPreview(); clearHover(); scroll = scrollTarget = 0; if (s == Sub.CAPES && mcCapes != null && mcCapes.isEmpty()) mcCapes = null; } sub = s; input = ""; return true; }
            sx += w + 4;
        }
        // preview buttons under the model
        if (skin != null && previewApply != null) {
            int scx = skinCenterX(), sy = skin.y + skin.h + 6;
            if (in(mx, my, scx - 76, sy + 24, 74, 18)) { Runnable r = previewApply; Sounds.click(); r.run(); return true; }
            if (in(mx, my, scx + 2, sy + 24, 74, 18)) { clearPreview(); Sounds.click(); return true; }
        }
        int top = y + 54, gt = gridTop(top);
        if (sub == Sub.SKINS) {
            int iw = Math.min(180, pw - 90);
            if (in(mx, my, px, top + 12, iw, 20)) { inputFor = "skinname"; return true; }
            if (in(mx, my, px + iw + 6, top + 12, 80, 20)) { inputFor = "skinname"; Sounds.click(); submitInput(); return true; }
            if (skins != null && my > gt) {
                int cell = 64, gap = 6, cols = Math.max(1, (pw + gap) / (cell + gap));
                for (int i = 0; i < skins.size(); i++) {
                    int x = px + (i % cols) * (cell + gap), yy = gt + (i / cols) * (cell + gap) - Math.round(scroll);
                    if (!in(mx, my, x, yy, cell, cell)) continue;
                    JsonObject s = skins.get(i);
                    String id = str(s, "id");
                    if (in(mx, my, x + cell - 16, yy + 2, 14, 14)) { // delete (click twice)
                        if (!confirm("delskin:" + id)) { Sounds.click(); return true; }
                        OrbitBridge.post("skins/delete", OrbitBridge.obj("id", id)).whenComplete((r, e) -> client.execute(() -> {
                            if (e != null) say(OrbitBridge.message(e), false);
                            else { skins = objs(r); Tex.forget("skin:" + id); say("Deleted " + str(s, "name"), true); if (("skin:" + id).equals(previewKey)) clearPreview(); }
                        }));
                        return true;
                    }
                    // preview on the model, apply with "Wear"
                    Identifier tex = Tex.fromDataUrl("skin:" + id, str(s, "data"), false);
                    if (tex == null) return true;
                    clearPreview();
                    previewKey = "skin:" + id;
                    previewLabel = str(s, "name");
                    previewSkin = new SkinTextures(new net.minecraft.util.AssetInfo.TextureAssetInfo(tex, tex), null, null,
                            "slim".equals(str(s, "model")) ? net.minecraft.entity.player.PlayerSkinType.SLIM : net.minecraft.entity.player.PlayerSkinType.WIDE, false);
                    previewApply = () -> {
                        say("Applying " + str(s, "name") + "…", true);
                        OrbitBridge.post("skins/apply", OrbitBridge.obj("id", id)).whenComplete((r, e) -> client.execute(() -> {
                            if (e != null) say(OrbitBridge.message(e), false);
                            else { Sounds.success(); clearPreview(); say("Skin changed — others see it after you rejoin a server", true); }
                        }));
                    };
                    Sounds.click();
                    return true;
                }
            }
        } else if (sub == Sub.CAPES && capes != null && my > gt) {
            for (int[] it : capeLayout(px, pw, gt)) {
                if (it[0] == 0 || it[0] == 3 || it[0] == 6) continue;
                int x = it[2], yy = it[3] - Math.round(scroll);
                if (!in(mx, my, x, yy, CW, CH)) continue;
                boolean mc = it[0] <= 2, none = it[0] == 2 || it[0] == 5;
                JsonObject cape = none ? null : (mc ? mcCapes : capes).get(it[1]);
                String id = none ? "" : str(cape, "id");
                Runnable equip;
                if (mc) {
                    if (mcOffline) { say("Offline accounts have no Minecraft capes", false); return true; }
                    equip = () -> {
                        say("Changing your Minecraft cape…", true);
                        OrbitBridge.post("capes/mojang/select", OrbitBridge.obj("id", id)).whenComplete((r, e) -> client.execute(() -> {
                            if (e != null) { say(OrbitBridge.message(e), false); return; }
                            if (r != null && r.isJsonObject()) mcCapes = objs(r.getAsJsonObject().get("capes"));
                            capes = null; // the Orbit cape was taken off so this one shows
                            clearPreview(); Sounds.success();
                            say(none ? "Minecraft cape removed" : "Minecraft cape equipped — others see it after you rejoin", true);
                        }));
                    };
                } else {
                    equip = () -> OrbitBridge.post("capes/select", OrbitBridge.obj("id", id)).whenComplete((r, e) -> client.execute(() -> {
                        if (e != null) say(OrbitBridge.message(e), false);
                        else { capes = objs(r); clearPreview(); Sounds.success(); say(none ? "Cape removed" : "Cape equipped", true); }
                    }));
                }
                if (none) { Sounds.click(); equip.run(); return true; }
                Identifier tex = Tex.fromDataUrl((mc ? "mccape:" : "cape:") + id, str(cape, "data"), !mc);
                clearPreview();
                previewKey = (mc ? "mccape:" : "cape:") + id;
                previewLabel = str(cape, "name");
                previewCape = tex;
                previewApply = equip;
                if (skin != null) skin.showBack(true);
                Sounds.click();
                return true;
            }        } else if (sub == Sub.EMOTES) {
            return clickEmotes(mx, my, px, top, pw);
        } else if (sub == Sub.COSMETICS && my > gt) {
            var cos = (dev.orbit.client.module.NewModules.Cosmetics) OrbitClient.modules().get("cosmetics");
            String[][] groups = {dev.orbit.client.module.NewModules.Cosmetics.HATS, dev.orbit.client.module.NewModules.Cosmetics.WINGS, dev.orbit.client.module.NewModules.Cosmetics.AURAS};
            int yy = gt - Math.round(scroll);
            for (int gi = 0; gi < 3; gi++) {
                yy += 12;
                var setting = gi == 0 ? cos.hat : gi == 1 ? cos.wings : cos.aura;
                int cell = 62, gap = 6, cols = Math.max(1, (pw + gap) / (cell + gap));
                for (int i = 0; i < groups[gi].length; i++) {
                    int x = px + (i % cols) * (cell + gap), cy = yy + (i / cols) * (62 + gap);
                    if (in(mx, my, x, cy, cell, 62)) {
                        setting.set(groups[gi][i]);
                        if (!cos.isEnabled()) cos.setEnabled(true);
                        OrbitClient.modules().save();
                        Sounds.toggle(!groups[gi][i].equals("None"));
                        return true;
                    }
                }
                yy += ((groups[gi].length + cols - 1) / cols) * (62 + gap) + 8;
            }
        }
        return false;
    }

    /** Puts an image file on the Windows clipboard (Minecraft runs Java without AWT, so PowerShell does it). */
    private void copyImage(File f) {
        if (!Util.getOperatingSystem().equals(Util.OperatingSystem.WINDOWS)) { say("Copying images works on Windows only", false); return; }
        String path = f.getAbsolutePath().replace("'", "''");
        String ps = "Add-Type -AssemblyName System.Windows.Forms,System.Drawing; $i=[System.Drawing.Image]::FromFile('" + path + "'); [System.Windows.Forms.Clipboard]::SetImage($i); $i.Dispose()";
        Util.getIoWorkerExecutor().execute(() -> {
            try {
                Process p = new ProcessBuilder("powershell", "-NoProfile", "-STA", "-WindowStyle", "Hidden", "-Command", ps).redirectErrorStream(true).start();
                boolean ok = p.waitFor(15, java.util.concurrent.TimeUnit.SECONDS) && p.exitValue() == 0;
                client.execute(() -> { if (ok) { Sounds.success(); say("Screenshot copied — paste it anywhere", true); } else say("Couldn't copy the screenshot", false); });
            } catch (Exception e) { client.execute(() -> say("Couldn't copy the screenshot", false)); }
        });
    }

    /** Moves a file to the Recycle Bin (so it can be restored); plain delete on other systems. */
    private void recycle(File f) {
        Util.getIoWorkerExecutor().execute(() -> {
            boolean ok;
            try {
                if (Util.getOperatingSystem().equals(Util.OperatingSystem.WINDOWS)) {
                    String path = f.getAbsolutePath().replace("'", "''");
                    Process p = new ProcessBuilder("powershell", "-NoProfile", "-WindowStyle", "Hidden", "-Command",
                            "Add-Type -AssemblyName Microsoft.VisualBasic; [Microsoft.VisualBasic.FileIO.FileSystem]::DeleteFile('" + path + "','OnlyErrorDialogs','SendToRecycleBin')").start();
                    ok = p.waitFor(15, java.util.concurrent.TimeUnit.SECONDS) && !f.exists();
                } else ok = f.delete();
            } catch (Exception e) { ok = false; }
            boolean done = ok;
            client.execute(() -> {
                if (done) { Sounds.success(); say("Moved to the Recycle Bin", true); preview = null; loadShots(); }
                else say("Couldn't delete the screenshot", false);
            });
        });
    }

    private boolean clickShots(double mx, double my) {
        int px = 24, pw = width - 48, y = 44;
        if (preview != null) {
            int w = Math.min(width - 60, Math.round((height - 90) * 16 / 9f)), h = Math.round(w * 9 / 16f), yy = 30, bx0 = width / 2 - 188;
            if (in(mx, my, bx0, yy + h + 22, 90, 20)) { Util.getOperatingSystem().open(preview); return true; }
            if (in(mx, my, bx0 + 94, yy + h + 22, 90, 20)) { Sounds.click(); copyImage(preview); return true; }
            if (in(mx, my, bx0 + 188, yy + h + 22, 90, 20)) { Sounds.click(); if (confirm("delshot")) recycle(preview); return true; }
            if (in(mx, my, bx0 + 282, yy + h + 22, 90, 20) || !in(mx, my, (width - w) / 2, yy, w, h)) { preview = null; Sounds.click(); }
            return true;
        }
        if (in(mx, my, px + pw - 100, y, 100, 20)) { shotsDir().mkdirs(); Util.getOperatingSystem().open(shotsDir()); return true; }
        if (shots == null || shots.isEmpty()) return false;
        int top = y + 32, cols = Math.max(1, pw / 150), gap = 8, cw = (pw - (cols - 1) * gap) / cols, chh = Math.round(cw * 9 / 16f);
        for (int i = 0; i < shots.size(); i++) {
            int x = px + (i % cols) * (cw + gap), yy = top + (i / cols) * (chh + gap) - Math.round(scroll);
            if (my >= top && in(mx, my, x, yy, cw, chh)) { preview = shots.get(i); Sounds.click(); return true; }
        }
        return false;
    }

    private boolean clickAccounts(double mx, double my) { return clickHits(mx, my); }

    @Override protected boolean onScroll(double mx, double my, double v) {
        if (tab == Tab.SHOTS || tab == Tab.LOCKER) { scrollTarget -= (float) (v * 30); return true; }
        if (tab == Tab.FRIENDS) {
            if (mx >= chatX0 && mx < chatX1 && my >= chatY0 && my < chatY1) { chatScrollTarget += (float) (v * 24); return true; } // up = older messages
            if (my >= listY0 && my < listY1 && mx < chatX0) { friendScrollTarget -= (float) (v * 24); return true; }
        }
        return false;
    }

    @Override
    protected boolean onKey(int key, int scan, int mods) {
        if (skipIntro()) return true;
        if (!inputFor.isEmpty()) {
            String edited = TextKeys.edit(input, key, mods, inputFor.equals("chat") ? 256 : 16);
            if (edited != null) { input = inputFor.equals("chat") ? edited : edited.replaceAll("[^A-Za-z0-9_]", ""); return true; }
            if (key == GLFW.GLFW_KEY_BACKSPACE && !input.isEmpty()) { input = input.substring(0, input.length() - 1); return true; }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { submitInput(); return true; }
            if (key == GLFW.GLFW_KEY_ESCAPE) { inputFor = ""; return true; }
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (appearanceOpen) { appearanceOpen = false; return true; }
            if (preview != null) { preview = null; return true; }
            if (tab != Tab.HOME) { switchTab(Tab.HOME); return true; }
            if (ingame) { client.setScreen(null); return true; }
        }
        return false;
    }

    @Override
    protected boolean onChar(char chr) {
        if (inputFor.isEmpty()) return false;
        if (TextKeys.ctrl(0)) return true;
        if (inputFor.equals("chat")) { if (chr >= ' ' && input.length() < 256) input += chr; return true; }
        if ((Character.isLetterOrDigit(chr) || chr == '_') && input.length() < 16) input += chr;
        return true;
    }
}
