package dev.orbit.client;

import dev.orbit.client.compat.Compat;
import dev.orbit.client.gui.HudEditorScreen;
import dev.orbit.client.gui.OrbitScreen;
import dev.orbit.client.module.HudModule;
import dev.orbit.client.module.ModuleManager;
import dev.orbit.client.spotify.SpotifyLyrics;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class OrbitClient implements ClientModInitializer {
    public static final String MOD_ID = "orbit";
    public static final Logger LOG = LoggerFactory.getLogger("Orbit");
    private static ModuleManager modules;
    public static KeyBinding menuKey, zoomKey, freelookKey, emoteKey, waypointKey, paletteKey;
    private static int saveTimer;

    public static ModuleManager modules() { return modules; }

    @Override
    public void onInitializeClient() {
        modules = new ModuleManager();
        modules.load();

        menuKey = KeyBindingHelper.registerKeyBinding(Compat.key("menu", GLFW.GLFW_KEY_RIGHT_SHIFT));
        zoomKey = KeyBindingHelper.registerKeyBinding(Compat.key("zoom", GLFW.GLFW_KEY_C));
        freelookKey = KeyBindingHelper.registerKeyBinding(Compat.key("freelook", GLFW.GLFW_KEY_LEFT_ALT));
        emoteKey = KeyBindingHelper.registerKeyBinding(Compat.key("emotes", GLFW.GLFW_KEY_B));
        waypointKey = KeyBindingHelper.registerKeyBinding(Compat.key("waypoint", GLFW.GLFW_KEY_N));
        paletteKey = KeyBindingHelper.registerKeyBinding(Compat.key("palette", GLFW.GLFW_KEY_K));
        modules.autoReconnect().install();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (menuKey.wasPressed()) if (client.currentScreen == null) client.setScreen(new OrbitScreen(null));
            modules.tick();
            dev.orbit.client.emote.EmoteSync.tick(); // friends stay online on the main menu too
            dev.orbit.client.friends.FriendsRelay.tick();
            dev.orbit.client.death.DeathAnimations.tick();
            dev.orbit.client.cosmetics.Props.tick();
            dev.orbit.client.pvp.Combat.tick();
            try { dev.orbit.client.cosmetics.CosmeticsApi.tick(); } catch (Throwable t) { LOG.debug("Cosmetics API tick", t); }
            while (paletteKey.wasPressed()) if (client.player != null && client.currentScreen == null) client.setScreen(new dev.orbit.client.gui.CommandPaletteScreen(null));
            SpotifyLyrics.setWanted(modules.get("lyrics").isEnabled() || modules.get("spotify").isEnabled());
            if (++saveTimer >= 20 * 30) { saveTimer = 0; modules.save(); } // autosave every 30s
        });

        HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            // the HUD follows the "Smooth font" setting even while an Orbit menu is open on top
            dev.orbit.client.gui.Render2D.hudPass = true;
            try { renderHud(ctx); } finally { dev.orbit.client.gui.Render2D.hudPass = false; }
        });

        // Reach Display: measure every hit you land
        net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient()) dev.orbit.client.pvp.Combat.onAttack(player, entity);
            return net.minecraft.util.ActionResult.PASS;
        });

        // sessions: stats reset when you join, a recap when you leave
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() -> {
            var se = client.getCurrentServerEntry();
            dev.orbit.client.pvp.Combat.reset(client.isInSingleplayer() ? "Singleplayer" : se != null ? se.address : "Multiplayer");
            if (modules.get("invprofiles") instanceof dev.orbit.client.module.PvpModules.InventoryProfiles ip) ip.onJoin();
        }));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
            if (modules.get("recap") instanceof dev.orbit.client.module.PvpModules.SessionRecap r && r.isEnabled()) {
                var snap = dev.orbit.client.pvp.Combat.snapshot();
                if (snap.millis() >= r.minMinutes.get() * 60_000) dev.orbit.client.pvp.Combat.pendingRecap = snap;
            }
        }));
        // chat bubbles
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, time) -> {
            if (sender != null && modules.get("chatbubbles") instanceof dev.orbit.client.module.PvpModules.ChatBubbles cb && cb.isEnabled())
                dev.orbit.client.module.PvpModules.ChatBubbles.said(sender.id(), signed != null ? signed.getContent().getString() : message.getString());
        });
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay && modules.get("chatbubbles") instanceof dev.orbit.client.module.PvpModules.ChatBubbles cb && cb.isEnabled())
                dev.orbit.client.module.PvpModules.ChatBubbles.parse(message.getString());
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(c -> { modules.save(); dev.orbit.client.friends.FriendsRelay.shutdown(); });

        dev.orbit.client.gui.ScreenFade.install(); // smooth fade between menus

        // hats & wings on the player model
        net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, ctx) -> {
            if (renderer instanceof net.minecraft.client.render.entity.PlayerEntityRenderer<?> pr)
                { helper.register(new dev.orbit.client.cosmetics.CosmeticsFeature((net.minecraft.client.render.entity.feature.FeatureRendererContext) pr));
                  helper.register(new dev.orbit.client.cosmetics.Props((net.minecraft.client.render.entity.feature.FeatureRendererContext) pr)); }
        });

        // Dev-only self-test for versions without Fabric's client game-test API (see AutoTest in those folders).
        if (System.getProperty("orbit.autotest") != null) {
            try { Class.forName("dev.orbit.client.AutoTest").getMethod("install").invoke(null); }
            catch (ReflectiveOperationException e) { LOG.warn("Autotest unavailable for this version", e); }
        }
        LOG.info("Orbit Client loaded — {} modules. Press Right Shift in-game.", modules.all().size());
    }

    private static void renderHud(net.minecraft.client.gui.DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options.hudHidden) return;
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        modules.waypoints().renderMarkers(ctx, sw, sh);
        if (modules.get("trajectories") instanceof dev.orbit.client.module.PvpModules.ProjectilePredictor pp) pp.render(ctx, sw, sh);
        if (modules.get("damagenumbers") instanceof dev.orbit.client.module.PvpModules.DamageNumbers dn) dn.render(ctx, sw, sh);
        if (modules.get("killeffects") instanceof dev.orbit.client.module.PvpModules.KillEffectsModule ke) ke.renderPopup(ctx, sw, sh);
        modules.crosshair().render(ctx, sw, sh);
        if (mc.currentScreen instanceof HudEditorScreen) return;
        if (modules.ui().hideInF3.on() && Compat.debugHudOpen(mc)) return;
        for (HudModule h : modules.hud()) if (h.isEnabled()) h.render(ctx, sw, sh, false);
    }
}
