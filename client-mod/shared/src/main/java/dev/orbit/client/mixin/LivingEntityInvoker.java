package dev.orbit.client.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.sound.SoundEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Troll Mod: asks a disguise entity which hurt sound it makes. */
@Mixin(LivingEntity.class)
public interface LivingEntityInvoker {
    @Invoker("getHurtSound") SoundEvent orbit$hurtSound(DamageSource source);
    @Invoker("getDeathSound") SoundEvent orbit$deathSound();
}
