package dev.orbit.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.orbit.client.troll.Troll;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.sound.SoundEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Troll Mod: when a disguised player gets hit you hear their mob's hurt sound instead of the player one. */
@Mixin(LivingEntity.class)
public abstract class TrollSoundMixin {
    @ModifyExpressionValue(method = "onDamaged", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getHurtSound(Lnet/minecraft/entity/damage/DamageSource;)Lnet/minecraft/sound/SoundEvent;"))
    private SoundEvent orbit$mobHurtSound(SoundEvent original, @com.llamalad7.mixinextras.sugar.Local(argsOnly = true) DamageSource source) {
        return Troll.hurtSound((LivingEntity) (Object) this, source, original);
    }
}
