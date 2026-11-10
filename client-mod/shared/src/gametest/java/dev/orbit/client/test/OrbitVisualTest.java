package dev.orbit.client.test;

import dev.orbit.client.OrbitClient;
import dev.orbit.client.emote.Emote;
import dev.orbit.client.emote.Emotes;
import dev.orbit.client.gui.CrosshairEditorScreen;
import dev.orbit.client.gui.EmoteWheelScreen;
import dev.orbit.client.gui.HudEditorScreen;
import dev.orbit.client.gui.OrbitScreen;
import dev.orbit.client.gui.OrbitServersScreen;
import dev.orbit.client.gui.OrbitTitleScreen;
import dev.orbit.client.gui.OrbitWorldsScreen;
import dev.orbit.client.module.Module;
import dev.orbit.client.module.NewModules;
import dev.orbit.client.sky.SkyPreset;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.option.Perspective;

/** Boots a world and screenshots each Orbit feature (build/run/clientGameTest/screenshots). */
public class OrbitVisualTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext ctx) {
        String cos = System.getenv("ORBIT_TEST_COSMETICS");
        if (cos != null) System.setProperty("orbit.cosmetics", cos);
        if ("cosmetics".equals(System.getenv("ORBIT_TEST_ONLY"))) { cosmeticsOnly(ctx); return; }
        if ("menuscale".equals(System.getenv("ORBIT_TEST_ONLY"))) { menuScaleOnly(ctx); return; }
        if ("death".equals(System.getenv("ORBIT_TEST_ONLY"))) { deathOnly(ctx); return; }

        ctx.waitTicks(16);
        ctx.takeScreenshot("00-intro-a");
        ctx.waitTicks(14);
        ctx.takeScreenshot("00-intro-b");
        ctx.waitTicks(40);
        ctx.takeScreenshot("00-title");
        ctx.runOnClient(c -> { if (c.currentScreen instanceof OrbitTitleScreen t) t.testAppearance(true); });
        ctx.waitTicks(8);
        ctx.takeScreenshot("00-title-appearance");
        ctx.runOnClient(c -> { if (c.currentScreen instanceof OrbitTitleScreen t) t.testAppearance(false); });
        ctx.setScreen(() -> new net.minecraft.client.gui.screen.option.OptionsScreen(new OrbitTitleScreen(), net.minecraft.client.MinecraftClient.getInstance().options));
        ctx.waitTicks(15);
        ctx.takeScreenshot("00-options-menu");
        // no friends yet + "Online" filter: one clear message, nothing drawn on top of each other
        ctx.setScreen(() -> OrbitTitleScreen.onFriends(1));
        ctx.waitTicks(20);
        ctx.takeScreenshot("00-title-friends-empty-online");
        ctx.setScreen(OrbitTitleScreen::new);
        ctx.waitTicks(5);
        ctx.runOnClient(c -> {
            dev.orbit.client.friends.Friends.addDirect("069a79f4-44e9-4726-a5be-fca90e38aaf5", "Notch");
            dev.orbit.client.friends.Friends.onDm("853c80ef-3c37-49fd-aa49-938b674adae6", "jeb_", "hey, want to play bedwars later?", 0);
        });
        for (String t : new String[]{"locker", "shots", "accounts", "friends"}) {
            ctx.setScreen(() -> OrbitTitleScreen.onTab(t));
            ctx.waitTicks(20);
            ctx.takeScreenshot("00-title-" + t);
        }
        // an open chat with a few messages (grouped bubbles, times, avatar)
        ctx.runOnClient(c -> {
            var chat = dev.orbit.client.friends.Friends.chat("069a79f4-44e9-4726-a5be-fca90e38aaf5");
            long now = System.currentTimeMillis();
            chat.add(new dev.orbit.client.friends.Friends.Msg(false, "yo are you on tonight?", now - 600_000));
            chat.add(new dev.orbit.client.friends.Friends.Msg(false, "got a new cape btw", now - 590_000));
            chat.add(new dev.orbit.client.friends.Friends.Msg(true, "yeah in like 10 min", now - 120_000));
            chat.add(new dev.orbit.client.friends.Friends.Msg(true, "join eu.mcpvp.club, I'll be in duels", now - 100_000));
        });
        ctx.setScreen(() -> OrbitTitleScreen.onFriend("069a79f4-44e9-4726-a5be-fca90e38aaf5"));
        ctx.waitTicks(20);
        ctx.takeScreenshot("00-title-friends-chat");
        // Friends 2.0: Notch is online on a server (Join button), and invites us (Join inside the bubble)
        ctx.runOnClient(c -> {
            dev.orbit.client.friends.Friends.onOnline(java.util.List.of("069a79f4-44e9-4726-a5be-fca90e38aaf5"), java.util.Map.of("069a79f4-44e9-4726-a5be-fca90e38aaf5", "eu.mcpvp.club"));
            dev.orbit.client.friends.Friends.onInvite("069a79f4-44e9-4726-a5be-fca90e38aaf5", "Notch", "eu.mcpvp.club", System.currentTimeMillis());
        });
        ctx.setScreen(() -> OrbitTitleScreen.onFriend("069a79f4-44e9-4726-a5be-fca90e38aaf5"));
        ctx.waitTicks(20);
        ctx.takeScreenshot("00-title-friends-join");
        // locker preview: a hat + wings shown on the 3D model (turned round), then the cosmetics tiles
        ctx.setScreen(() -> OrbitTitleScreen.onTab("locker"));
        ctx.runOnClient(c -> { dev.orbit.client.cosmetics.CosmeticsFeature.previewHat = "Top Hat"; dev.orbit.client.cosmetics.CosmeticsFeature.previewWings = "Butterfly"; dev.orbit.client.cosmetics.CosmeticsFeature.previewAura = "Flames"; });
        ctx.waitTicks(20);
        ctx.takeScreenshot("00-locker-preview");
        ctx.runOnClient(c -> { dev.orbit.client.cosmetics.CosmeticsFeature.previewHat = null; dev.orbit.client.cosmetics.CosmeticsFeature.previewWings = null; dev.orbit.client.cosmetics.CosmeticsFeature.previewAura = null;
            OrbitTitleScreen.testCape = net.minecraft.util.Identifier.ofVanilla("textures/block/diamond_block.png"); });
        ctx.setScreen(() -> OrbitTitleScreen.onTab("locker"));
        ctx.waitTicks(10);
        ctx.takeScreenshot("00-locker-cape");
        ctx.runOnClient(c -> OrbitTitleScreen.testCape = null);
        ctx.setScreen(() -> OrbitTitleScreen.onTab("cosmetics"));
        ctx.waitTicks(20);
        ctx.takeScreenshot("00-locker-cosmetics");
        ctx.setScreen(() -> OrbitTitleScreen.onTab("emotes"));
        ctx.waitTicks(20);
        ctx.takeScreenshot("00-locker-emotes");
        ctx.setScreen(() -> OrbitTitleScreen.onTab("home"));
        ctx.waitTicks(5);
        // classic title screen must offer a way back to Orbit's menu
        ctx.runOnClient(c -> OrbitTitleScreen.allowVanillaOnce = true);
        ctx.setScreen(net.minecraft.client.gui.screen.TitleScreen::new);
        ctx.waitTicks(20);
        ctx.takeScreenshot("00z-classic-title");
        ctx.setScreen(() -> new OrbitWorldsScreen(new OrbitTitleScreen()));
        ctx.waitTicks(25);
        ctx.takeScreenshot("01-worlds");
        ctx.setScreen(() -> new OrbitServersScreen(new OrbitTitleScreen()));
        ctx.waitTicks(30);
        ctx.takeScreenshot("02-servers");
        // paste into "Add server" with Ctrl+V
        ctx.runOnClient(c -> { c.keyboard.setClipboard("play.example.net\r\n"); ((OrbitServersScreen) c.currentScreen).testOpenForm(false); });
        ctx.waitTicks(2);
        ctx.getInput().holdKey(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL);
        ctx.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_V);
        ctx.getInput().releaseKey(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL);
        ctx.waitTicks(3);
        ctx.runOnClient(c -> OrbitClient.LOG.info("[test] pasted address = '{}'", ((OrbitServersScreen) c.currentScreen).testAddress()));
        ctx.takeScreenshot("02b-servers-paste");
        ctx.setScreen(OrbitTitleScreen::new);
        ctx.waitTicks(5);

        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getClientWorld().waitForChunksRender();
            for (String c : new String[]{"time set 1000", "weather clear", "effect give @a minecraft:speed 300 1",
                    "scoreboard objectives add orbit dummy \"Orbit Test\"", "scoreboard objectives setdisplay sidebar orbit", "scoreboard players set Commander orbit 42", "scoreboard players set Pilot orbit 7"})
                sp.getServer().runCommand(c);
            ctx.runOnClient(c -> {
                c.player.setPitch(0);
                var m = OrbitClient.modules();
                m.crosshair().setEnabled(true);
                m.crosshair().style.set("Custom");
                var sb = (NewModules.Scoreboard) m.get("scoreboard");
                sb.setEnabled(true); sb.scale.set(1.3); sb.bgOpacity.set(70.0);
                m.get("potions").setEnabled(true);
                m.get("saturation").setEnabled(true);
            });
            ctx.waitTicks(40);
            ctx.takeScreenshot("03-hud");

            ctx.setScreen(() -> new OrbitScreen(null).tab(Module.Category.RENDER));
            ctx.waitTicks(20);
            ctx.takeScreenshot("04-menu-render");
            ctx.setScreen(() -> new OrbitScreen(null).tab(Module.Category.COMBAT));
            ctx.waitTicks(20);
            ctx.takeScreenshot("05-menu-combat");
            ctx.setScreen(() -> { OrbitScreen s = new OrbitScreen(null); s.open(OrbitClient.modules().crosshair()); return s; });
            ctx.waitTicks(20);
            ctx.takeScreenshot("06-crosshair-settings");
            ctx.setScreen(() -> new CrosshairEditorScreen(null, OrbitClient.modules().crosshair()));
            ctx.waitTicks(15);
            ctx.takeScreenshot("07-crosshair-painter");
            ctx.setScreen(EmoteWheelScreen::new);
            ctx.waitTicks(15);
            ctx.takeScreenshot("08-emote-wheel");
            ctx.waitTicks(12);
            ctx.takeScreenshot("08b-emote-wheel-later");
            ctx.setScreen(() -> new net.minecraft.client.gui.screen.GameMenuScreen(true)); // Esc -> Orbit pause menu
            ctx.waitTicks(20);
            ctx.takeScreenshot("08c-pause-menu");
            ctx.runOnClient(c -> OrbitTitleScreen.allowVanillaPause = true);
            ctx.setScreen(() -> new net.minecraft.client.gui.screen.GameMenuScreen(true));
            ctx.waitTicks(10);
            ctx.takeScreenshot("08c2-classic-pause");
            ctx.setScreen(() -> new net.minecraft.client.gui.screen.option.OptionsScreen(OrbitTitleScreen.pause(), net.minecraft.client.MinecraftClient.getInstance().options));
            ctx.waitTicks(15);
            ctx.takeScreenshot("08e-options-styled");
            ctx.runOnClient(c -> dev.orbit.client.module.Configs.save("Test PvP"));
            ctx.setScreen(() -> new dev.orbit.client.gui.ConfigsScreen(null));
            ctx.waitTicks(15);
            ctx.takeScreenshot("08f-configs");
            ctx.runOnClient(c -> OrbitClient.LOG.info("[test] pause screen = {}", c.currentScreen == null ? null : c.currentScreen.getClass().getSimpleName()));
            ctx.setScreen(() -> OrbitTitleScreen.pauseOn("emotes"));
            ctx.waitTicks(20);
            ctx.takeScreenshot("08d-pause-locker-emotes");
            ctx.setScreen(() -> null);

            ctx.runOnClient(c -> { OrbitClient.modules().get("nametag").setEnabled(true); Emotes.playLocal(Emote.CHEER); });
            ctx.waitTicks(20);
            ctx.takeScreenshot("09-emote-nametag");
            ctx.waitTicks(60);

            // cosmetics (crown + angel wings + stardust aura) from behind, then the Tab list badge
            ctx.runOnClient(c -> {
                var cm = (NewModules.Cosmetics) OrbitClient.modules().get("cosmetics");
                cm.setEnabled(true); cm.hat.set("Crown"); cm.wings.set("Angel"); cm.aura.set("Stardust");
                c.options.setPerspective(Perspective.THIRD_PERSON_BACK);
                c.player.setPitch(10);
            });
            ctx.waitTicks(30);
            ctx.takeScreenshot("09b-cosmetics-back");
            ctx.waitTicks(3);
            ctx.takeScreenshot("09b2-wings-standing");
            var fwd = ctx.computeOnClient(c -> c.options.forwardKey);
            var sprint = ctx.computeOnClient(c -> c.options.sprintKey);
            ctx.getInput().holdKey(fwd);
            ctx.waitTicks(15);
            ctx.takeScreenshot("09b2-wings-walking");
            ctx.waitTicks(3);
            ctx.takeScreenshot("09b2-wings-walking-b");
            ctx.waitTicks(6);
            ctx.takeScreenshot("09b2-wings-walking2");
            ctx.getInput().holdKey(sprint);
            ctx.waitTicks(15);
            ctx.takeScreenshot("09b2-wings-sprinting");
            ctx.getInput().releaseKey(sprint);
            ctx.getInput().releaseKey(fwd);
            ctx.waitTicks(20);
            ctx.runOnClient(c -> ((NewModules.Cosmetics) OrbitClient.modules().get("cosmetics")).wings.set("Butterfly"));
            ctx.waitTicks(5);
            ctx.takeScreenshot("09b3-wings-butterfly");
            ctx.runOnClient(c -> { var cm = (NewModules.Cosmetics) OrbitClient.modules().get("cosmetics"); cm.hat.set("Halo"); cm.wings.set("Demon"); c.options.setPerspective(Perspective.THIRD_PERSON_FRONT); });
            ctx.waitTicks(20);
            ctx.takeScreenshot("09c-cosmetics-front");
            var tabKey = ctx.computeOnClient(c -> c.options.playerListKey);
            ctx.getInput().holdKey(tabKey);
            ctx.waitTicks(5);
            ctx.takeScreenshot("09d-tab-badge");
            ctx.getInput().releaseKey(tabKey);
            ctx.runOnClient(c -> { var cm = (NewModules.Cosmetics) OrbitClient.modules().get("cosmetics"); cm.hat.set("None"); cm.wings.set("None"); cm.aura.set("None"); });

            ctx.runOnClient(c -> {
                c.options.setPerspective(Perspective.FIRST_PERSON);
                var m = OrbitClient.modules();
                m.get("motionblur").setEnabled(true);
                m.get("nofog").setEnabled(true);
                m.get("time").setEnabled(true);
            });
            for (int i = 0; i < 12; i++) { ctx.runOnClient(c -> c.player.setYaw(c.player.getYaw() + 6)); ctx.waitTick(); }
            ctx.runOnClient(c -> c.player.setYaw(c.player.getYaw() + 25)); // turn right before the shot so trails show
            ctx.takeScreenshot("10-motionblur-time-nofog");
            ctx.runOnClient(c -> { OrbitClient.modules().get("motionblur").setEnabled(false); OrbitClient.modules().get("time").setEnabled(false); });

            ctx.getInput().holdKey(OrbitClient.freelookKey);
            ctx.waitTicks(3);
            ctx.runOnClient(c -> OrbitClient.modules().freelook().turn(900, 0));
            ctx.waitTicks(10);
            ctx.runOnClient(c -> OrbitClient.LOG.info("[test] freelook camera yaw={} player yaw={}", c.gameRenderer.getCamera().getYaw(), c.player.getYaw()));
            ctx.takeScreenshot("11-freelook");
            ctx.getInput().releaseKey(OrbitClient.freelookKey);
            ctx.waitTicks(5);

            // AppleSkin-style saturation: hungry, holding food
            sp.getServer().runCommand("effect give @a minecraft:hunger 4 120");
            sp.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:cooked_beef 16");
            ctx.waitTicks(90);
            ctx.runOnClient(c -> c.player.setPitch(10));
            ctx.waitTicks(5);
            ctx.takeScreenshot("11b-saturation");

            // chunk borders, TNT timer, shield status
            ctx.runOnClient(c -> {
                var m = OrbitClient.modules();
                m.get("chunkborders").setEnabled(true); m.get("tnttimer").setEnabled(true); m.get("shield").setEnabled(true);
                c.player.setPitch(15);
            });
            sp.getServer().runCommand("item replace entity @a weapon.offhand with minecraft:shield");
            ctx.runOnClient(c -> c.player.getItemCooldownManager().set(new net.minecraft.item.ItemStack(net.minecraft.item.Items.SHIELD), 100));
            sp.getServer().runCommand("execute as @a at @s run summon minecraft:tnt ^ ^ ^4 {fuse:100}");
            ctx.waitTicks(20);
            ctx.runOnClient(c -> OrbitClient.LOG.info("[test] chunk borders visible={} f3={}", c.debugHudEntryList.isEntryVisible(net.minecraft.client.gui.hud.debug.DebugHudEntries.CHUNK_BORDERS), c.debugHudEntryList.isF3Enabled()));
            ctx.takeScreenshot("11e-tnt-chunks-shield");
            ctx.runOnClient(c -> OrbitClient.modules().get("chunkborders").setEnabled(false));
            ctx.waitTicks(90); // let the TNT go off before the next shots

            // Spotify card with chat open + sky lyrics
            ctx.runOnClient(c -> {
                long now = 61_200;
                dev.orbit.client.spotify.SpotifyLyrics.fakeForTest("Пачка сигарет", "Кино", "Звезда по имени Солнце", 266_000, now, java.util.List.of(
                        new dev.orbit.client.spotify.SpotifyLyrics.Line(58_000, "Я сижу и смотрю в чужое небо"),
                        new dev.orbit.client.spotify.SpotifyLyrics.Line(62_500, "Из чужого окна"),
                        new dev.orbit.client.spotify.SpotifyLyrics.Line(68_000, "И не вижу ни одной знакомой звезды")));
                OrbitClient.modules().get("spotify").setEnabled(true);
                OrbitClient.modules().get("lyrics").setEnabled(true);
                c.player.setPitch(-20);
            });
            ctx.waitTicks(30);
            ctx.setScreen(() -> dev.orbit.client.compat.Compat.chat(""));
            ctx.waitTicks(10);
            ctx.takeScreenshot("11c-spotify-chat-lyrics");
            ctx.setScreen(() -> null);
            ctx.runOnClient(c -> c.player.setYaw(c.player.getYaw() + 25)); // lyrics should stay put in the sky
            ctx.waitTicks(5);
            ctx.takeScreenshot("11d-lyrics-turned");
            ctx.runOnClient(c -> OrbitClient.modules().get("lyrics").setEnabled(false));

            ctx.runOnClient(c -> { var s = OrbitClient.modules().sky(); s.preset.set(SkyPreset.NEBULA.title); s.setEnabled(true); c.player.setPitch(-28); });
            ctx.waitTicks(80);
            ctx.takeScreenshot("12-sky");
            ctx.runOnClient(c -> { OrbitClient.modules().sky().setEnabled(false); OrbitClient.modules().get("spotify").setEnabled(true); });

            // waypoints + in-game clock, dropped items scaled up, armor hidden
            sp.getServer().runCommand("item replace entity @a armor.chest with minecraft:diamond_chestplate");
            sp.getServer().runCommand("item replace entity @a armor.head with minecraft:golden_helmet");
            ctx.runOnClient(c -> {
                var m = OrbitClient.modules();
                m.get("gametime").setEnabled(true); m.waypoints().setEnabled(true);
                var w = dev.orbit.client.waypoints.Waypoints.addHere(); w.x += 30; w.z += 10; w.name = "Base";
                var w2 = dev.orbit.client.waypoints.Waypoints.addHere(); w2.x -= 40; w2.z += 60; w2.y += 8; w2.name = "Mine";
                var is = (NewModules.ItemScale) m.get("itemscale"); is.setEnabled(true); is.droppedSize.set(2.5); is.spin.set(false); is.bob.set(false); is.held.set(true); is.heldSize.set(0.5);
                c.player.setPitch(20);
            });
            sp.getServer().runCommand("execute as @a at @s run summon minecraft:item ^ ^ ^3 {Item:{id:\"minecraft:diamond\",count:1},PickupDelay:32767}");
            ctx.waitTicks(20);
            ctx.takeScreenshot("14-waypoints-clock-itemscale");
            ctx.runOnClient(c -> { OrbitClient.modules().get("itemphysics").setEnabled(true); OrbitClient.modules().get("hitboxes").setEnabled(true); OrbitClient.modules().get("totems").setEnabled(true);
                var w = (NewModules.WeatherChanger) OrbitClient.modules().get("weather"); w.setEnabled(true); w.weather.set("Snow"); });
            sp.getServer().runCommand("effect give @a minecraft:strength 60 1");
            sp.getServer().runCommand("give @p minecraft:totem_of_undying 3");
            sp.getServer().runCommand("execute as @p at @s run summon minecraft:item ^1 ^ ^2 {Item:{id:\"minecraft:golden_apple\",count:1},PickupDelay:32767}");
            sp.getServer().runCommand("execute as @p at @s run summon minecraft:cow ^ ^ ^5 {NoAI:1b}");
            ctx.waitTicks(40);
            ctx.runOnClient(c -> OrbitClient.LOG.info("[test] hitboxes visible={} enabled={}", c.debugHudEntryList.isEntryVisible(net.minecraft.client.gui.hud.debug.DebugHudEntries.ENTITY_HITBOXES), OrbitClient.modules().get("hitboxes").isEnabled()));
            ctx.takeScreenshot("14d-physics-hitbox-snow-potions");
            ctx.runOnClient(c -> OrbitClient.modules().get("weather").setEnabled(false));
            ctx.waitTicks(100); // toasts gone
            ctx.takeScreenshot("14e-hitbox-potions-clear");
            ctx.runOnClient(c -> OrbitClient.modules().get("hitboxes").setEnabled(false));
            ctx.runOnClient(c -> { var w3 = dev.orbit.client.waypoints.Waypoints.addHere(); w3.z -= 80; w3.name = "Behind you"; c.player.setPitch(5); });
            ctx.waitTicks(5);
            ctx.takeScreenshot("14b-waypoints-arrows");
            ctx.setScreen(() -> new dev.orbit.client.gui.WaypointsScreen(null));
            ctx.waitTicks(15);
            ctx.takeScreenshot("14c-waypoints-screen");
            ctx.setScreen(() -> null);
            ctx.runOnClient(c -> { c.options.setPerspective(Perspective.THIRD_PERSON_FRONT); c.player.setPitch(10); });
            ctx.waitTicks(10);
            ctx.takeScreenshot("15-armor-shown");
            ctx.runOnClient(c -> OrbitClient.modules().get("hidearmor").setEnabled(true));
            ctx.waitTicks(5);
            ctx.takeScreenshot("16-armor-hidden");
            ctx.runOnClient(c -> c.options.setPerspective(Perspective.FIRST_PERSON));

            // Troll Mod on yourself (third person): disguise, then invisible with name tag
            ctx.runOnClient(c -> {
                var tm = OrbitClient.modules().get("troll"); tm.setEnabled(true);
                ((NewModules.TrollModule) tm).self.set(true);
                dev.orbit.client.troll.Troll.set(c.player.getGameProfile().name(), dev.orbit.client.troll.Troll.Kind.DISGUISE, "minecraft:creeper");
                c.options.setPerspective(Perspective.THIRD_PERSON_FRONT); c.player.setPitch(15);
            });
            ctx.waitTicks(10);
            ctx.takeScreenshot("17-troll-creeper");
            ctx.runOnClient(c -> dev.orbit.client.troll.Troll.set(c.player.getGameProfile().name(), dev.orbit.client.troll.Troll.Kind.DISGUISE, "minecraft:iron_golem"));
            ctx.waitTicks(5);
            ctx.takeScreenshot("17b-troll-golem");
            ctx.runOnClient(c -> dev.orbit.client.troll.Troll.set(c.player.getGameProfile().name(), dev.orbit.client.troll.Troll.Kind.INVISIBLE, null));
            ctx.waitTicks(5);
            ctx.takeScreenshot("17c-troll-invisible");
            ctx.setScreen(() -> new dev.orbit.client.gui.TrollScreen(null));
            ctx.waitTicks(10);
            ctx.takeScreenshot("17d-troll-menu");
            ctx.setScreen(() -> null);
            // a server-style name tag (text display riding the player) should sit on the disguise's head
            ctx.runOnClient(c -> ((NewModules.TrollModule) OrbitClient.modules().get("troll")).self.set(false));
            sp.getServer().runCommand("execute as @p at @s run summon minecraft:text_display ~ ~ ~ {Tags:[\"nametag\"],billboard:\"center\",text:{text:\"Player0\n20 \u2764\",color:\"white\"}}");
            sp.getServer().runCommand("ride @e[type=minecraft:text_display,tag=nametag,limit=1] mount @p");
            ctx.runOnClient(c -> dev.orbit.client.troll.Troll.set(c.player.getGameProfile().name(), dev.orbit.client.troll.Troll.Kind.DISGUISE, "minecraft:pig"));
            ctx.waitTicks(10);
            ctx.takeScreenshot("17e-troll-pig-servertag");
            sp.getServer().runCommand("kill @e[type=minecraft:text_display]");
            ctx.runOnClient(c -> { dev.orbit.client.troll.Troll.clearAll(); c.options.setPerspective(Perspective.FIRST_PERSON); });

            // prop emotes, seen from the front-side in third person
            ctx.runOnClient(c -> { c.options.setPerspective(Perspective.THIRD_PERSON_FRONT); c.player.setPitch(20); });
            for (var em : new dev.orbit.client.emote.Emote[]{dev.orbit.client.emote.Emote.JOYRIDE, dev.orbit.client.emote.Emote.SUNBATHE, dev.orbit.client.emote.Emote.CAMPFIRE, dev.orbit.client.emote.Emote.DJ, dev.orbit.client.emote.Emote.HOVERBOARD, dev.orbit.client.emote.Emote.MONEY}) {
                ctx.runOnClient(c -> { c.player.setYaw(c.player.getYaw() + 35); Emotes.playLocal(em); c.options.setPerspective(Perspective.THIRD_PERSON_FRONT); });
                ctx.waitTicks(em == dev.orbit.client.emote.Emote.JOYRIDE ? 12 : 30);
                ctx.takeScreenshot("20-emote-" + em.name().toLowerCase());
                ctx.runOnClient(c -> Emotes.stop(c.player.getUuid()));
                ctx.waitTicks(5);
            }
            ctx.runOnClient(c -> c.options.setPerspective(Perspective.FIRST_PERSON));

            // PvP pack: target HUD, combo, hit delay, damage numbers, fight stats, timeline, trajectories, palette, designer
            ctx.runOnClient(c -> {
                var m = OrbitClient.modules();
                for (String id : new String[]{"targethud", "combo", "hitdelay", "damagenumbers", "fightstats", "timeline", "trajectories", "killeffects", "trails", "pearls", "wtap", "chatbubbles"}) m.get(id).setEnabled(true);
                c.options.setPerspective(Perspective.FIRST_PERSON);
                c.player.setPitch(5);
            });
            sp.getServer().runCommand("give @p minecraft:ender_pearl 8");
            sp.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:snowball 16");
            sp.getServer().runCommand("execute as @p at @s run summon minecraft:zombie ^ ^ ^2.5 {NoAI:1b,Tags:[\"dummy\"],Health:20f,ArmorItems:[{},{},{id:\"minecraft:iron_chestplate\",count:1},{id:\"minecraft:diamond_helmet\",count:1}]}");
            ctx.waitTicks(10);
            for (int k = 0; k < 3; k++) {
                ctx.runOnClient(c -> { var z = c.world.getEntitiesByClass(net.minecraft.entity.mob.ZombieEntity.class, c.player.getBoundingBox().expand(6), e -> true); if (!z.isEmpty()) c.interactionManager.attackEntity(c.player, z.get(0)); });
                ctx.waitTicks(12);
            }
            ctx.takeScreenshot("21-pvp-hud");
            ctx.setScreen(() -> new dev.orbit.client.gui.CommandPaletteScreen(null));
            ctx.waitTicks(10);
            ctx.takeScreenshot("21b-command-palette");
            ctx.setScreen(() -> new HudEditorScreen(null));
            ctx.waitTicks(10);
            ctx.takeScreenshot("21c-hud-designer");
            ctx.setScreen(() -> null);
            sp.getServer().runCommand("kill @e[type=minecraft:zombie,tag=dummy]");

            // HUD themes: Neon keystrokes, Glass CPS, Pill coordinates + friend pop-ups
            ctx.runOnClient(c -> {
                var m = OrbitClient.modules();
                for (String id : new String[]{"targethud", "combo", "hitdelay", "fightstats", "timeline", "trajectories", "pearls", "wtap"}) m.get(id).setEnabled(false);
                ((dev.orbit.client.module.HudModule) m.get("keystrokes")).theme.set("Neon");
                ((dev.orbit.client.module.HudModule) m.get("cps")).theme.set("Glass");
                ((dev.orbit.client.module.HudModule) m.get("coords")).theme.set("Pill");
                dev.orbit.client.friends.FriendToasts.show("Notch", "Notch is online", "Playing on eu.mcpvp.club", false);
                dev.orbit.client.friends.FriendToasts.show("jeb_", "jeb_ invited you", "Play on hypixel.net · join in Friends", true);
            });
            ctx.waitTicks(25);
            ctx.takeScreenshot("21d-hud-themes-toasts");
            ctx.runOnClient(c -> { var m = OrbitClient.modules(); for (String id : new String[]{"keystrokes", "cps", "coords"}) ((dev.orbit.client.module.HudModule) m.get(id)).theme.set("Default"); });

            // Clips: record a few seconds at 60 fps, save, check the video file, then watch it in the Gallery
            ctx.runOnClient(c -> {
                var cm = (dev.orbit.client.module.ClipsModule) OrbitClient.modules().get("clips");
                cm.rate.set("60 fps");
                cm.quality.set("1080p");
                cm.setEnabled(true);
                c.options.getMaxFps().setValue(260); // let the game itself run fast enough
                c.getWindow().setWindowedSize(1920, 1080); // record at a real 1080p, like a normal full-screen game
            });
            ctx.waitTicks(20);
            ctx.runOnClient(c -> OrbitClient.LOG.info("[TEST] clip window: {}x{}", c.getFramebuffer().textureWidth, c.getFramebuffer().textureHeight));
            for (int k = 0; k < 8; k++) { ctx.runOnClient(c -> c.player.setYaw(c.player.getYaw() + 12)); ctx.waitTicks(10); }
            ctx.runOnClient(c -> { OrbitClient.LOG.info("[TEST] clip game fps while recording: {}", c.getCurrentFps()); dev.orbit.client.clips.ClipRecorder.saveClip(); });
            ctx.waitTicks(60);
            ctx.runOnClient(c -> {
                java.io.File[] clips = new java.io.File(c.runDirectory, "clips").listFiles();
                if (clips == null || clips.length == 0) OrbitClient.LOG.warn("[TEST] clip: NO FILE");
                else for (java.io.File f : clips) {
                    try { var inf = dev.orbit.client.clips.ClipPlayer.read(f); OrbitClient.LOG.info("[TEST] clip: {} {} bytes, {} frames, {} fps, {}x{}", f.getName(), f.length(), inf.frames(), Math.round(inf.fps()), inf.width(), inf.height()); }
                    catch (Exception e) { OrbitClient.LOG.warn("[TEST] clip unreadable", e); }
                }
                OrbitClient.modules().get("clips").setEnabled(false);
                c.getWindow().setWindowedSize(854, 480);
            });
            ctx.waitTicks(10);
            ctx.takeScreenshot("21e-clip-saved");
            ctx.setScreen(() -> OrbitTitleScreen.pauseOn("clips"));
            ctx.waitTicks(30);
            ctx.takeScreenshot("21f-gallery-clips");
            ctx.runOnClient(c -> {
                java.io.File[] fs = new java.io.File(c.runDirectory, "clips").listFiles();
                if (fs != null && fs.length > 0 && c.currentScreen instanceof OrbitTitleScreen ts) ts.playClip(fs[0]);
            });
            ctx.waitTicks(30);
            ctx.takeScreenshot("21g-clip-player");
            ctx.waitTicks(60);
            ctx.runOnClient(c -> { if (c.currentScreen instanceof OrbitTitleScreen ts) OrbitClient.LOG.info("[TEST] clip playback: {} fps shown in-game (game {} FPS)", Math.round(ts.clipPlaybackFps()), c.getCurrentFps()); });
            ctx.setScreen(() -> null);
            ctx.runOnClient(c -> { var m = OrbitClient.modules(); for (String id : new String[]{"targethud", "combo", "hitdelay", "fightstats", "timeline", "trajectories", "pearls", "wtap"}) m.get(id).setEnabled(false); });
            // Death Animations on zombies, one per style
            ctx.runOnClient(c -> { OrbitClient.modules().get("deathanim").setEnabled(true); c.player.setPitch(10); });
            String[] styles = {"Soul Ascend", "Black Hole", "Thunder Strike", "Firework", "Shatter", "Tung Tung Bonk"};
            for (int i = 0; i < styles.length; i++) {
                String st = styles[i];
                ctx.runOnClient(c -> ((NewModules.DeathAnimationsModule) OrbitClient.modules().get("deathanim")).style.set(st));
                sp.getServer().runCommand("execute as @p at @s run summon minecraft:zombie ^ ^ ^4 {NoAI:1b,Tags:[\"victim\"]}");
                ctx.waitTicks(5);
                sp.getServer().runCommand("kill @e[type=minecraft:zombie,tag=victim]");
                ctx.waitTicks(st.equals("Firework") ? 9 : st.equals("Shatter") ? 6 : st.startsWith("Tung") ? 7 : 10);
                ctx.takeScreenshot("19-death-" + i + "-" + st.replace(" ", ""));
                if (st.startsWith("Tung")) { ctx.waitTicks(7); ctx.takeScreenshot("19-death-" + i + "b-bonk"); }
                ctx.waitTicks(30);
            }

            // No Fog in the Nether
            sp.getServer().runCommand("execute in minecraft:the_nether run fill -20 99 -20 20 99 20 minecraft:obsidian");
            sp.getServer().runCommand("execute in minecraft:the_nether run fill -20 100 -20 20 115 20 minecraft:air");
            sp.getServer().runCommand("execute in minecraft:the_nether run tp @a 0 100 0 0 0");
            ctx.waitTicks(60);
            sp.getClientWorld().waitForChunksRender();
            ctx.runOnClient(c -> { OrbitClient.modules().get("nofog").setEnabled(true); OrbitClient.modules().sky().setEnabled(false); c.player.setPitch(0); });
            ctx.waitTicks(20);
            ctx.takeScreenshot("18-nether-nofog");
            ctx.runOnClient(c -> OrbitClient.modules().get("nofog").setEnabled(false));
            ctx.waitTicks(20);
            ctx.takeScreenshot("18b-nether-fog");
            ctx.runOnClient(c -> OrbitClient.modules().get("nofog").setEnabled(true));

            ctx.setScreen(() -> new HudEditorScreen(null));            ctx.waitTicks(15);
            ctx.takeScreenshot("13-hud-editor");
            ctx.setScreen(() -> null);
            OrbitTitleScreen.allowVanillaOnce = true; // the harness requires ending on vanilla's title screen
        }
    }

    /** quick run (ORBIT_TEST_ONLY=death): the cartoon death animations on zombies, several frames each */
    private static void deathOnly(ClientGameTestContext ctx) {
        ctx.waitTicks(40);
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getClientWorld().waitForChunksRender();
            sp.getServer().runCommand("time set 1000");
            sp.getServer().runCommand("weather clear");
            ctx.runOnClient(c -> { OrbitClient.modules().get("deathanim").setEnabled(true); c.player.setPitch(8); });
            ctx.waitTicks(10);
            String[] styles = {"Piano Drop", "Bowling Strike", "UFO Abduction", "Tung Tung Bonk"};
            int[][] shots = {{5, 9, 12, 22, 34}, {4, 8, 11, 16, 26}, {3, 8, 13, 18, 26}, {3, 8, 12, 14, 17}};
            for (int i = 0; i < styles.length; i++) {
                String st = styles[i];
                ctx.runOnClient(c -> ((NewModules.DeathAnimationsModule) OrbitClient.modules().get("deathanim")).style.set(st));
                sp.getServer().runCommand("execute as @p at @s run summon minecraft:zombie ^ ^ ^3.5 {NoAI:1b,Tags:[\"victim\"],equipment:{head:{id:\"minecraft:leather_helmet\",count:1}}}");
                ctx.waitTicks(8);
                sp.getServer().runCommand("kill @e[type=minecraft:zombie,tag=victim]");
                int last = 0;
                for (int k : shots[i]) { ctx.waitTicks(k - last); last = k; ctx.takeScreenshot("d-" + i + "-" + st.replace(" ", "") + "-t" + k); }
                ctx.waitTicks(40);
            }
            OrbitTitleScreen.allowVanillaOnce = true;
        }
    }

    /** quick run (ORBIT_TEST_ONLY=menuscale): Orbit menus at 1080p with GUI scale 4, drawn like GUI 3 (and "Same as Minecraft") */
    private static void menuScaleOnly(ClientGameTestContext ctx) {
        ctx.waitTicks(90); // intro
        ctx.runOnClient(c -> { c.getWindow().setWindowedSize(1920, 1080); c.options.getGuiScale().setValue(4); c.onResolutionChanged(); });
        ctx.waitTicks(30);
        for (String size : new String[]{"Normal (like GUI 3)", "Same as Minecraft"}) {
            String tag = size.startsWith("Normal") ? "gui3" : "gui4";
            ctx.runOnClient(c -> OrbitClient.modules().ui().menuSize.set(size));
            ctx.setScreen(OrbitTitleScreen::new);
            ctx.waitTicks(30);
            ctx.takeScreenshot("m-" + tag + "-title");
            ctx.setScreen(() -> OrbitTitleScreen.onTab("cosmetics"));
            ctx.waitTicks(25);
            ctx.takeScreenshot("m-" + tag + "-locker");
            ctx.setScreen(() -> new OrbitScreen(null).tab(Module.Category.RENDER));
            ctx.waitTicks(25);
            ctx.takeScreenshot("m-" + tag + "-menu");
        }
        // mouse check at GUI-3 size: hover + click the 2nd tab ("Combat") using the real cursor
        ctx.runOnClient(c -> OrbitClient.modules().ui().menuSize.set("Normal (like GUI 3)"));
        ctx.setScreen(() -> new OrbitScreen(null).tab(Module.Category.RENDER));
        ctx.waitTicks(20);
        ctx.runOnClient(c -> OrbitClient.LOG.info("[TEST] menu scale {} screen {}x{} window scale {}", dev.orbit.client.gui.OrbitBaseScreen.uiScale(), c.currentScreen.width, c.currentScreen.height, c.getWindow().getScaleFactor()));
        ctx.getInput().setCursorPos(825, 70); // the "Combat" tab in the 1920x1080 screenshot
        ctx.waitTicks(2);
        ctx.getInput().pressMouse(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT);
        ctx.waitTicks(15);
        ctx.takeScreenshot("m-click-combat");
        ctx.runOnClient(c -> OrbitClient.modules().ui().menuSize.set("Normal (like GUI 3)"));
        ctx.setScreen(OrbitTitleScreen::new);
        ctx.waitTicks(5);
        ctx.runOnClient(c -> { c.options.getGuiScale().setValue(0); c.getWindow().setWindowedSize(854, 480); c.onResolutionChanged(); });
        ctx.waitTicks(10);
        ctx.runOnClient(c -> OrbitTitleScreen.allowVanillaOnce = true);
        ctx.setScreen(net.minecraft.client.gui.screen.TitleScreen::new);
        ctx.waitTicks(5);
    }

    /** quick run (ORBIT_TEST_ONLY=cosmetics): the Locker's cosmetics pages and every pet / new hat / wing / aura in a world */
    private static void cosmeticsOnly(ClientGameTestContext ctx) {
        ctx.waitTicks(40);
        for (int g = 0; g < 4; g++) {
            int gg = g;
            ctx.runOnClient(c -> OrbitTitleScreen.testCosGroup(gg));
            ctx.setScreen(() -> OrbitTitleScreen.onTab("cosmetics"));
            ctx.waitTicks(25);
            ctx.takeScreenshot("c0-locker-" + g);
        }
        ctx.runOnClient(c -> OrbitTitleScreen.testHover = "Fox");
        ctx.waitTicks(10);
        ctx.takeScreenshot("c0-locker-preview-fox");
        ctx.runOnClient(c -> OrbitTitleScreen.testHover = "Parrot");
        ctx.waitTicks(10);
        ctx.takeScreenshot("c0-locker-preview-parrot");
        ctx.runOnClient(c -> OrbitTitleScreen.testHover = null);
        ctx.setScreen(OrbitTitleScreen::new);
        ctx.waitTicks(5);
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getClientWorld().waitForChunksRender();
            sp.getServer().runCommand("time set 1000");
            sp.getServer().runCommand("weather clear");
            ctx.runOnClient(c -> { var cm = (NewModules.Cosmetics) OrbitClient.modules().get("cosmetics"); cm.setEnabled(true); c.options.setPerspective(Perspective.THIRD_PERSON_FRONT); c.player.setPitch(12); });
            ctx.waitTicks(20);
            String[] pets = NewModules.Cosmetics.PETS, hats = NewModules.Cosmetics.HATS, wings = NewModules.Cosmetics.WINGS, auras = NewModules.Cosmetics.AURAS;
            for (int i = 1; i < pets.length; i++) {
                String p = pets[i];
                ctx.runOnClient(c -> ((NewModules.Cosmetics) OrbitClient.modules().get("cosmetics")).pet.set(p));
                ctx.waitTicks(4);
                ctx.takeScreenshot("c1-pet-" + i + "-" + p.replace(" ", ""));
            }
            ctx.runOnClient(c -> ((NewModules.Cosmetics) OrbitClient.modules().get("cosmetics")).pet.set("None"));
            for (int i = 7; i < hats.length; i++) {
                String h = hats[i];
                ctx.runOnClient(c -> ((NewModules.Cosmetics) OrbitClient.modules().get("cosmetics")).hat.set(h));
                ctx.waitTicks(3);
                ctx.takeScreenshot("c2-hat-" + i + "-" + h.replace(" ", ""));
            }
            ctx.runOnClient(c -> { var cm = (NewModules.Cosmetics) OrbitClient.modules().get("cosmetics"); cm.hat.set("None"); c.options.setPerspective(Perspective.THIRD_PERSON_BACK); });
            for (int i = 5; i < wings.length; i++) {
                String w = wings[i];
                ctx.runOnClient(c -> ((NewModules.Cosmetics) OrbitClient.modules().get("cosmetics")).wings.set(w));
                ctx.waitTicks(4);
                ctx.takeScreenshot("c3-wings-" + i + "-" + w);
            }
            ctx.runOnClient(c -> { var cm = (NewModules.Cosmetics) OrbitClient.modules().get("cosmetics"); cm.wings.set("None"); c.options.setPerspective(Perspective.THIRD_PERSON_FRONT); c.player.setPitch(20); });
            for (int i = 7; i < auras.length; i++) {
                String a = auras[i];
                ctx.runOnClient(c -> ((NewModules.Cosmetics) OrbitClient.modules().get("cosmetics")).aura.set(a));
                ctx.waitTicks(8);
                ctx.takeScreenshot("c4-aura-" + i + "-" + a);
            }
            ctx.runOnClient(c -> { var cm = (NewModules.Cosmetics) OrbitClient.modules().get("cosmetics"); cm.aura.set("None"); cm.pet.set("Baby Dragon"); cm.hat.set("Party Hat"); c.options.setPerspective(Perspective.THIRD_PERSON_BACK); });
            var fwd = ctx.computeOnClient(c -> c.options.forwardKey);
            ctx.getInput().holdKey(fwd);
            ctx.waitTicks(20);
            ctx.takeScreenshot("c5-walking-dragon");
            ctx.getInput().releaseKey(fwd);
            ctx.runOnClient(c -> { var cm = (NewModules.Cosmetics) OrbitClient.modules().get("cosmetics"); cm.pet.set("None"); cm.hat.set("None"); });
            // Joyride: parked black car, engine start (front view, then from the side once it runs)
            ctx.runOnClient(c -> { c.options.setPerspective(Perspective.THIRD_PERSON_FRONT); c.player.setPitch(18); Emotes.playLocal(Emote.JOYRIDE); });
            ctx.waitTicks(1);
            ctx.waitTicks(7);
            ctx.takeScreenshot("c6-car-press");
            ctx.waitTicks(10);
            ctx.takeScreenshot("c6-car-crank");
            ctx.waitTicks(20);
            ctx.takeScreenshot("c6-car-running");
            ctx.runOnClient(c -> c.options.setPerspective(Perspective.THIRD_PERSON_BACK));
            ctx.getInput().holdKey(OrbitClient.freelookKey);
            ctx.waitTicks(3);
            ctx.runOnClient(c -> OrbitClient.modules().freelook().turn(-450, 60));
            ctx.waitTicks(10);
            ctx.takeScreenshot("c6-car-side");
            ctx.runOnClient(c -> OrbitClient.modules().freelook().turn(-500, 0));
            ctx.waitTicks(10);
            ctx.takeScreenshot("c6-car-front-quarter");
            ctx.getInput().releaseKey(OrbitClient.freelookKey);
            ctx.runOnClient(c -> { Emotes.stop(c.player.getUuid()); c.options.setPerspective(Perspective.FIRST_PERSON); });
            OrbitTitleScreen.allowVanillaOnce = true;
        }
    }
}
