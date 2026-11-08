package dev.orbit.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.orbit.client.death.DeathAnimations;
import dev.orbit.client.troll.Troll;
import dev.orbit.client.troll.TrollState;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Troll Mod (only on your screen): a disguised player is drawn as another entity standing exactly where they
 * are; the server's name tag (with health) is lifted to the mob's head. An "invisible" player is not drawn at all.
 * Also tags dying entities with their Death Animations style.
 */
@Mixin(EntityRenderManager.class)
public abstract class TrollRenderMixin {
    @WrapMethod(method = "getAndUpdateRenderState")
    private EntityRenderState orbit$troll(Entity entity, float tickProgress, Operation<EntityRenderState> original) {
        EntityRenderState s = orbit$state(entity, tickProgress, original);
        if (entity instanceof LivingEntity l && l.deathTime > 0) { ((TrollState) s).orbit$setDeathStyle(DeathAnimations.styleFor(entity)); ((TrollState) s).orbit$setDeathPhi(DeathAnimations.phiFor(entity)); }
        return s;
    }

    private static EntityRenderState orbit$state(Entity entity, float tickProgress, Operation<EntityRenderState> original) {
        if (entity instanceof AbstractClientPlayerEntity p) {
            Troll.Prank pr = Troll.prankFor(p);
            if (pr != null && pr.kind == Troll.Kind.DISGUISE) {
                Entity fake = Troll.fakeFor(p, pr.entity);
                if (fake != null) {
                    try {
                        EntityRenderState s = original.call(fake, tickProgress);
                        // no server tag? keep the vanilla score line ("20 ❤") under our name tag
                        if (s.displayName != null && s.squaredDistanceToCamera < 100.0) ((TrollState) s).orbit$setBelow(p.getMannequinName());
                        return s;
                    } catch (Throwable t) { Troll.broken(pr.entity); }
                }
            }
            if (pr != null && pr.kind == Troll.Kind.INVISIBLE) return orbit$hide(original.call(entity, tickProgress));
            return original.call(entity, tickProgress);
        }
        AbstractClientPlayerEntity owner = Troll.tagOwner(entity);
        if (owner != null) {
            Troll.Prank pr = Troll.prankFor(owner);
            EntityRenderState s = original.call(entity, tickProgress);
            if (pr != null && pr.kind == Troll.Kind.INVISIBLE) return orbit$hide(s);
            // the server's name / health tag: keep it (the disguise then skips its own name) and move it to the mob's head
            Troll.sawServerTag(owner);
            s.y += Troll.headOffset(owner);
            return s;
        }
        return original.call(entity, tickProgress);
    }

    /** Chat Bubbles: the player's latest message floats above their name tag */
    private static void orbit$chatBubble(EntityRenderState state, CameraRenderState camera, double x, double y, double z, MatrixStack matrices, OrderedRenderCommandQueue queue) {
        if (!(state instanceof net.minecraft.client.render.entity.state.PlayerEntityRenderState ps)) return;
        if (!(dev.orbit.client.OrbitClient.modules().get("chatbubbles") instanceof dev.orbit.client.module.PvpModules.ChatBubbles cb) || !cb.isEnabled()) return;
        var mc = net.minecraft.client.MinecraftClient.getInstance();
        if (mc.world == null || !(mc.world.getEntityById(ps.id) instanceof net.minecraft.entity.player.PlayerEntity p)) return;
        if (p == mc.player && (!cb.mine.on() || mc.options.getPerspective().isFirstPerson())) return;
        java.util.List<String> lines = cb.linesFor(p.getUuid());
        if (lines == null || state.squaredDistanceToCamera > 32 * 32) return;
        net.minecraft.util.math.Vec3d base = state.nameLabelPos != null ? state.nameLabelPos : new net.minecraft.util.math.Vec3d(0, state.height + 0.5, 0);
        matrices.push();
        matrices.translate(x, y, z);
        for (int i = 0; i < lines.size(); i++) {
            double up = 0.42 + (lines.size() - 1 - i) * 0.27;
            queue.submitLabel(matrices, base.add(0, up, 0), 0, net.minecraft.text.Text.literal(lines.get(i)).formatted(net.minecraft.util.Formatting.WHITE), true, state.light, state.squaredDistanceToCamera, camera);
        }
        matrices.pop();
    }

    private static EntityRenderState orbit$hide(EntityRenderState s) {
        ((TrollState) s).orbit$setHidden(true);
        s.shadowPieces.clear();
        s.onFire = false;
        s.displayName = null;
        return s;
    }

    @WrapMethod(method = "render")
    private void orbit$renderHidden(EntityRenderState state, CameraRenderState camera, double x, double y, double z, MatrixStack matrices,
                                    OrderedRenderCommandQueue queue, Operation<Void> original) {
        TrollState ts = (TrollState) state;
        if (ts.orbit$hidden()) return; // hidden: draw nothing at all (no body, items, shadow or name tag)
        if (DeathAnimations.isBonk(ts.orbit$deathStyle()) && state instanceof net.minecraft.client.render.entity.state.LivingEntityRenderState ls)
            dev.orbit.client.cosmetics.Props.renderBonker(matrices, queue, camera, x, y, z, state.light, ls.deathTime, ts.orbit$deathPhi(), state.height, state.squaredDistanceToCamera);
        dev.orbit.client.cosmetics.Props.renderHitbox(matrices, queue, state, x, y, z); // Hitboxes module
        orbit$chatBubble(state, camera, x, y, z, matrices, queue);
        net.minecraft.text.Text below = ts.orbit$below();
        if (below == null || state.displayName == null || state.nameLabelPos == null) { original.call(state, camera, x, y, z, matrices, queue); return; }
        // disguise with a score line: the mob without its label, then score + name stacked like vanilla players
        net.minecraft.text.Text name = state.displayName;
        state.displayName = null;
        original.call(state, camera, x, y, z, matrices, queue);
        state.displayName = name;
        matrices.push();
        matrices.translate(x, y, z);
        queue.submitLabel(matrices, state.nameLabelPos, 0, below, !state.sneaking, state.light, state.squaredDistanceToCamera, camera);
        matrices.translate(0.0F, 9.0F * 1.15F * 0.025F, 0.0F);
        queue.submitLabel(matrices, state.nameLabelPos, 0, name, !state.sneaking, state.light, state.squaredDistanceToCamera, camera);
        matrices.pop();
    }
}
