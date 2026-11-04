package com.mineagent.engine.pathing.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

/**
 * Block state query helpers for the pathfinding system. Provides
 * static utility methods for checking block properties relevant
 * to movement calculations.
 */
public final class BlockHelper {

    private BlockHelper() {}

    /**
     * Check if a block is air (can walk through).
     */
    public static boolean isAir(BlockState state) {
        return state.isAir();
    }

    /**
     * Check if a block is solid (can stand on, can't walk through).
     */
    @SuppressWarnings("deprecation")
    public static boolean isSolid(BlockState state) {
        return state.isSolid();
    }

    /**
     * Check if a block can be stood on (has a solid top surface).
     */
    @SuppressWarnings("deprecation")
    /**
     * The cell a body stands in: its block position, or the cell over it when that block is less than a block high and
     * the body stands on top of it (farmland, a path, soul sand, mud), where the game puts its position in the block.
     */
    public static net.minecraft.core.BlockPos bodyCell(net.minecraft.world.entity.Entity e) {
        net.minecraft.core.BlockPos p = e.blockPosition();
        if (e.getY() - p.getY() > 0.5 && !isPassable(e.level().getBlockState(p))) return p.above();
        return p;
    }

    public static boolean canStandOn(BlockState state) {
        return state != null && !isHazardous(state) && !tall(state)
                && (state.isSolid() || state.is(Blocks.SCAFFOLDING));
    }

    /**
     * A block a block and a half high (a fence, a wall, a fence gate): the game counts it solid, but its top is half a
     * block above the cell over it, so no walk or jump lands there as on a block, and a pen walked out of over its fence
     * is one it can't get back into. Never ground to stand on, for paths.
     */
    public static boolean tall(BlockState state) {
        return state.is(net.minecraft.tags.BlockTags.FENCES) || state.is(net.minecraft.tags.BlockTags.WALLS)
                || state.getBlock() instanceof net.minecraft.world.level.block.FenceGateBlock;
    }

    /**
     * Check if a block is passable (the player can walk through it).
     * This includes air, tall grass, flowers, etc.
     */
    @SuppressWarnings("deprecation")
    public static boolean isPassable(BlockState state) {
        if (state == null) return false;
        if (state.isAir()) return true;
        if (isClimbable(state)) return true;
        if (isHazardous(state)) return false;
        // Solidity is not collision geometry: fences and closed doors can be
        // non-solid while still blocking the same player physics we execute.
        return state.isPathfindable(
                net.minecraft.world.level.pathfinder.PathComputationType.LAND)
                || state.getCollisionShape(
                net.minecraft.world.level.EmptyBlockGetter.INSTANCE,
                BlockPos.ZERO).isEmpty();
    }

    /**
     * Check if a block is water.
     */
    public static boolean isWater(BlockState state) {
        return state != null && state.getFluidState().is(net.minecraft.tags.FluidTags.WATER);
    }

    /**
     * Swimming at the surface (SMH plan 70, MA5.10): feet in still water over more water, head in the air. Its head
     * stays out, so it never runs out of air; a waterfall or a bubble column is no place to swim.
     */
    public static boolean isSwimNode(BlockState below, BlockState feet, BlockState head) {
        if (below == null || feet == null || head == null) return false;
        if (!isWater(feet) || !isWater(below) || isWater(head) || !isPassable(head) || isHazardous(head)) return false;
        if (feet.is(Blocks.BUBBLE_COLUMN) || below.is(Blocks.BUBBLE_COLUMN)) return false;
        return feet.getFluidState().isSource();
    }

    /**
     * Check if a block is lava.
     */
    public static boolean isLava(BlockState state) {
        return state != null && state.getFluidState().is(net.minecraft.tags.FluidTags.LAVA);
    }

    /**
     * Check if a block is any fluid.
     */
    public static boolean isFluid(BlockState state) {
        FluidState fluid = state.getFluidState();
        return !fluid.isEmpty();
    }

    /**
     * Check if a block is a liquid that harms the player (lava).
     */
    public static boolean isHarmfulFluid(BlockState state) {
        return isLava(state);
    }

    /** Vanilla climbable blocks plus scaffolding's vertical traversal. */
    public static boolean isClimbable(BlockState state) {
        return state != null && (state.is(net.minecraft.tags.BlockTags.CLIMBABLE)
                || state.is(Blocks.SCAFFOLDING));
    }

    /** Blocks and fluids that must not be treated as ordinary walkable space. */
    public static boolean isHazardous(BlockState state) {
        if (state == null) return true;
        return isHarmfulFluid(state)
                || state.is(Blocks.FIRE)
                || state.is(Blocks.SOUL_FIRE)
                || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.CAMPFIRE)
                || state.is(Blocks.SOUL_CAMPFIRE)
                || state.is(Blocks.CACTUS)
                || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.WITHER_ROSE)
                || state.is(Blocks.POWDER_SNOW);
    }

    /**
     * Whether a block is the land, something someone built, or something better left alone (SMH plan 70, MA5.1), from
     * its registry name and whether it holds a block entity. See {@link BreakCost#classify}.
     */
    public static BreakCost.Kind kindOf(BlockState state) {
        if (state == null) return BreakCost.Kind.AVOID;
        String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return BreakCost.classify(id, state.hasBlockEntity());
    }

    /** A closed iron door: never opened by hand, only by a button, lever or plate (SMH plan 70, MA5.3). */
    public static boolean isClosedIronDoor(BlockState state) {
        return state != null && state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock door
                && !door.type().canOpenByHand()
                && state.hasProperty(net.minecraft.world.level.block.DoorBlock.OPEN)
                && !state.getValue(net.minecraft.world.level.block.DoorBlock.OPEN);
    }

    /**
     * The button or lever that opens the iron door at x, y, z (either half): one within a block of the door, beside
     * it or above it. Null when there is none, and then the door is a wall it never breaks. Whether it can reach the
     * one found from where it stands is settled when it tries.
     */
    public static BlockPos doorActivator(BreakSafety.BlockAt world, int x, int y, int z) {
        BlockState here = world.at(x, y, z);
        if (here == null) return null;
        int lower = here.hasProperty(net.minecraft.world.level.block.DoorBlock.HALF)
                && here.getValue(net.minecraft.world.level.block.DoorBlock.HALF)
                == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER ? y - 1 : y;
        for (int dy = -1; dy <= 2; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0 && (dy == 0 || dy == 1)) continue; // the door itself
                    BlockState s = world.at(x + dx, lower + dy, z + dz);
                    if (s != null && (s.getBlock() instanceof net.minecraft.world.level.block.ButtonBlock
                            || s.getBlock() instanceof net.minecraft.world.level.block.LeverBlock)) {
                        return new BlockPos(x + dx, lower + dy, z + dz);
                    }
                }
            }
        }
        return null;
    }

    /** Whether a closed vanilla passage can be opened by an ordinary hand click. */
    public static boolean canOpenByHand(BlockState state) {
        if (state == null) return false;
        Block block = state.getBlock();
        if (block instanceof net.minecraft.world.level.block.DoorBlock door) {
            return door.type().canOpenByHand()
                    && state.hasProperty(net.minecraft.world.level.block.DoorBlock.OPEN)
                    && !state.getValue(net.minecraft.world.level.block.DoorBlock.OPEN);
        }
        if (block instanceof net.minecraft.world.level.block.FenceGateBlock) {
            return state.hasProperty(net.minecraft.world.level.block.FenceGateBlock.OPEN)
                    && !state.getValue(net.minecraft.world.level.block.FenceGateBlock.OPEN);
        }
        return block instanceof net.minecraft.world.level.block.TrapDoorBlock
                && block != Blocks.IRON_TRAPDOOR
                && state.hasProperty(net.minecraft.world.level.block.TrapDoorBlock.OPEN)
                && !state.getValue(net.minecraft.world.level.block.TrapDoorBlock.OPEN);
    }

    /**
     * Check if a block can be broken by the companion (dig-through).
     * Excludes unbreakable blocks like bedrock and obsidian.
     */
    @SuppressWarnings("deprecation")
    public static boolean isBreakable(BlockState state) {
        if (state == null || state.isAir() || !state.getFluidState().isEmpty()) return false;
        Block block = state.getBlock();
        return block != Blocks.BEDROCK
                && block != Blocks.END_PORTAL
                && block != Blocks.END_PORTAL_FRAME
                && block != Blocks.END_GATEWAY
                && block != Blocks.COMMAND_BLOCK
                && block != Blocks.REPEATING_COMMAND_BLOCK
                && block != Blocks.CHAIN_COMMAND_BLOCK
                && block != Blocks.BARRIER
                && block != Blocks.STRUCTURE_BLOCK
                && block != Blocks.STRUCTURE_VOID
                && block != Blocks.JIGSAW;
    }

    /**
     * Check if a position has a solid block below it (can stand there).
     */
    public static boolean hasSolidBelow(Level level, int x, int y, int z) {
        BlockState below = level.getBlockState(new BlockPos(x, y - 1, z));
        return canStandOn(below);
    }

    /**
     * Check if a 2-block-tall space is clear (player can stand there).
     */
    public static boolean isTwoBlockSpaceClear(BlockState feet, BlockState head) {
        return isPassable(feet) && isPassable(head);
    }

    /**
     * Calculate the horizontal distance between two positions.
     */
    public static double horizontalDistance(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * Calculate the 3D distance between two positions.
     */
    public static double distance3D(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Calculate the squared horizontal distance (avoids sqrt).
     */
    public static double horizontalDistanceSq(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    /**
     * Check if the companion can place a block at the given position.
     * The position must be replaceable (air, water, etc).
     */
    public static boolean canPlaceBlockAt(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.getFluidState().is(Fluids.WATER)
                || state.getFluidState().is(Fluids.EMPTY);
    }

    /**
     * Get the position as a center Vec3 for entity distance checks.
     */
    public static Vec3 centerOf(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }
}
