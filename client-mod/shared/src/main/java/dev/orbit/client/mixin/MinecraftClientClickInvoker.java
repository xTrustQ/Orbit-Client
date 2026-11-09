package dev.orbit.client.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets the Auto Clicker press "attack" / "use" exactly like a real mouse click does. */
@Mixin(MinecraftClient.class)
public interface MinecraftClientClickInvoker {
    @Invoker("doAttack") boolean orbit$doAttack();
    @Invoker("doItemUse") void orbit$doItemUse();
}
