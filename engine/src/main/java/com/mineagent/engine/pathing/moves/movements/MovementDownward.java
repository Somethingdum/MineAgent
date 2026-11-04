package com.mineagent.engine.pathing.moves.movements;

import com.mineagent.api.entity.AgentPlayer;
import com.mineagent.engine.pathing.moves.*;
import com.mineagent.engine.pathing.util.BlockHelper;
import com.mineagent.engine.task.TaskContext;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Straight down through the block under its feet: Baritone's {@code MovementDownward} (LGPL-3.0, as this mod; see
 * THIRD_PARTY_NOTICES.md), its rule and its cost kept: ground to land on two under it, the block under it broken (its
 * mining time, as {@link MovementHelper#costOfBreaking} prices it, with every one of that rule's refusals: nothing
 * someone built, nothing that lets liquid in, no falling stack), then a fall of one. Only when its walk may dig
 * through: the way down off a tree top, a pillar or a ledge, where every other move needs a fall it won't take (the
 * three-world run's second world stood on a spruce top through two of its three days).
 */
public class MovementDownward extends Movement {
    private final Input tickInput = new Input();

    public MovementDownward(int x, int y, int z) {
        super(x, y, z, x, y - 1, z);
    }

    @Override
    public double calculateCost(CalculationContext ctx) {
        if (!ctx.allowDigThrough()) return cost = Double.POSITIVE_INFINITY;
        BlockState under = ctx.getBlockState(srcX, srcY - 1, srcZ);
        BlockState ground = ctx.getBlockState(srcX, srcY - 2, srcZ);
        // Something to dig (open below is a fall, its own move), and ground to land on under it.
        if (under == null || ground == null || BlockHelper.isPassable(under) || !BlockHelper.canStandOn(ground)) {
            return cost = Double.POSITIVE_INFINITY;
        }
        double dig = MovementHelper.costOfBreaking(ctx, srcX, srcY - 1, srcZ);
        if (!Double.isFinite(dig)) return cost = Double.POSITIVE_INFINITY;
        return cost = dig + ActionCosts.FALL_1;
    }

    @Override
    public Input getTickInput(AgentPlayer player) {
        tickInput.clear();
        var sp = TaskContext.serverPlayer(player);
        // Over the middle of its block first (as Baritone waits until it is within 0.2): on its edge it would stand on
        // the block beside the hole.
        double dx = srcX + 0.5 - sp.getX(), dz = srcZ + 0.5 - sp.getZ();
        if (dx * dx + dz * dz > 0.04 && sp.blockPosition().getY() == srcY) {
            moveToward(player, srcX, srcZ, tickInput);
            return tickInput;
        }
        // The block under it broken; it drops into the cell.
        ensureClearance(player, new BlockPos(dstX, dstY, dstZ));
        return tickInput;
    }

    @Override
    public boolean isFinished(AgentPlayer player) {
        var sp = TaskContext.serverPlayer(player);
        return sp.onGround() && sp.blockPosition().getY() == dstY && isAtDestination(player);
    }
}
