package com.mineagent.engine.pathing.util;

import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Whether breaking a block to get somewhere is safe (SMH plan 70, MA5.1). Two things make it unsafe: a liquid beside or
 * above the block, which pours into the gap (lava burns, water floods the path; a waterlogged block counts, as it pours
 * too), and a tall stack of falling blocks above it (sand, gravel, concrete powder), which drops into the gap and onto
 * whoever stands there. A block below is never the problem: liquid does not climb.
 *
 * <p>The same rules are used while planning (over the path's view of the world) and again right before the break,
 * since the world can change in between.
 */
public final class BreakSafety {
    /** A block lookup; null means unknown (an unloaded chunk), which counts as unsafe. */
    @FunctionalInterface
    public interface BlockAt {
        BlockState at(int x, int y, int z);
    }

    private BreakSafety() {}

    /** Whether breaking the block at x, y, z lets a liquid in from a side or from above. Unknown neighbours count. */
    public static boolean opensToLiquid(BlockAt world, int x, int y, int z) {
        return liquidOrUnknown(world.at(x + 1, y, z))
                || liquidOrUnknown(world.at(x - 1, y, z))
                || liquidOrUnknown(world.at(x, y, z + 1))
                || liquidOrUnknown(world.at(x, y, z - 1))
                || liquidOrUnknown(world.at(x, y + 1, z));
    }

    private static boolean liquidOrUnknown(BlockState state) {
        return state == null || !state.getFluidState().isEmpty();
    }

    /**
     * How many falling blocks stand stacked straight above x, y, z, counted up to one past {@link
     * BreakCost#MAX_FALLING_STACK}. Each would fall into the gap and have to be broken again.
     */
    public static int fallingStackAbove(BlockAt world, int x, int y, int z) {
        int count = 0;
        for (int dy = 1; dy <= BreakCost.MAX_FALLING_STACK + 1; dy++) {
            BlockState above = world.at(x, y + dy, z);
            if (above == null || !(above.getBlock() instanceof FallingBlock)) break;
            count++;
        }
        return count;
    }

    /** Whether the block may be broken to get somewhere at all: no liquid let in, not too tall a stack above. */
    public static boolean safeToBreak(BlockAt world, int x, int y, int z) {
        return !opensToLiquid(world, x, y, z) && fallingStackAbove(world, x, y, z) <= BreakCost.MAX_FALLING_STACK;
    }
}
