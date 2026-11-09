package dev.orbit.client.module;

import dev.orbit.client.mixin.MinecraftClientClickInvoker;
import dev.orbit.client.module.setting.Setting;
import net.minecraft.util.hit.HitResult;

import java.util.Random;

/**
 * Auto Clicker: while you hold the attack (or use) button it keeps clicking for you at the chosen CPS, like a
 * normal autoclicker. Holding left on a block still mines it normally. Many servers don't allow autoclickers —
 * only use it where it's permitted.
 */
public final class AutoClicker extends Module {
    final Setting.Bool left = add(new Setting.Bool("left", "Left click (attack)", true));
    final Setting.Slider leftCps = add(new Setting.Slider("leftcps", "Left CPS", 12, 1, 20, 1, " CPS"));
    final Setting.Bool right = add(new Setting.Bool("right", "Right click (use / place)", false));
    final Setting.Slider rightCps = add(new Setting.Slider("rightcps", "Right CPS", 12, 1, 20, 1, " CPS"));
    final Setting.Bool mine = add(new Setting.Bool("mine", "Mine blocks normally when aiming at one", true));
    final Setting.Bool vary = add(new Setting.Bool("vary", "Slightly uneven timing (feels like a real hand)", true));
    private final Random rnd = new Random();
    private double leftAcc, rightAcc;

    public AutoClicker() { super("autoclicker", "Auto Clicker", "Keeps clicking while you hold a mouse button — check the server rules first", Category.COMBAT, false); }

    @Override
    public void tick() {
        if (!isEnabled() || mc.player == null || mc.world == null || mc.currentScreen != null || !mc.isWindowFocused()) { leftAcc = rightAcc = 0; return; }
        var inv = (MinecraftClientClickInvoker) mc;
        // left: attack, but let vanilla mining work when looking at a block
        boolean onBlock = mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.BLOCK;
        if (left.on() && mc.options.attackKey.isPressed() && !(mine.on() && onBlock) && !mc.player.isUsingItem()) {
            leftAcc += perTick(leftCps.get());
            while (leftAcc >= 1) { leftAcc -= 1; inv.orbit$doAttack(); }
        } else leftAcc = 0;
        if (right.on() && mc.options.useKey.isPressed() && !mc.player.isUsingItem()) {
            rightAcc += perTick(rightCps.get());
            while (rightAcc >= 1) { rightAcc -= 1; inv.orbit$doItemUse(); }
        } else rightAcc = 0;
    }

    /** clicks to do this game tick (20 ticks per second) */
    private double perTick(double cps) {
        double c = cps / 20.0;
        return vary.on() ? c * (0.8 + rnd.nextDouble() * 0.4) : c;
    }
}
