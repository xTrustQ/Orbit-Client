package dev.orbit.client.mixin;

import dev.orbit.client.troll.TrollState;
import net.minecraft.client.render.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderState.class)
public abstract class EntityRenderStateMixin implements TrollState {
    @Unique private boolean orbit$hidden;
    @Override public boolean orbit$hidden() { return orbit$hidden; }
    @Override public void orbit$setHidden(boolean hidden) { orbit$hidden = hidden; }
    @Unique private net.minecraft.text.Text orbit$below;
    @Override public net.minecraft.text.Text orbit$below() { return orbit$below; }
    @Override public void orbit$setBelow(net.minecraft.text.Text below) { orbit$below = below; }
    @Unique private int orbit$deathStyle = -1;
    @Override public int orbit$deathStyle() { return orbit$deathStyle; }
    @Override public void orbit$setDeathStyle(int style) { orbit$deathStyle = style; }
    @Unique private float orbit$deathPhi;
    @Override public float orbit$deathPhi() { return orbit$deathPhi; }
    @Override public void orbit$setDeathPhi(float phi) { orbit$deathPhi = phi; }
    @Unique private int orbit$stage;
    @Override public int orbit$stage() { return orbit$stage; }
    @Override public void orbit$setStage(int key) { orbit$stage = key; }
}
