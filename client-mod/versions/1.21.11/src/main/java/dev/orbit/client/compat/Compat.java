package dev.orbit.client.compat;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.util.Identifier;

/** Version-sensitive calls for Minecraft 1.21.9 – 1.21.11 (GUI render state, input records, GPU samplers). */
public final class Compat {
    private Compat() {}

    private static final KeyBinding.Category CATEGORY = KeyBinding.Category.create(Identifier.of("orbit", "main"));

    public static net.minecraft.util.Identifier id(String path) { return net.minecraft.util.Identifier.of("orbit", path); }

    public static void push(DrawContext c) { c.getMatrices().pushMatrix(); }
    public static void pop(DrawContext c) { c.getMatrices().popMatrix(); }
    public static void translate(DrawContext c, float x, float y) { c.getMatrices().translate(x, y); }
    public static void scale(DrawContext c, float s) { c.getMatrices().scale(s, s); }

    public static void effectIcon(DrawContext c, StatusEffectInstance e, int x, int y, int size) {
        c.drawGuiTexture(RenderPipelines.GUI_TEXTURED, InGameHud.getEffectTexture(e.getEffectType()), x, y, size, size);
    }

    public static KeyBinding key(String id, int glfwKey) {
        return new KeyBinding("key.orbit." + id, InputUtil.Type.KEYSYM, glfwKey, CATEGORY);
    }
    public static boolean matches(KeyBinding kb, int key, int scan) { return kb.matchesKey(new KeyInput(key, scan, 0)); }

    public static NativeImageBackedTexture texture(String name, NativeImage img, boolean smoothClamped) {
        return smoothClamped ? new Smooth(name, img) : new NativeImageBackedTexture(() -> name, img);
    }

    /** Linear-filtered, edge-clamped texture (used by the sky cube map so faces don't bleed). */
    private static final class Smooth extends NativeImageBackedTexture {
        Smooth(String name, NativeImage img) {
            super(() -> name, img);
            this.sampler = RenderSystem.getSamplerCache().get(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.LINEAR, false);
        }
    }
    public static String effectName(net.minecraft.entity.effect.StatusEffectInstance e) { return e.getEffectType().value().getName().getString(); }
    /** Only the real F3 screen — not hitboxes/chunk borders, which also count as 'debug entries' since 1.21.9. */
    public static boolean debugHudOpen(net.minecraft.client.MinecraftClient mc) { return mc.debugHudEntryList.isF3Enabled() && !mc.options.hudHidden; }
    // ---------------------------------------------------------------- keys
    public static void rebind(net.minecraft.client.option.KeyBinding kb, int code) {
        kb.setBoundKey(code < 0 ? net.minecraft.client.util.InputUtil.UNKNOWN_KEY : net.minecraft.client.util.InputUtil.Type.KEYSYM.createFromCode(code));
        net.minecraft.client.option.KeyBinding.updateKeysByCode();
        net.minecraft.client.MinecraftClient.getInstance().options.write();
    }
    public static String keyName(net.minecraft.client.option.KeyBinding kb) { return kb.getBoundKeyLocalizedText().getString(); }

    // ---------------------------------------------------------------- images, worlds, servers
    /** Draws a whole texture of texW×texH scaled into w×h. */
    public static void blit(net.minecraft.client.gui.DrawContext c, net.minecraft.util.Identifier id, int x, int y, int w, int h, int texW, int texH) {
        c.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, id, x, y, 0f, 0f, w, h, texW, texH, texW, texH);
    }
    public static void joinWorld(String name, net.minecraft.client.gui.screen.Screen parent) {
        var mc = net.minecraft.client.MinecraftClient.getInstance();
        mc.createIntegratedServerLoader().start(name, () -> mc.setScreen(parent));
    }
    public static void createWorld(net.minecraft.client.gui.screen.Screen parent) {
        var mc = net.minecraft.client.MinecraftClient.getInstance();
        net.minecraft.client.gui.screen.world.CreateWorldScreen.show(mc, () -> mc.setScreen(parent));
    }
    public static void connect(net.minecraft.client.gui.screen.Screen parent, net.minecraft.client.network.ServerInfo info) {
        var mc = net.minecraft.client.MinecraftClient.getInstance();
        net.minecraft.client.gui.screen.multiplayer.ConnectScreen.connect(parent, mc, net.minecraft.client.network.ServerAddress.parse(info.address), info, false, null);
    }
    public static void ping(net.minecraft.client.network.MultiplayerServerListPinger pinger, net.minecraft.client.network.ServerInfo info, Runnable saver) throws java.net.UnknownHostException {
        var mc = net.minecraft.client.MinecraftClient.getInstance();
        pinger.add(info, saver, saver, net.minecraft.network.NetworkingBackend.remote(mc.options.shouldUseNativeTransport()));
    }
    /** Text style using one of Orbit's bundled fonts (assets/orbit/font). */
    public static net.minecraft.text.Style font(net.minecraft.util.Identifier id) { return net.minecraft.text.Style.EMPTY.withFont(new net.minecraft.text.StyleSpriteSource.Font(id)); }
    public static net.minecraft.util.Identifier vanilla(String path) { return net.minecraft.util.Identifier.of("minecraft", path); }
    /** {hunger, saturation} an item restores, or null if it isn't food. */
    public static float[] food(net.minecraft.item.ItemStack st) {
        if (st == null || st.isEmpty()) return null;
        net.minecraft.component.type.FoodComponent f = st.get(net.minecraft.component.DataComponentTypes.FOOD);
        return f == null ? null : new float[]{f.nutrition(), f.saturation()};
    }
    public interface ChatClick { boolean click(double x, double y, int button); }
    /** Lets HUD widgets take mouse clicks while the chat screen is open (true = consumed). */
    public static void onChatClick(ChatClick h) {
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((client, screen, w, hgt) -> {
            if (screen instanceof net.minecraft.client.gui.screen.ChatScreen)
                net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents.allowMouseClick(screen).register((s, click) -> !h.click(click.x(), click.y(), click.button()));
        });
    }
    public static net.minecraft.client.gui.screen.ChatScreen chat(String text) { return new net.minecraft.client.gui.screen.ChatScreen(text, false); }
    /** Draws everything queued so far, so later drawing lands on top of it. */
    public static void flush(net.minecraft.client.gui.DrawContext c) { c.createNewRootLayer(); }
    /** Draws part of a texture tinted with an ARGB colour. */
    public static void blitTint(net.minecraft.client.gui.DrawContext c, net.minecraft.util.Identifier id, int x, int y, int w, int h, float u, float v, int rw, int rh, int tw, int th, int argb) {
        c.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, id, x, y, u, v, w, h, rw, rh, tw, th, argb);
    }
    public static float matScale(net.minecraft.client.gui.DrawContext c) {
        org.joml.Matrix3x2f m = c.getMatrices();
        return (float) Math.sqrt(m.m00() * m.m00() + m.m01() * m.m01());
    }
    /** Draws a GUI sprite (from the vanilla gui atlas) stretched to w x h. */
    public static void guiSprite(net.minecraft.client.gui.DrawContext c, net.minecraft.util.Identifier id, int x, int y, int w, int h) { c.drawGuiTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, id, x, y, w, h); }
}
