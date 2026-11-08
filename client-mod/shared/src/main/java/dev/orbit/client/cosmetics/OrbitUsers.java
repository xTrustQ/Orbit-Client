package dev.orbit.client.cosmetics;

import dev.orbit.client.OrbitClient;
import dev.orbit.client.compat.Compat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who runs Orbit: you, plus everyone the Orbit sync server reports on the same Minecraft server.
 * Those players get the Orbit badge (a little planet, font "orbit:badge") before their name.
 */
public final class OrbitUsers {
    private OrbitUsers() {}
    private static final Set<UUID> USERS = ConcurrentHashMap.newKeySet();
    public static void add(UUID id) { USERS.add(id); }
    public static void remove(UUID id) { USERS.remove(id); }

    public static boolean isOrbit(UUID id) {
        if (id == null) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        UUID me = mc.player != null ? mc.player.getUuid() : mc.getSession().getUuidOrNull();
        return id.equals(me) || USERS.contains(id);
    }

    public static boolean badgesOn() {
        var m = OrbitClient.modules() == null ? null : OrbitClient.modules().get("badge");
        return m != null && m.isEnabled();
    }

    /** "🪐 name" — the badge glyph is drawn in its own colours, the name keeps its style. */
    public static Text withBadge(Text name) {
        MutableText badge = Text.literal("").setStyle(Compat.font(Compat.id("badge")).withColor(0xFFFFFF));
        return Text.empty().append(badge).append(Text.literal(" ")).append(name);
    }
}
