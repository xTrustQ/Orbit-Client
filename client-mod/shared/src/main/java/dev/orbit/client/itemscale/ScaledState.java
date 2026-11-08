package dev.orbit.client.itemscale;

/** Added to ItemEntityRenderState: the size worked out for that dropped item this frame. */
public interface ScaledState {
    float orbit$scale();
    void orbit$setScale(float scale);
    boolean orbit$onGround();
    void orbit$setOnGround(boolean onGround);
}
