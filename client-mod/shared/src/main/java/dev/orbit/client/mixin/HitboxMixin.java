package dev.orbit.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.orbit.client.OrbitClient;
import dev.orbit.client.module.NewModules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.debug.EntityHitboxDebugRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Hitboxes module: filter which entities get a box and recolour the box / eye line / look line. */
@Mixin(EntityHitboxDebugRenderer.class)
public abstract class HitboxMixin {
    private static NewModules.Hitboxes orbit$mod() {
        return OrbitClient.modules() != null && OrbitClient.modules().get("hitboxes") instanceof NewModules.Hitboxes h && h.isEnabled() ? h : null;
    }

    @WrapMethod(method = "drawHitbox")
    private void orbit$filter(Entity entity, float tickProgress, boolean inLocalServer, Operation<Void> original) {
        var h = orbit$mod();
        if (h != null && !h.shows(entity)) return;
        original.call(entity, tickProgress, inLocalServer);
    }

    @ModifyArg(method = "drawHitbox", index = 0, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/DrawStyle;stroked(I)Lnet/minecraft/client/render/DrawStyle;", ordinal = 0))
    private int orbit$boxColour(int colour, @Local(argsOnly = true) Entity entity) {
        var h = orbit$mod();
        if (h == null) return colour;
        var mc = MinecraftClient.getInstance();
        boolean aimed = mc.targetedEntity == entity;
        return h.target.on() && aimed ? h.targetColor.argb() : h.color.argb();
    }

    @ModifyArg(method = "drawHitbox", index = 0, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/DrawStyle;stroked(I)Lnet/minecraft/client/render/DrawStyle;", ordinal = 2))
    private int orbit$eyeLine(int colour) {
        var h = orbit$mod();
        return h != null && !h.eyes.on() ? 0 : colour;
    }

    @ModifyArg(method = "drawHitbox", index = 2, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/debug/gizmo/GizmoDrawing;arrow(Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Vec3d;I)Lnet/minecraft/world/debug/gizmo/VisibilityConfigurable;", ordinal = 0), require = 0)
    private int orbit$lookLine(int colour) {
        var h = orbit$mod();
        return h != null && !h.look.on() ? 0 : colour;
    }
}
