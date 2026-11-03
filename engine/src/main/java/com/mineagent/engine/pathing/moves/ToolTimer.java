package com.mineagent.engine.pathing.moves;

import com.mineagent.engine.pathing.util.BreakCost;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ticks to break a block with the best tool the companion carries (SMH plan 70, MA5.1), as {@code
 * task/BlockDigger#prepareBestTool} will choose it: a tool the block drops with first, then the fastest, never one about
 * to break. The tools are copied when a search starts, so the search reads no live inventory.
 */
public final class ToolTimer implements CalculationContext.BreakTimer {
    private final Level level;
    private final List<ItemStack> tools;
    private final Map<Block, Integer> byBlock = new HashMap<>();

    private ToolTimer(Level level, List<ItemStack> tools) {
        this.level = level;
        this.tools = tools;
    }

    public static ToolTimer of(ServerPlayer player) {
        List<ItemStack> tools = new ArrayList<>();
        var inventory = player.getInventory();
        int limit = Math.min(36, inventory.getContainerSize());
        for (int slot = 0; slot < limit; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) continue;
            if (stack.isDamageableItem() && stack.getMaxDamage() - stack.getDamageValue() <= 1) continue;
            tools.add(stack.copy());
        }
        return new ToolTimer(player.level(), tools);
    }

    @Override
    public int ticks(BlockState state, int x, int y, int z) {
        return byBlock.computeIfAbsent(state.getBlock(), b -> compute(state, x, y, z));
    }

    private int compute(BlockState state, int x, int y, int z) {
        float hardness = state.getDestroySpeed(level, new BlockPos(x, y, z));
        boolean needsTool = state.requiresCorrectToolForDrops();
        int best = BreakCost.ticks(hardness, 1.0, !needsTool);
        for (ItemStack tool : tools) {
            boolean drops = !needsTool || tool.isCorrectToolForDrops(state);
            best = Math.min(best, BreakCost.ticks(hardness, tool.getDestroySpeed(state), drops));
        }
        return best;
    }
}
