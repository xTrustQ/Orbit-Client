package dev.orbit.client.mixin;

import dev.orbit.client.troll.Troll;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.PlaySoundFromEntityS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.sound.SoundEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Troll Mod: the player hurt/death sounds come from the server. If one belongs to a disguised player,
 * play their mob's sound instead.
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class TrollPacketSoundMixin {
    @Inject(method = "onPlaySound", cancellable = true,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/network/PacketApplyBatcher;)V", shift = At.Shift.AFTER))
    private void orbit$posSound(PlaySoundS2CPacket packet, CallbackInfo ci) {
        var mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        String path = packet.getSound().value().id().getPath();
        if (path.equals("item.shield.break")) dev.orbit.client.module.NewModules.ShieldStatus.onShieldBreakNear(packet.getX(), packet.getY(), packet.getZ()); // Shield Status for enemies
        if (!Troll.enabled()) return;
        if (!path.startsWith("entity.player.hurt") && !path.equals("entity.player.death")) return;
        AbstractClientPlayerEntity best = null;
        double bestD = 2.25;
        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            double d = p.squaredDistanceTo(packet.getX(), packet.getY(), packet.getZ());
            if (d < bestD) { bestD = d; best = p; }
        }
        if (best == null) return;
        SoundEvent mob = Troll.mobSound(best, path);
        if (mob == null) return;
        mc.world.playSoundClient(packet.getX(), packet.getY(), packet.getZ(), mob, packet.getCategory(), packet.getVolume(), packet.getPitch(), false);
        ci.cancel();
    }

    @Inject(method = "onPlaySoundFromEntity", cancellable = true,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/network/PacketApplyBatcher;)V", shift = At.Shift.AFTER))
    private void orbit$entitySound(PlaySoundFromEntityS2CPacket packet, CallbackInfo ci) {
        var mc = MinecraftClient.getInstance();
        if (mc.world == null || !Troll.enabled()) return;
        String path = packet.getSound().value().id().getPath();
        if (!path.startsWith("entity.player.hurt") && !path.equals("entity.player.death")) return;
        if (!(mc.world.getEntityById(packet.getEntityId()) instanceof AbstractClientPlayerEntity p)) return;
        SoundEvent mob = Troll.mobSound(p, path);
        if (mob == null) return;
        mc.world.playSoundClient(p.getX(), p.getY(), p.getZ(), mob, packet.getCategory(), packet.getVolume(), packet.getPitch(), false);
        ci.cancel();
    }
}
