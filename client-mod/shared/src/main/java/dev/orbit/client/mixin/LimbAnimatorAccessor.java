package dev.orbit.client.mixin;

import net.minecraft.entity.LimbAnimator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Troll Mod: copies a player's walking animation onto their disguise. */
@Mixin(LimbAnimator.class)
public interface LimbAnimatorAccessor {
    @Accessor("speed") float orbit$getSpeed();
    @Accessor("speed") void orbit$setSpeed(float v);
    @Accessor("lastSpeed") float orbit$getLastSpeed();
    @Accessor("lastSpeed") void orbit$setLastSpeed(float v);
    @Accessor("animationProgress") float orbit$getAnimationProgress();
    @Accessor("animationProgress") void orbit$setAnimationProgress(float v);
}
