// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/automatone/pathing/movement/movements/MovementFall.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.mineagent.sbe.automatone.pathing.movement.movements;

import com.mineagent.sbe.altoclef.multiversion.DimensionVer;
import com.mineagent.sbe.altoclef.multiversion.PlayerInventoryVer;
import com.mineagent.sbe.automatone.Automatone;
import com.mineagent.sbe.automatone.api.IBaritone;
import com.mineagent.sbe.automatone.api.Settings;
import com.mineagent.sbe.automatone.api.pathing.movement.MovementStatus;
import com.mineagent.sbe.automatone.api.utils.BetterBlockPos;
import com.mineagent.sbe.automatone.api.utils.Rotation;
import com.mineagent.sbe.automatone.api.utils.RotationUtils;
import com.mineagent.sbe.automatone.api.utils.VecUtils;
import com.mineagent.sbe.automatone.api.utils.input.Input;
import com.mineagent.sbe.automatone.behavior.InventoryBehavior;
import com.mineagent.sbe.automatone.pathing.movement.CalculationContext;
import com.mineagent.sbe.automatone.pathing.movement.Movement;
import com.mineagent.sbe.automatone.pathing.movement.MovementHelper;
import com.mineagent.sbe.automatone.pathing.movement.MovementState;
import com.mineagent.sbe.automatone.pathing.movement.MovementState.MovementTarget;
import com.mineagent.sbe.automatone.utils.pathing.MutableMoveResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.WaterFluid;
import net.minecraft.world.phys.Vec3;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Moves 1 block horizontally, then at least 2 blocks downwards.
 *
 * <p>If {@link Settings#allowBreak} is {@code true}, this movement will break
 * all blocks in the way.
 *
 * <p>Seen from the side:
 * <pre>
 *     src↘
 *         ⬇
 *         ⬇
 *         ⬇
 *       dest
 * </pre>
 */
public class MovementFall extends Movement {

    public MovementFall(IBaritone baritone, BetterBlockPos src, BetterBlockPos dest) {
        super(baritone, src, dest, MovementFall.buildPositionsToBreak(src, dest));
    }

    @Override
    public double calculateCost(CalculationContext context) {
        MutableMoveResult result = new MutableMoveResult();
        MovementDescend.cost(context, src.x, src.y, src.z, dest.x, dest.z, result);
        if (result.y != dest.y) {
            return COST_INF; // doesn't apply to us, this position is a descend not a fall
        }
        return result.cost;
    }

    @Override
    protected Set<BetterBlockPos> calculateValidPositions() {
        Set<BetterBlockPos> set = new HashSet<>();
        set.add(src);
        for (int y = src.y - dest.y; y >= 0; y--) {
            set.add(dest.above(y));
        }
        return set;
    }

    private boolean willPlaceBucket() {
        CalculationContext context = new CalculationContext(baritone);
        MutableMoveResult result = new MutableMoveResult();
        return MovementDescend.dynamicFallCost(context, src.x, src.y, src.z, dest.x, dest.z, 0, context.get(dest.x, src.y - 2, dest.z), result);
    }

    @Override
    public MovementState updateState(MovementState state) {
        super.updateState(state);
        if (state.getStatus() != MovementStatus.RUNNING) {
            return state;
        }

        BlockPos playerFeet = ctx.feetPos();
        Rotation toDest = RotationUtils.calcRotationFromVec3d(ctx.headPos(), VecUtils.getBlockPosCenter(dest), ctx.entityRotations());
        Rotation targetRotation = null;
        BlockState destState = ctx.world().getBlockState(dest);
        boolean isWater = destState.getFluidState().getType() instanceof WaterFluid;
        if (!isWater && willPlaceBucket() && !playerFeet.equals(dest) && !((com.mineagent.sbe.automatone.Baritone) baritone).getExtraBaritoneSettings().shouldNotPlaceBucketButStillFall()) { // SMH: AltoClef 0002
            Inventory inventory = ctx.inventory();
            if (inventory == null || !Inventory.isHotbarSlot(InventoryBehavior.getSlotWithStack(inventory, Automatone.WATER_BUCKETS)) || DimensionVer.isUltrawarm(ctx.world())) {
                return state.setStatus(MovementStatus.UNREACHABLE);
            }

            if (ctx.entity().getY() - dest.getY() < ctx.interactionController().getBlockReachDistance() && !ctx.entity().onGround()) {
                PlayerInventoryVer.setSelectedSlot(inventory, InventoryBehavior.getSlotWithStack(inventory, Automatone.WATER_BUCKETS));

                targetRotation = new Rotation(toDest.getYaw(), 90.0F);

                if (ctx.isLookingAt(dest) || ctx.isLookingAt(dest.below())) {
                    state.setInput(Input.CLICK_RIGHT, true);
                }
            }
        }
        if (targetRotation != null) {
            state.setTarget(new MovementTarget(targetRotation, true));
        } else {
            state.setTarget(new MovementTarget(toDest, false));
        }
        if (playerFeet.equals(dest) && (ctx.entity().getY() - playerFeet.getY() < 0.094 || isWater)) { // 0.094 because lilypads
            if (isWater) { // only match water, not flowing water (which we cannot pick up with a bucket)
                // Avoid sinking further than expected
                state.setInput(Input.JUMP, true);

                Inventory inventory = ctx.inventory();

                if (inventory != null && Inventory.isHotbarSlot(InventoryBehavior.getSlotWithStack(inventory, Automatone.EMPTY_BUCKETS))) {
                    PlayerInventoryVer.setSelectedSlot(inventory, InventoryBehavior.getSlotWithStack(inventory, Automatone.EMPTY_BUCKETS));
                    if (ctx.entity().getDeltaMovement().y >= 0) {
                        return state.setInput(Input.CLICK_RIGHT, true);
                    } else {
                        return state;
                    }
                } else {
                    if (ctx.entity().getDeltaMovement().y >= 0) {
                        return state.setStatus(MovementStatus.SUCCESS);
                    } // don't else return state; we need to stay centered because this water might be flowing under the surface
                }
            } else {
                return state.setStatus(MovementStatus.SUCCESS);
            }
        }
        Vec3 destCenter = VecUtils.getBlockPosCenter(dest); // we are moving to the 0.5 center not the edge (like if we were falling on a ladder)
        if (Math.abs(ctx.entity().getX() + ctx.entity().getDeltaMovement().x - destCenter.x) > 0.1 || Math.abs(ctx.entity().getZ() + ctx.entity().getDeltaMovement().z - destCenter.z) > 0.1) {
            if (!ctx.entity().onGround() && Math.abs(ctx.entity().getDeltaMovement().y) > 0.4) {
                state.setInput(Input.SNEAK, true);
            }
            state.setInput(Input.MOVE_FORWARD, true);
        }
        Vec3i avoid = Optional.ofNullable(avoid()).map(Direction::getNormal).orElse(null);
        if (avoid == null) {
            avoid = src.subtract(dest);
        } else {
            double dist = Math.abs(avoid.getX() * (destCenter.x - avoid.getX() / 2.0 - ctx.entity().getX())) + Math.abs(avoid.getZ() * (destCenter.z - avoid.getZ() / 2.0 - ctx.entity().getZ()));
            if (dist < 0.6) {
                state.setInput(Input.MOVE_FORWARD, true);
            } else if (!ctx.entity().onGround()) {
                state.setInput(Input.SNEAK, false);
            }
        }
        if (targetRotation == null) {
            Vec3 destCenterOffset = new Vec3(destCenter.x + 0.125 * avoid.getX(), destCenter.y, destCenter.z + 0.125 * avoid.getZ());
            state.setTarget(new MovementTarget(RotationUtils.calcRotationFromVec3d(ctx.headPos(), destCenterOffset, ctx.entityRotations()), false));
        }
        if (ctx.world().getBlockState(playerFeet).is(Blocks.SCAFFOLDING) || ctx.world().getBlockState(playerFeet.below()).is(Blocks.SCAFFOLDING)) {
            state.setInput(Input.SNEAK, true);
        }
        return state;
    }

    private Direction avoid() {
        for (int i = 0; i < 15; i++) {
            BlockState state = ctx.world().getBlockState(ctx.feetPos().below(i));
            if (state.getBlock() == Blocks.LADDER) {
                return state.getValue(LadderBlock.FACING);
            }
        }
        return null;
    }

    @Override
    public boolean safeToCancel(MovementState state) {
        // if we haven't started walking off the edge yet, or if we're in the process of breaking blocks before doing the fall
        // then it's safe to cancel this
        return ctx.feetPos().equals(src) || state.getStatus() != MovementStatus.RUNNING;
    }

    private static BetterBlockPos[] buildPositionsToBreak(BetterBlockPos src, BetterBlockPos dest) {
        BetterBlockPos[] toBreak;
        int diffX = src.getX() - dest.getX();
        int diffZ = src.getZ() - dest.getZ();
        int diffY = src.getY() - dest.getY();
        toBreak = new BetterBlockPos[diffY + 2];
        for (int i = 0; i < toBreak.length; i++) {
            toBreak[i] = new BetterBlockPos(src.getX() - diffX, src.getY() + 1 - i, src.getZ() - diffZ);
        }
        return toBreak;
    }

    @Override
    protected boolean prepared(MovementState state) {
        if (state.getStatus() == MovementStatus.WAITING) {
            return true;
        }
        // only break if one of the first three needs to be broken
        // specifically ignore the last one which might be water
        for (int i = 0; i < 4 && i < positionsToBreak.length; i++) {
            if (!MovementHelper.canWalkThrough(ctx, positionsToBreak[i])) {
                return super.prepared(state);
            }
        }
        return true;
    }
}
