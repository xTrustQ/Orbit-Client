package dev.orbit.client.mixin;

import dev.orbit.client.itemscale.ScaledState;
import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ItemEntityRenderState.class)
public abstract class ItemEntityRenderStateMixin implements ScaledState {
    @Unique private float orbit$scale = 1f;
    @Override public float orbit$scale() { return orbit$scale; }
    @Override public void orbit$setScale(float scale) { orbit$scale = scale; }
    @Unique private boolean orbit$onGround;
    @Override public boolean orbit$onGround() { return orbit$onGround; }
    @Override public void orbit$setOnGround(boolean onGround) { orbit$onGround = onGround; }
}
