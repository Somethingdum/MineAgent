package com.mineagent.engine.act;

import com.mineagent.engine.task.Ballistics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.phys.Vec3;

/**
 * A bow shot, a step a tick, as a player draws and looses (SMH plan 70, MA10.1 and MA10.6): aimed where the target will
 * be when the arrow gets there, high enough for its drop; drawn to full; loosed only along a line that passes nobody
 * else ({@code survival/Manners}) and that it can see.
 */
public final class BowShot {
    private BowShot() {}

    /** Draw ticks for a full-power arrow. */
    public static final int FULL_DRAW = 20;

    public enum State { DRAWING, LOOSED, HELD_BACK, NO_BOW }

    /** One tick of the shot at {@code target}; the bow must already be in the main hand. */
    public static State tick(ServerPlayer sp, Entity target) {
        if (!(sp.getMainHandItem().getItem() instanceof BowItem)) return State.NO_BOW;
        aim(sp, target);
        if (!sp.isUsingItem() || !(sp.getUseItem().getItem() instanceof BowItem)) {
            if (sp.isUsingItem()) sp.stopUsingItem();
            Interaction.useItem(sp, InteractionHand.MAIN_HAND);
            return State.DRAWING;
        }
        if (sp.getTicksUsingItem() < FULL_DRAW) return State.DRAWING;
        if (!sp.hasLineOfSight(target) || com.mineagent.engine.survival.Manners.crossesSomeone(sp, sp.getEyePosition(),
                target.getBoundingBox().getCenter(), target)) {
            sp.stopUsingItem();
            return State.HELD_BACK;
        }
        sp.releaseUsingItem();
        com.mineagent.engine.task.TaskContext.syncInventory(sp);
        return State.LOOSED;
    }

    /** Faces where the arrow meets the target, with the pitch its drop needs. */
    public static void aim(ServerPlayer sp, Entity target) {
        String bow = BuiltInRegistries.ITEM.getKey(sp.getMainHandItem().getItem()).toString();
        double v = Ballistics.velocityForWeapon(bow, FULL_DRAW);
        double g = Ballistics.gravityForWeapon(bow);
        Vec3 from = sp.getEyePosition();
        Vec3 at = target.position().add(0, target.getBbHeight() * 0.6, 0);
        Vec3 lead = Ballistics.calculateLead(from, at, target.getDeltaMovement(), v, g);
        Vec3 d = lead.subtract(from);
        double flat = Math.sqrt(d.x * d.x + d.z * d.z);
        double pitch = Ballistics.calculatePitch(flat, d.y, v, g);
        if (!Double.isFinite(pitch)) pitch = -Math.atan2(d.y, flat);
        sp.setYRot((float) Math.toDegrees(Math.atan2(-d.x, d.z)));
        sp.setXRot((float) Math.toDegrees(pitch));
        sp.setYHeadRot(sp.getYRot());
    }
}
