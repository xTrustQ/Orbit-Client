package dev.orbit.client.troll;

/** Added to EntityRenderState: this player is "invisible" — draw only the name tag. */
public interface TrollState {
    boolean orbit$hidden();
    void orbit$setHidden(boolean hidden);
    /** the server's line under the name (e.g. "20 ❤") for a disguised player */
    net.minecraft.text.Text orbit$below();
    void orbit$setBelow(net.minecraft.text.Text below);
    /** Death Animations style playing on this entity, -1 = none */
    int orbit$deathStyle();
    void orbit$setDeathStyle(int style);
    float orbit$deathPhi();
    void orbit$setDeathPhi(float phi);
}
