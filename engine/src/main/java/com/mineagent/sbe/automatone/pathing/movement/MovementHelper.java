// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/automatone/pathing/movement/MovementHelper.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
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

package com.mineagent.sbe.automatone.pathing.movement;

import com.mineagent.sbe.altoclef.multiversion.PlayerInventoryVer;
import com.mineagent.sbe.automatone.Baritone;
import com.mineagent.sbe.automatone.api.IBaritone;
import com.mineagent.sbe.automatone.api.Settings;
import com.mineagent.sbe.automatone.api.pathing.movement.ActionCosts;
import com.mineagent.sbe.automatone.api.pathing.movement.MovementStatus;
import com.mineagent.sbe.automatone.api.utils.BetterBlockPos;
import com.mineagent.sbe.automatone.api.utils.IEntityContext;
import com.mineagent.sbe.automatone.api.utils.RayTraceUtils;
import com.mineagent.sbe.automatone.api.utils.Rotation;
import com.mineagent.sbe.automatone.api.utils.RotationUtils;
import com.mineagent.sbe.automatone.api.utils.VecUtils;
import com.mineagent.sbe.automatone.api.utils.input.Input;
import com.mineagent.sbe.automatone.pathing.movement.MovementState.MovementTarget;
import com.mineagent.sbe.automatone.utils.BlockStateInterface;
import com.mineagent.sbe.automatone.utils.ToolSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.AbstractSkullBlock;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.InfestedBlock;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WaterlilyBlock;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.WaterFluid;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.Optional;

import static com.mineagent.sbe.automatone.pathing.movement.Movement.HORIZONTALS_BUT_ALSO_DOWN_____SO_EVERY_DIRECTION_EXCEPT_UP;

/**
 * Static helpers for cost calculation
 *
 * @author leijurv
 */
public interface MovementHelper extends ActionCosts {

    static boolean avoidBreaking(BlockStateInterface bsi, int x, int y, int z, BlockState state, Settings settings) {
        Block b = state.getBlock();
        return b == Blocks.ICE // ice becomes water, and water can mess up the path
                || b instanceof InfestedBlock // obvious reasons
                // call context.get directly with x,y,z. no need to make 5 new BlockPos for no reason
                || avoidAdjacentBreaking(bsi, x, y + 1, z, true, settings)
                || avoidAdjacentBreaking(bsi, x + 1, y, z, false, settings)
                || avoidAdjacentBreaking(bsi, x - 1, y, z, false, settings)
                || avoidAdjacentBreaking(bsi, x, y, z + 1, false, settings)
                || avoidAdjacentBreaking(bsi, x, y, z - 1, false, settings);
    }

    static boolean avoidAdjacentBreaking(BlockStateInterface bsi, int x, int y, int z, boolean directlyAbove, Settings settings) {
        // returns true if you should avoid breaking a block that's adjacent to this one (e.g. lava that will start flowing if you give it a path)
        // this is only called for north, south, east, west, and up. this is NOT called for down.
        // we assume that it's ALWAYS okay to break the block thats ABOVE liquid
        BlockState state = bsi.get0(x, y, z);
        Block block = state.getBlock();
        if (!directlyAbove // it is fine to mine a block that has a falling block directly above, this (the cost of breaking the stacked fallings) is included in cost calculations
                // therefore if directlyAbove is true, we will actually ignore if this is falling
                && block instanceof FallingBlock // obviously, this check is only valid for falling blocks
                && settings.avoidUpdatingFallingBlocks.get() // and if the setting is enabled
                && FallingBlock.isFree(bsi.get0(x, y - 1, z))) { // and if it would fall (i.e. it's unsupported)
            return true; // dont break a block that is adjacent to unsupported gravel because it can cause really weird stuff
        }
        return !state.getFluidState().isEmpty();
    }

    static boolean canWalkThrough(IEntityContext ctx, BetterBlockPos pos) {
        return canWalkThrough(new BlockStateInterface(ctx), pos.x, pos.y, pos.z, ctx.baritone().settings());
    }

    static boolean canWalkThrough(BlockStateInterface bsi, int x, int y, int z, Settings settings) {
        return canWalkThrough(bsi, x, y, z, bsi.get0(x, y, z), settings);
    }

    static boolean canWalkThrough(BlockStateInterface bsi, int x, int y, int z, BlockState state, Settings settings) {
        Block block = state.getBlock();
        if (block instanceof AirBlock) { // early return for most common case
            return true;
        }
        if (block instanceof BaseFireBlock
                || block == Blocks.TRIPWIRE
                || block == Blocks.COBWEB
                || block == Blocks.END_PORTAL
                || block == Blocks.COCOA
                || block instanceof AbstractSkullBlock
                || block == Blocks.BUBBLE_COLUMN
                || block instanceof ShulkerBoxBlock
                || block instanceof SlabBlock
                || block instanceof TrapDoorBlock
                || block == Blocks.HONEY_BLOCK
                || block == Blocks.AZALEA
                || block == Blocks.FLOWERING_AZALEA
                || block == Blocks.GLOW_LICHEN
                || block == Blocks.CAVE_VINES
                || block == Blocks.CAVE_VINES_PLANT
                || block == Blocks.END_ROD) {
            return false;
        }
        if (settings.blocksToAvoid.get().contains(block)) {
            return false;
        }
        if (block instanceof DoorBlock || block instanceof FenceGateBlock) {
            // Because there's no nice method in vanilla to check if a door is openable or not, we just have to assume
            // that all wooden doors are openable and vice versa.
            return block instanceof FenceGateBlock || DoorBlock.isWoodenDoor(state);
        }
        if (block instanceof CarpetBlock) {
            return canWalkOn(bsi, x, y - 1, z, settings);
        }
        if (block instanceof SnowLayerBlock) {
            // we've already checked doors and fence gates
            // so the only remaining dynamic isPassables are snow and trapdoor
            // if they're cached as a top block, we don't know their metadata
            // default to true (mostly because it would otherwise make long distance pathing through snowy biomes impossible)
            if (!bsi.worldContainsLoadedChunk(x, z)) {
                return true;
            }
            // the check in BlockSnow.isPassable is layers < 5
            // while actually, we want < 3 because 3 or greater makes it impassable in a 2 high ceiling
            if (state.getValue(SnowLayerBlock.LAYERS) >= 3) {
                return false;
            }
            // ok, it's low enough we could walk through it, but is it supported?
            return canWalkOn(bsi, x, y - 1, z, settings);
        }
        if (isFlowing(x, y, z, state, bsi)) {
            return false; // Don't walk through flowing liquids
        }
        FluidState fluidState = state.getFluidState();
        if (fluidState.getType() instanceof WaterFluid) {
            if (settings.assumeWalkOnWater.get()) {
                return false;
            }
            BlockState up = bsi.get0(x, y + 1, z);
            if ((!settings.allowSwimming.get() && !up.getFluidState().isEmpty()) || up.getBlock() instanceof WaterlilyBlock) {
                return false;
            }
            return true;
        }
        // every block that overrides isPassable with anything more complicated than a "return true;" or "return false;"
        // has already been accounted for above
        // therefore it's safe to not construct a blockpos from our x, y, z ints and instead just pass null
        return state.isPathfindable( PathComputationType.LAND); // workaround for future compatibility =P
    }

    /**
     * canWalkThrough but also won't impede movement at all. so not including doors or fence gates (we'd have to right click),
     * not including water, and not including ladders or vines or cobwebs (they slow us down)
     *
     * @param context Calculation context to provide block state lookup
     * @param x       The block's x position
     * @param y       The block's y position
     * @param z       The block's z position
     * @return Whether or not the block at the specified position
     */
    static boolean fullyPassable(CalculationContext context, int x, int y, int z) {
        return fullyPassable(
                context.bsi.access,
                context.bsi.isPassableBlockPos.set(x, y, z),
                context.bsi.get0(x, y, z)
        );
    }

    static boolean fullyPassable(IEntityContext ctx, BlockPos pos) {
        return fullyPassable(ctx.world(), pos, ctx.world().getBlockState(pos));
    }

    static boolean fullyPassable(BlockGetter access, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        if (block instanceof AirBlock) { // early return for most common case
            return true;
        }
        // exceptions - blocks that are isPassable true, but we can't actually jump through
        if (block instanceof BaseFireBlock
                || block == Blocks.TRIPWIRE
                || block == Blocks.COBWEB
                || block == Blocks.VINE
                || block == Blocks.LADDER
                || block == Blocks.COCOA
                || block instanceof DoorBlock
                || block instanceof FenceGateBlock
                || block instanceof SnowLayerBlock
                || !state.getFluidState().isEmpty()
                || block instanceof TrapDoorBlock
                || block instanceof EndPortalBlock
                || block instanceof SkullBlock
                || block instanceof ShulkerBoxBlock) {
            return false;
        }
        // door, fence gate, liquid, trapdoor have been accounted for, nothing else uses the world or pos parameters
        return state.isPathfindable( PathComputationType.LAND);
    }

    static boolean isReplaceable(int x, int y, int z, BlockState state, BlockStateInterface bsi) {
        // for MovementTraverse and MovementAscend
        // block double plant defaults to true when the block doesn't match, so don't need to check that case
        // all other overrides just return true or false
        // the only case to deal with is snow
        /*
         *  public boolean isReplaceable(IBlockAccess worldIn, BlockPos pos)
         *     {
         *         return ((Integer)worldIn.getBlockState(pos).getValue(LAYERS)).intValue() == 1;
         *     }
         */
        Block block = state.getBlock();
        if (block instanceof AirBlock) {
            // early return for common cases hehe
            return true;
        }
        if (block instanceof SnowLayerBlock) {
            // as before, default to true (mostly because it would otherwise make long distance pathing through snowy biomes impossible)
            if (!bsi.worldContainsLoadedChunk(x, z)) {
                return true;
            }
            return state.getValue(SnowLayerBlock.LAYERS) == 1;
        }
        if (block == Blocks.LARGE_FERN || block == Blocks.TALL_GRASS) {
            return true;
        }
        return state.canBeReplaced();
    }

    @Deprecated
    static boolean isReplacable(int x, int y, int z, BlockState state, BlockStateInterface bsi) {
        return isReplaceable(x, y, z, state, bsi);
    }

    static boolean isDoorPassable(IEntityContext ctx, BlockPos doorPos, BlockPos playerPos) {
        if (playerPos.equals(doorPos)) {
            return false;
        }

        BlockState state = BlockStateInterface.get(ctx, doorPos);
        if (!(state.getBlock() instanceof DoorBlock)) {
            return true;
        }

        return isHorizontalBlockPassable(doorPos, state, playerPos, DoorBlock.OPEN);
    }

    static boolean isGatePassable(IEntityContext ctx, BlockPos gatePos, BlockPos playerPos) {
        if (playerPos.equals(gatePos)) {
            return false;
        }

        BlockState state = BlockStateInterface.get(ctx, gatePos);
        if (!(state.getBlock() instanceof FenceGateBlock)) {
            return true;
        }

        return state.getValue(FenceGateBlock.OPEN);
    }

    static boolean isHorizontalBlockPassable(BlockPos blockPos, BlockState blockState, BlockPos playerPos, BooleanProperty propertyOpen) {
        if (playerPos.equals(blockPos)) {
            return false;
        }

        Direction.Axis facing = blockState.getValue(HorizontalDirectionalBlock.FACING).getAxis();
        boolean open = blockState.getValue(propertyOpen);

        Direction.Axis playerFacing;
        if (playerPos.north().equals(blockPos) || playerPos.south().equals(blockPos)) {
            playerFacing = Direction.Axis.Z;
        } else if (playerPos.east().equals(blockPos) || playerPos.west().equals(blockPos)) {
            playerFacing = Direction.Axis.X;
        } else {
            return true;
        }

        return (facing == playerFacing) == open;
    }

    static boolean avoidWalkingInto(BlockState state) {
        Block block = state.getBlock();
        return !state.getFluidState().isEmpty()
                || block == Blocks.MAGMA_BLOCK
                || block == Blocks.CACTUS
                || block instanceof BaseFireBlock
                || block == Blocks.END_PORTAL
                || block == Blocks.COBWEB
                || block == Blocks.BUBBLE_COLUMN;
    }

    /**
     * Can I walk on this block without anything weird happening like me falling
     * through? Includes water because we know that we automatically jump on
     * water
     *
     * @param bsi   Block state provider
     * @param x     The block's x position
     * @param y     The block's y position
     * @param z     The block's z position
     * @param state The state of the block at the specified location
     * @param settings
     * @return Whether or not the specified block can be walked on
     */
    static boolean canWalkOn(BlockStateInterface bsi, int x, int y, int z, BlockState state, Settings settings) {
        Block block = state.getBlock();
        if (block instanceof AirBlock || block == Blocks.MAGMA_BLOCK || block == Blocks.BUBBLE_COLUMN || block == Blocks.HONEY_BLOCK) {
            // early return for most common case (air)
            // plus magma, which is a normal cube but it hurts you
            return false;
        }
        if (isBlockNormalCube(state)) {
            return true;
        }
        if (state.is(BlockTags.CLIMBABLE)) { // TODO reconsider this
            return true;
        }
        if (block == Blocks.FARMLAND || block == Blocks.DIRT_PATH) {
            return true;
        }
        if (block == Blocks.ENDER_CHEST || block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST) {
            return true;
        }
        if (isWater(state)) {
            // since this is called literally millions of times per second, the benefit of not allocating millions of useless "pos.up()"
            // BlockPos s that we'd just garbage collect immediately is actually noticeable. I don't even think its a decrease in readability
            BlockState upState = bsi.get0(x, y + 1, z);
            Block up = upState.getBlock();
            if (up == Blocks.LILY_PAD || up instanceof CarpetBlock) {
                return true;
            }
            if (isFlowing(x, y, z, state, bsi) || upState.getFluidState().getType() == Fluids.FLOWING_WATER) {
                // the only scenario in which we can walk on flowing water is if it's under still water with jesus off
                return isWater(upState) && !settings.assumeWalkOnWater.get();
            }
            // if assumeWalkOnWater is on, we can only walk on water if there isn't water above it
            // if assumeWalkOnWater is off, we can only walk on water if there is water above it
            return isWater(upState) ^ settings.assumeWalkOnWater.get();
        }
        if (settings.assumeWalkOnLava.get() && isLava(state) && !isFlowing(x, y, z, state, bsi)) {
            return true;
        }
        if (block == Blocks.GLASS || block instanceof StainedGlassBlock) {
            return true;
        }
        if (block instanceof SlabBlock) {
            if (!settings.allowWalkOnBottomSlab.get()) {
                return state.getValue(SlabBlock.TYPE) != SlabType.BOTTOM;
            }
            return true;
        }
        return block instanceof StairBlock;
    }

    static boolean canWalkOn(IEntityContext ctx, BetterBlockPos pos, BlockState state) {
        return canWalkOn(new BlockStateInterface(ctx), pos.x, pos.y, pos.z, state, ctx.baritone().settings());
    }

    static boolean canWalkOn(IEntityContext ctx, BlockPos pos) {
        return canWalkOn(new BlockStateInterface(ctx), pos.getX(), pos.getY(), pos.getZ(), ctx.baritone().settings());
    }

    static boolean canWalkOn(IEntityContext ctx, BetterBlockPos pos) {
        return canWalkOn(new BlockStateInterface(ctx), pos.x, pos.y, pos.z, ctx.baritone().settings());
    }

    static boolean canWalkOn(BlockStateInterface bsi, int x, int y, int z, Settings settings) {
        return canWalkOn(bsi, x, y, z, bsi.get0(x, y, z), settings);
    }

    static boolean canPlaceAgainst(BlockStateInterface bsi, int x, int y, int z) {
        return canPlaceAgainst(bsi, x, y, z, bsi.get0(x, y, z));
    }

    static boolean canPlaceAgainst(BlockStateInterface bsi, BlockPos pos) {
        return canPlaceAgainst(bsi, pos.getX(), pos.getY(), pos.getZ());
    }

    static boolean canPlaceAgainst(IEntityContext ctx, BlockPos pos) {
        return canPlaceAgainst(new BlockStateInterface(ctx), pos);
    }

    static boolean canPlaceAgainst(BlockStateInterface bsi, int x, int y, int z, BlockState state) {
        // can we look at the center of a side face of this block and likely be able to place?
        // (thats how this check is used)
        // therefore dont include weird things that we technically could place against (like carpet) but practically can't
        return isBlockNormalCube(state) || state.getBlock() == Blocks.GLASS || state.getBlock() instanceof StainedGlassBlock;
    }

    static double getMiningDurationTicks(CalculationContext context, int x, int y, int z, boolean includeFalling) {
        return getMiningDurationTicks(context, x, y, z, context.get(x, y, z), includeFalling);
    }

    static double getMiningDurationTicks(CalculationContext context, int x, int y, int z, BlockState state, boolean includeFalling) {
        if (!canWalkThrough(context.bsi, x, y, z, state, context.baritone.settings())) {
            if (!state.getFluidState().isEmpty()) {
                return COST_INF;
            }
            double mult = context.breakCostMultiplierAt(x, y, z, state);
            if (mult >= COST_INF) {
                return COST_INF;
            }
            if (avoidBreaking(context.bsi, x, y, z, state, context.baritone.settings())) {
                return COST_INF;
            }
            if (context.toolSet == null) {
                return COST_INF;
            }
            double strVsBlock = context.toolSet.getStrVsBlock(state);
            if (strVsBlock <= 0) {
                return COST_INF;
            }
            double result = 1 / strVsBlock;
            result += context.breakBlockAdditionalCost;
            result *= mult;
            if (includeFalling) {
                BlockState above = context.get(x, y + 1, z);
                if (above.getBlock() instanceof FallingBlock) {
                    result += getMiningDurationTicks(context, x, y + 1, z, above, true);
                }
            }
            return result;
        }
        return 0; // we won't actually mine it, so don't check fallings above
    }

    // TODO handle other collision boxes
    static boolean isBottomSlab(BlockState state) {
        return state.getBlock() instanceof SlabBlock
                && state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM;
    }

    /**
     * AutoTool for a specific block
     *
     * @param ctx The player context
     * @param b   the blockstate to mine
     */
    static void switchToBestToolFor(IEntityContext ctx, BlockState b) {
        ServerPlayer entity = ctx.entity();
        switchToBestToolFor(ctx, b, new ToolSet(entity), ctx.baritone().settings().preferSilkTouch.get());
    }

    /**
     * AutoTool for a specific block with precomputed ToolSet data
     *
     * @param ctx The player context
     * @param b   the blockstate to mine
     * @param ts  previously calculated ToolSet
     */
    static void switchToBestToolFor(IEntityContext ctx, BlockState b, ToolSet ts, boolean preferSilkTouch) {
        Inventory inventory = ctx.inventory();

        if (inventory != null && !ctx.baritone().settings().disableAutoTool.get() && !ctx.baritone().settings().assumeExternalAutoTool.get()) {
            PlayerInventoryVer.setSelectedSlot(inventory, ts.getBestSlot(b.getBlock(), preferSilkTouch));
        }
    }

    static void moveTowards(IEntityContext ctx, MovementState state, BlockPos pos) {
        state.setTarget(new MovementTarget(
                new Rotation(RotationUtils.calcRotationFromVec3d(ctx.headPos(),
                        VecUtils.getBlockPosCenter(pos),
                        ctx.entityRotations()).getYaw(), ctx.entity().getXRot()),
                false
        )).setInput(Input.MOVE_FORWARD, true);
    }

    /**
     * Returns whether or not the specified block is
     * water, regardless of whether or not it is flowing.
     *
     * @param state The block state
     * @return Whether or not the block is water
     */
    static boolean isWater(BlockState state) {
        return state.getFluidState().is(FluidTags.WATER);
    }

    /**
     * Returns whether or not the block at the specified pos is
     * water, regardless of whether or not it is flowing.
     *
     * @param ctx The player context
     * @param bp  The block pos
     * @return Whether or not the block is water
     */
    static boolean isWater(IEntityContext ctx, BlockPos bp) {
        return isWater(BlockStateInterface.get(ctx, bp));
    }

    static boolean isLava(BlockState state) {
        Fluid f = state.getFluidState().getType();
        return f == Fluids.LAVA || f == Fluids.FLOWING_LAVA;
    }

    /**
     * Returns whether or not the specified pos has a liquid
     *
     * @param ctx The player context
     * @param p   The pos
     * @return Whether or not the block is a liquid
     */
    static boolean isLiquid(IEntityContext ctx, BlockPos p) {
        return isLiquid(BlockStateInterface.get(ctx, p));
    }

    static boolean isLiquid(BlockState blockState) {
        // PERF: getFluidState is kinda slow, but users can install Lithium to fix it
        return !blockState.getFluidState().isEmpty();
    }

    static boolean possiblyFlowing(BlockState state) {
        FluidState fluidState = state.getFluidState();
        return fluidState.getType() instanceof FlowingFluid
                && fluidState.getType().getAmount(fluidState) != 8;
    }

    static boolean isFlowing(int x, int y, int z, BlockState state, BlockStateInterface bsi) {
        FluidState fluidState = state.getFluidState();
        if (!(fluidState.getType() instanceof FlowingFluid)) {
            return false;
        }
        if (fluidState.getType().getAmount(fluidState) != 8) {
            return true;
        }
        return possiblyFlowing(bsi.get0(x + 1, y, z))
                || possiblyFlowing(bsi.get0(x - 1, y, z))
                || possiblyFlowing(bsi.get0(x, y, z + 1))
                || possiblyFlowing(bsi.get0(x, y, z - 1));
    }

    static boolean isBlockNormalCube(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof BambooStalkBlock
                || block instanceof MovingPistonBlock
                || block instanceof ScaffoldingBlock
                || block instanceof ShulkerBoxBlock) {
            return false;
        }
        try {
            // PERF: isShapeFullCube is slow, but people can install lithium to fix it
            // World lookups too slow, just pass null and pray
            return state.isCollisionShapeFullBlock(null, BlockPos.ZERO);
        } catch (NullPointerException npe) {
            return false;
        }
    }

    static PlaceResult attemptToPlaceABlock(MovementState state, IBaritone baritone, BlockPos placeAt, boolean preferDown, boolean wouldSneak) {
        IEntityContext ctx = baritone.getPlayerContext();
        Optional<Rotation> direct = RotationUtils.reachable(ctx, placeAt, wouldSneak); // we assume that if there is a block there, it must be replacable
        boolean found = false;
        if (direct.isPresent()) {
            state.setTarget(new MovementState.MovementTarget(direct.get(), true));
            found = true;
        }
        for (int i = 0; i < 5; i++) {
            BlockPos against1 = placeAt.relative(HORIZONTALS_BUT_ALSO_DOWN_____SO_EVERY_DIRECTION_EXCEPT_UP[i]);
            if (MovementHelper.canPlaceAgainst(ctx, against1)) {
                if (!((Baritone) baritone).getInventoryBehavior().selectThrowawayForLocation(false, placeAt.getX(), placeAt.getY(), placeAt.getZ())) { // get ready to place a throwaway block
                    baritone.logDebug("bb pls get me some blocks. dirt, netherrack, cobble");
                    state.setStatus(MovementStatus.UNREACHABLE);
                    return PlaceResult.NO_OPTION;
                }
                double faceX = (placeAt.getX() + against1.getX() + 1.0D) * 0.5D;
                double faceY = (placeAt.getY() + against1.getY() + 0.5D) * 0.5D;
                double faceZ = (placeAt.getZ() + against1.getZ() + 1.0D) * 0.5D;
                Rotation place = RotationUtils.calcRotationFromVec3d(wouldSneak ? RayTraceUtils.inferSneakingEyePosition(ctx.entity()) : ctx.headPos(), new Vec3(faceX, faceY, faceZ), ctx.entityRotations());
                HitResult res = RayTraceUtils.rayTraceTowards(ctx.entity(), place, ctx.interactionController().getBlockReachDistance(), wouldSneak);
                if (res != null && res.getType() == HitResult.Type.BLOCK && ((BlockHitResult) res).getBlockPos().equals(against1) && ((BlockHitResult) res).getBlockPos().relative(((BlockHitResult) res).getDirection()).equals(placeAt)) {
                    state.setTarget(new MovementState.MovementTarget(place, true));
                    found = true;

                    if (!preferDown) {
                        // if preferDown is true, we want the last option
                        // if preferDown is false, we want the first
                        break;
                    }
                }
            }
        }
        if (ctx.getSelectedBlock().isPresent()) {
            BlockPos selectedBlock = ctx.getSelectedBlock().get();
            Direction side = ((BlockHitResult) ctx.objectMouseOver()).getDirection();
            // only way for selectedBlock.equals(placeAt) to be true is if it's replacable
            if (selectedBlock.equals(placeAt) || (MovementHelper.canPlaceAgainst(ctx, selectedBlock) && selectedBlock.relative(side).equals(placeAt))) {
                if (wouldSneak) {
                    state.setInput(Input.SNEAK, true);
                }
                ((Baritone) baritone).getInventoryBehavior().selectThrowawayForLocation(true, placeAt.getX(), placeAt.getY(), placeAt.getZ());
                return PlaceResult.READY_TO_PLACE;
            }
        }
        if (found) {
            if (wouldSneak) {
                state.setInput(Input.SNEAK, true);
            }
            ((Baritone) baritone).getInventoryBehavior().selectThrowawayForLocation(true, placeAt.getX(), placeAt.getY(), placeAt.getZ());
            return PlaceResult.ATTEMPTING;
        }
        return PlaceResult.NO_OPTION;
    }

    enum PlaceResult {
        READY_TO_PLACE, ATTEMPTING, NO_OPTION;
    }

    static boolean isTransparent(Block b) {

        return b == Blocks.AIR ||
                b == Blocks.LAVA ||
                b == Blocks.WATER;
    }
}
