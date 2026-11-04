package com.mineagent.engine.act;

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

    /** Where each target was over the last ticks, to read how it is moving (a player's own motion is not kept on the server). */
    private static final java.util.Map<Integer, double[]> TRACK = new java.util.HashMap<>();
    private static final java.util.Random ERR = new java.util.Random();
    /** The error left in, in degrees: a good archer, not a machine. */
    public static final double AIM_ERROR = 0.35;

    /** How a target is moving a tick, smoothed over the ticks it has been watched. */
    static double[] motion(Entity target, long now) {
        double[] t = TRACK.get(target.getId());
        double x = target.getX(), y = target.getY(), z = target.getZ();
        double[] v = {0, 0, 0};
        if (t != null && now - (long) t[6] >= 1 && now - (long) t[6] <= 10) {
            double dt = now - t[6];
            v = new double[] {(x - t[0]) / dt * 0.6 + t[3] * 0.4, (y - t[1]) / dt * 0.6 + t[4] * 0.4, (z - t[2]) / dt * 0.6 + t[5] * 0.4};
        } else if (t != null && now == (long) t[6]) {
            return new double[] {t[3], t[4], t[5]};
        } else if (!(target instanceof net.minecraft.world.entity.player.Player)) {
            Vec3 d = target.getDeltaMovement();
            v = new double[] {d.x, d.y, d.z};
        }
        if (TRACK.size() > 64) TRACK.clear();
        TRACK.put(target.getId(), new double[] {x, y, z, v[0], v[1], v[2], now});
        return v;
    }

    /** Faces where the arrow meets the target, flying it as the game does ({@code combat/ArrowAim}), with a little error. */
    public static void aim(ServerPlayer sp, Entity target) {
        Vec3 eye = sp.getEyePosition();
        Vec3 at = target.position().add(0, target.getBbHeight() * 0.55, 0);
        double[] tv = motion(target, sp.level().getGameTime());
        Vec3 own = sp.getDeltaMovement();
        double[] sv = {own.x, sp.onGround() ? 0 : own.y, own.z};
        // The error changes a few times a second, not each tick, as a hand drifts.
        if (sp.level().getGameTime() % 5 == 0) { errYaw = ERR.nextGaussian() * 0.5; errPitch = ERR.nextGaussian() * 0.5; }
        var shot = com.mineagent.engine.combat.ArrowAim.solve(new double[] {eye.x, eye.y, eye.z},
                new double[] {at.x, at.y, at.z}, tv, sv, AIM_ERROR, clamp(errYaw), clamp(errPitch));
        if (shot == null) {
            Vec3 d = at.subtract(eye);
            sp.setYRot((float) Math.toDegrees(Math.atan2(-d.x, d.z)));
            sp.setXRot((float) Math.toDegrees(-Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z))) - 20f);
        } else {
            sp.setYRot(shot.yaw());
            sp.setXRot(shot.pitch());
        }
        sp.setYHeadRot(sp.getYRot());
    }

    private static double errYaw, errPitch;

    private static double clamp(double e) { return Math.max(-1, Math.min(1, e)); }

    /** Whether the bow can reach it at all (the arrow's reach on that slope). */
    public static boolean inReach(ServerPlayer sp, Entity target) {
        Vec3 eye = sp.getEyePosition(), at = target.position().add(0, target.getBbHeight() * 0.55, 0);
        double dx = at.x - eye.x, dz = at.z - eye.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        return dist <= com.mineagent.engine.combat.ArrowAim.MAX_RANGE
                && Double.isFinite(com.mineagent.engine.combat.ArrowAim.pitchFor(dist, at.y - eye.y + com.mineagent.engine.combat.ArrowAim.SPAWN_DROP,
                        com.mineagent.engine.combat.ArrowAim.SPEED));
    }
}
