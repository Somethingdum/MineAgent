// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/automatone/pathing/movement/movements/MovementDownward.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
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

import com.mineagent.sbe.automatone.Baritone;
import com.mineagent.sbe.automatone.api.IBaritone;
import com.mineagent.sbe.automatone.api.Settings;
import com.mineagent.sbe.automatone.api.pathing.movement.MovementStatus;
import com.mineagent.sbe.automatone.api.utils.BetterBlockPos;
import com.mineagent.sbe.automatone.api.utils.input.Input;
import com.mineagent.sbe.automatone.pathing.movement.CalculationContext;
import com.mineagent.sbe.automatone.pathing.movement.Movement;
import com.mineagent.sbe.automatone.pathing.movement.MovementHelper;
import com.mineagent.sbe.automatone.pathing.movement.MovementState;
import com.mineagent.sbe.automatone.utils.pathing.MutableMoveResult;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.state.BlockState;
import com.google.common.collect.ImmutableSet;
import java.util.Set;

/**
 * Moves exactly one block downward, either by breaking the block the player is standing on
 * or by going down a climbable block.
 *
 * <p>This movement will only be used if {@link Settings#allowDownward} is {@code true}.
 *
 * <p>Seen from the side:
 * <pre>
 *     src ⬇
 *     dest
 * </pre>
 */
public class MovementDownward extends Movement {

    private int numTicks = 0;

    public MovementDownward(IBaritone baritone, BetterBlockPos start, BetterBlockPos end) {
        super(baritone, start, end, buildPositionsToBreak(baritone.getPlayerContext().entity(), end));
    }

    public static BetterBlockPos[] buildPositionsToBreak(Entity entity, BetterBlockPos end) {
        int x = end.x;
        int y = end.y;
        int z = end.z;
        EntityDimensions dims = entity.getDimensions(Pose.STANDING);
        int requiredSideSpace = CalculationContext.getRequiredSideSpace(dims);
        int sideLength = requiredSideSpace * 2 + 1;
        BetterBlockPos[] ret = new BetterBlockPos[sideLength * sideLength];
        int i = 0;

        for (int dx = -requiredSideSpace; dx <= requiredSideSpace; dx++) {
            for (int dz = -requiredSideSpace; dz <= requiredSideSpace; dz++) {
                // If we are at the starting position, we already cleared enough space to stand there
                // So only need to check the blocks below our feet
                ret[i++] = new BetterBlockPos(x + dx, y, z + dz);
            }
        }

        return ret;
    }

    @Override
    public void reset() {
        super.reset();
        numTicks = 0;
    }

    @Override
    public double calculateCost(CalculationContext context) {
        MutableMoveResult result = new MutableMoveResult();
        cost(context, src.x, src.y, src.z, result);
        return result.cost;
    }

    @Override
    protected Set<BetterBlockPos> calculateValidPositions() {
        return ImmutableSet.of(src, dest);
    }

    public static void cost(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
        if (!context.allowDownward) {
            return;
        }
        if (!MovementHelper.canWalkOn(context.bsi, x, y - 2, z, context.baritone.settings())) {
            return;
        }
        BlockState downBlock = context.get(x, y - 1, z);
        BlockState fromBlock = context.get(x, y, z);
        if (fromBlock.is(Blocks.SCAFFOLDING) && fromBlock.getValue(ScaffoldingBlock.BOTTOM)) {
            // scaffolding gains a floor when it is not supported
            // we want to avoid breaking unsupported scaffolding, so stop here
            return;
        }
        if (downBlock.is(BlockTags.CLIMBABLE)) {
            if (fromBlock.is(BlockTags.CLIMBABLE) && downBlock.is(Blocks.SCAFFOLDING) && !fromBlock.is(Blocks.SCAFFOLDING)) {
                // funni edge case
                // So like, if you try to descend into scaffolding while you are in a ladder, well you can't
                // because ladders want you to stop sneaking, but scaffolding doesn't
                return;
            }
            // Larger entities cannot use ladders and stuff
            if (context.requiredSideSpace == 0) {
                result.cost = LADDER_DOWN_ONE_COST;
            }
        } else {
            double totalHardness = 0;
            int requiredSideSpace = context.requiredSideSpace;
            boolean waterFloor = false;
            BlockState headState = context.get(x, y + context.height - 1, z);
            boolean inWater = MovementHelper.isWater(headState);
            for (int dx = -requiredSideSpace; dx <= requiredSideSpace; dx++) {
                for (int dz = -requiredSideSpace; dz <= requiredSideSpace; dz++) {
                    // If we are at the starting position, we already cleared enough space to stand there
                    // So only need to check the blocks below us
                    int checkedX = x + dx;
                    int checkedZ = z + dz;
                    BlockState toBreak = context.get(checkedX, y - 1, checkedZ);
                    // we're standing on it, while it might be block falling, it'll be air by the time we get here in the movement
                    totalHardness += MovementHelper.getMiningDurationTicks(context, checkedX, y - 1, checkedZ, toBreak, false);
                    if (MovementHelper.isWater(toBreak)) {
                        waterFloor = true;
                    }
                }
            }
            if (inWater) {
                totalHardness *= 5; // TODO handle aqua affinity
            }
            double fallCost = (waterFloor ? context.waterWalkSpeed / WALK_ONE_BLOCK_COST : 1) * FALL_N_BLOCKS_COST[1];
            result.cost = fallCost + totalHardness;
            result.oxygenCost = context.oxygenCost(fallCost * 0.5 + totalHardness, headState)
                    + context.oxygenCost(fallCost * 0.5, fromBlock);
        }
    }

    @Override
    public MovementState updateState(MovementState state) {
        super.updateState(state);
        if (state.getStatus() != MovementStatus.RUNNING) {
            return state;
        }

        if (ctx.feetPos().equals(dest)) {
            return state.setStatus(MovementStatus.SUCCESS);
        } else if (!playerInValidPosition()) {
            return state.setStatus(MovementStatus.UNREACHABLE);
        }

        double diffX = ctx.entity().getX() - (dest.getX() + 0.5);
        double diffZ = ctx.entity().getZ() - (dest.getZ() + 0.5);
        double ab = Math.sqrt(diffX * diffX + diffZ * diffZ);

        if (numTicks++ < 10 && ab < 0.2) {
            if (((Baritone) this.baritone).bsi.get0(this.baritone.getPlayerContext().feetPos().below()).is(Blocks.SCAFFOLDING)) {
                // Sneak to go down scaffolding
                state.setInput(Input.SNEAK, true);
            } else if (ctx.entity().isUnderWater()) {
                state.setInput(Input.SNEAK, true);  // go down faster in full water
            }
            return state;
        }
        MovementHelper.moveTowards(ctx, state, dest);
        return state;
    }
}
