package dev.orbit.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.orbit.client.OrbitClient;
import dev.orbit.client.compat.Compat;
import dev.orbit.client.module.NewModules;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.scoreboard.ScoreboardObjective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Scoreboard: hide, scale/move around its right-centre anchor, and recolour the background. */
@Mixin(InGameHud.class)
public abstract class ScoreboardMixin {
    private static final String TARGET = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V";

    private static NewModules.Scoreboard orbit$mod() { return (NewModules.Scoreboard) OrbitClient.modules().get("scoreboard"); }

    @Inject(method = TARGET, at = @At("HEAD"), cancellable = true, require = 0)
    private void orbit$begin(DrawContext c, ScoreboardObjective objective, CallbackInfo ci) {
        var m = orbit$mod();
        if (!m.isEnabled()) return;
        if (m.hide.on()) { ci.cancel(); return; }
        int sw = c.getScaledWindowWidth(), sh = c.getScaledWindowHeight();
        float s = m.scale.f();
        Compat.push(c);
        Compat.translate(c, sw - m.offsetX.f(), sh / 2f + m.offsetY.f());
        Compat.scale(c, s);
        Compat.translate(c, -sw, -sh / 2f);
    }

    @Inject(method = TARGET, at = @At("RETURN"), require = 0)
    private void orbit$end(DrawContext c, ScoreboardObjective objective, CallbackInfo ci) {
        var m = orbit$mod();
        if (m.isEnabled() && !m.hide.on()) Compat.pop(c);
    }

    @ModifyExpressionValue(method = TARGET, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/GameOptions;getTextBackgroundColor(F)I"), require = 0)
    private int orbit$background(int original) {
        var m = orbit$mod();
        if (!m.isEnabled()) return original;
        return ((int) (m.bgOpacity.get() * 2.55) << 24) | (original & 0xFFFFFF);
    }
}
