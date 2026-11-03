package com.mineagent.engine.survival;

import com.mineagent.api.entity.AgentPlayer;
import com.mineagent.api.task.TaskChain;
import com.mineagent.engine.act.Interaction;
import com.mineagent.engine.entity.CompanionEntity;
import com.mineagent.engine.pathing.util.BlockHelper;
import com.mineagent.engine.task.TaskContext;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.phys.Vec3;

/**
 * Lava and fire (SMH plan 70, MA8.1): out of lava the shortest safe way (the nearest block it can stand on, jumped
 * to); burning, water on itself (its bucket poured at its feet and taken back up, or into water near), or out of the
 * fire it stands in. Above the breath chain: lava kills faster than water. Switch: {@code behaviour.escapeLavaFire}.
 */
public final class LavaFireChain implements TaskChain {
    static final float PRIORITY = 9.5f;
    /** How far it looks for ground to get out onto, or water to put itself out in. */
    static final int REACH = 6;

    private final AgentPlayer companion;
    private BlockPos waterPlaced;
    private int pickUpTicks;
    private int previousSlot = -1;
    private BlockPos goal;
    private int goalAge;

    public LavaFireChain(AgentPlayer companion) {
        this.companion = companion;
    }

    @Override
    public String name() {
        return "lava_fire";
    }

    @Override
    public float getPriority(AgentPlayer companion) {
        try {
            if (!com.mineagent.engine.settings.Switches.escapeLavaFire()) return Float.NEGATIVE_INFINITY;
            ServerPlayer sp = ((CompanionEntity) companion).serverPlayer();
            if (sp == null || sp.isDeadOrDying()) return Float.NEGATIVE_INFINITY;
            if (waterPlaced != null) return PRIORITY;
            if (sp.isInLava()) return PRIORITY;
            if (sp.isOnFire() && !sp.hasEffect(MobEffects.FIRE_RESISTANCE)) return PRIORITY;
            return Float.NEGATIVE_INFINITY;
        } catch (RuntimeException e) {
            return Float.NEGATIVE_INFINITY;
        }
    }

    @Override
    public void tick(AgentPlayer companion) {
        ServerPlayer sp = ((CompanionEntity) companion).serverPlayer();
        var input = TaskContext.inputDriver(companion);
        try {
            if (waterPlaced != null) {
                takeWaterBack(sp);
                return;
            }
            ServerLevel level = sp.serverLevel();
            if (sp.isInLava()) {
                // The nearest ground it can stand on, faced and jumped to.
                if (goal == null || ++goalAge > 20 || !standable(level, goal)) {
                    goal = nearest(level, sp.blockPosition(), false);
                    goalAge = 0;
                }
                if (goal == null) {
                    input.clear();
                    input.setJumping(true); // up: lava is swum like water, slowly
                    return;
                }
                walkAt(sp, input, Vec3.atBottomCenterOf(goal));
                return;
            }
            // Burning, out of lava.
            int bucket = slotOf(sp, Items.WATER_BUCKET);
            BlockPos feet = sp.blockPosition();
            if (bucket >= 0 && sp.onGround() && !sp.isInWater() && level.getBlockState(feet).canBeReplaced()
                    && !level.getBlockState(feet.below()).isAir() && !(level.getBlockState(feet).getBlock() instanceof BaseFireBlock)) {
                input.clear();
                previousSlot = sp.getInventory().selected;
                hold(sp, bucket);
                sp.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(feet.below()).add(0, 0.5, 0));
                Interaction.interactBlock(sp, feet.below(), InteractionHand.MAIN_HAND);
                TaskContext.syncInventory(sp);
                if (level.getFluidState(feet).is(FluidTags.WATER)) {
                    waterPlaced = feet.immutable();
                    pickUpTicks = 0;
                    SurvivalBuiltin.bodyLog(companion).report("I was on fire: poured water on myself");
                }
                return;
            }
            // No bucket: into water near, or out of the fire it stands in.
            BlockPos water = nearest(level, feet, true);
            if (water != null) {
                walkAt(sp, input, Vec3.atBottomCenterOf(water));
                return;
            }
            if (level.getBlockState(feet).getBlock() instanceof BaseFireBlock) {
                BlockPos out = nearest(level, feet, false);
                if (out != null) {
                    walkAt(sp, input, Vec3.atBottomCenterOf(out));
                    return;
                }
            }
            input.clear(); // it burns out; nothing better to do here
        } catch (RuntimeException e) {
            System.err.println("[MineAgent] Lava/fire tick error: " + e.getMessage());
            input.clear();
        }
    }

    /** The water it poured, back in its bucket: no puddle left behind. */
    private void takeWaterBack(ServerPlayer sp) {
        var input = TaskContext.inputDriver(companion);
        input.clear();
        if (++pickUpTicks > 40 || !sp.serverLevel().getFluidState(waterPlaced).is(FluidTags.WATER)) {
            done(sp);
            return;
        }
        if (sp.isOnFire() && pickUpTicks < 6) return; // let it put the fire out first
        int bucket = slotOf(sp, Items.BUCKET);
        if (bucket < 0) {
            done(sp);
            return;
        }
        hold(sp, bucket);
        sp.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(waterPlaced));
        Interaction.useItem(sp, InteractionHand.MAIN_HAND);
        TaskContext.syncInventory(sp);
        if (!sp.serverLevel().getFluidState(waterPlaced).is(FluidTags.WATER)) done(sp);
    }

    private void done(ServerPlayer sp) {
        waterPlaced = null;
        if (previousSlot >= 0 && previousSlot < 9) sp.getInventory().selected = previousSlot;
        previousSlot = -1;
        TaskContext.syncInventory(sp);
    }

    /** Faced, forward, jumping: as a player gets out of lava. */
    private static void walkAt(ServerPlayer sp, com.mineagent.api.entity.InputDriver input, Vec3 to) {
        Vec3 d = to.subtract(sp.position());
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        sp.setYRot(yaw);
        sp.setYHeadRot(yaw);
        sp.setXRot(0);
        input.setForward(1.0f);
        input.setStrafe(0);
        input.setSprinting(false);
        input.setJumping(true);
    }

    /** The nearest cell it can stand in safely (or, {@code water}, a water cell to stand in), within {@link #REACH}. */
    static BlockPos nearest(ServerLevel level, BlockPos at, boolean water) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-REACH, -2, -REACH), at.offset(REACH, 2, REACH))) {
            if (water ? !level.getFluidState(p).is(FluidTags.WATER) : !standable(level, p)) continue;
            double d = p.distSqr(at) + Math.abs(p.getY() - at.getY()) * 2.0; // level ground first
            if (d < bestD) {
                bestD = d;
                best = p.immutable();
            }
        }
        return best;
    }

    /** Feet and head clear of lava and fire, solid ground under: safe to stand. */
    static boolean standable(ServerLevel level, BlockPos p) {
        var feet = level.getBlockState(p);
        var head = level.getBlockState(p.above());
        var below = level.getBlockState(p.below());
        if (!level.getFluidState(p).isEmpty() && !level.getFluidState(p).is(FluidTags.WATER)) return false;
        if (level.getFluidState(p.above()).is(FluidTags.LAVA) || level.getFluidState(p.below()).is(FluidTags.LAVA)) return false;
        if (feet.getBlock() instanceof BaseFireBlock || head.getBlock() instanceof BaseFireBlock) return false;
        return BlockHelper.isPassable(feet) && BlockHelper.isPassable(head) && BlockHelper.canStandOn(below);
    }

    private static int slotOf(ServerPlayer sp, net.minecraft.world.item.Item item) {
        var inv = sp.getInventory();
        if (inv.getSelected().is(item)) return inv.selected;
        for (int i = 0; i < Math.min(36, inv.getContainerSize()); i++) if (inv.getItem(i).is(item)) return i;
        return -1;
    }

    private static void hold(ServerPlayer sp, int slot) {
        var inv = sp.getInventory();
        if (slot == inv.selected) return;
        if (slot < 9) {
            inv.selected = slot;
        } else {
            ItemStack displaced = inv.getItem(inv.selected);
            inv.setItem(inv.selected, inv.getItem(slot));
            inv.setItem(slot, displaced);
        }
        TaskContext.syncInventory(sp);
    }

    @Override
    public void onInterrupt(AgentPlayer companion) {
        TaskContext.inputDriver(companion).clear();
        goal = null;
    }
}
