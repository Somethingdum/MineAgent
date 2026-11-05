// Ported from Mindcraft (github.com/mindcraft-bots/mindcraft, MIT, Copyright (c) 2024 Kolby Nottingham) at e1c1fc3:
// src/agent/npc/build_goal.js (BuildGoal.executeNext) and world.getNearestFreeSpace, as one of AltoClef's tasks: every
// tick it walks the blueprint as Mindcraft does (layers bottom up, rows, columns, turned to its orientation), and for
// the first block that is not what the blueprint asks it breaks what is there or places what belongs (AltoClef's
// DestroyBlockTask and PlaceBlockTask, Mindcraft's skills.breakBlockAt and skills.placeBlock); a block it carries none
// of counts as missing, and the missing ones are what its routine fetches next (Mindcraft's temp goals). Changes for
// the companion are marked SMH.
package com.mineagent.engine.agent.npc;

import com.mineagent.sbe.altoclef.tasks.construction.DestroyBlockTask;
import com.mineagent.sbe.altoclef.tasks.construction.PlaceBlockTask;
import com.mineagent.sbe.altoclef.tasksystem.Task;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

public class BuildGoalTask extends Task {

    private final Blueprint goal;
    private BlockPos position;
    private Integer orientation;
    /** What the last walk over the blueprint found it short of, by item name (Mindcraft's res.missing). */
    private final Map<String, Integer> missing = new LinkedHashMap<>();
    /** Whether the last walk found anything to do (Mindcraft's res.acted). */
    private boolean acted = true;

    public BuildGoalTask(Blueprint goal, BlockPos position, Integer orientation) {
        this.goal = goal;
        this.position = position;
        this.orientation = orientation;
    }

    public BlockPos position() {
        return position;
    }

    public Integer orientation() {
        return orientation;
    }

    public Map<String, Integer> missing() {
        return missing;
    }

    /** Built as the blueprint asks: nothing left to break or place. */
    public boolean built() {
        return !acted && missing.isEmpty();
    }

    @Override
    protected void onStart() {
        ServerPlayer player = controller.getPlayer();
        if (position == null) {
            // Mindcraft: the nearest free space as wide as the blueprint, narrower by one each try.
            for (int x = 0; x < goal.sizeX() - 1 && position == null; x++) {
                position = nearestFreeSpace(controller.getWorld(), player.blockPosition(), goal.sizeX() - x, 16);
            }
            if (position == null) position = player.blockPosition();
        }
        if (orientation == null) orientation = new Random().nextInt(4);
    }

    @Override
    protected Task onTick() {
        ServerLevel level = controller.getWorld();
        ServerPlayer player = controller.getPlayer();
        Map<String, Integer> inventory = NpcUtils.inventoryCounts(player);
        missing.clear();
        acted = false;
        int sizeX = goal.sizeX(), sizeZ = goal.sizeZ(), sizeY = goal.sizeY();
        for (int y = goal.offset(); y < sizeY + goal.offset(); y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    int[] r = NpcUtils.rotateXZ(x, z, orientation, sizeX, sizeZ);
                    int ry = y - goal.offset();
                    String blockName = goal.blocks()[ry][r[1]][r[0]];
                    if (blockName == null || blockName.isEmpty()) continue;
                    BlockPos worldPos = position.offset(x, y, z);
                    if (level.getChunkSource().getChunkNow(worldPos.getX() >> 4, worldPos.getZ() >> 4) == null) continue;
                    BlockState current = level.getBlockState(worldPos);
                    if (satisfied(blockName, current, level, worldPos)) continue;
                    acted = true;
                    if (!current.isAir() && current.getFluidState().isEmpty()) {
                        setDebugState("Clearing " + worldPos.toShortString() + " for the " + goal.name());
                        return new DestroyBlockTask(worldPos);
                    }
                    if (!blockName.equals("air")) {
                        String typed = NpcUtils.typeOfGeneric(player, blockName);
                        if (inventory.getOrDefault(typed, 0) > 0) {
                            Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.withDefaultNamespace(typed));
                            if (block != Blocks.AIR) {
                                setDebugState("Placing " + typed + " at " + worldPos.toShortString() + " for the " + goal.name());
                                return new PlaceBlockTask(worldPos, block);
                            }
                        }
                        missing.merge(typed, 1, Integer::sum);
                    }
                }
            }
        }
        setDebugState(missing.isEmpty() ? "The " + goal.name() + " stands" : "Short of " + missing + " for the " + goal.name());
        return null;
    }

    /**
     * Mindcraft's blockSatisfied; SMH: a "dirt" wall is any full, solid block already there that is not a fitting (its
     * shabby hut keeps the stone or wood it is dug into rather than breaking it to put dirt in its place).
     */
    private static boolean satisfied(String blockName, BlockState current, ServerLevel level, BlockPos pos) {
        if (NpcUtils.blockSatisfied(blockName, current)) return true;
        return blockName.equals("dirt") && current.getFluidState().isEmpty() && current.isCollisionShapeFullBlock(level, pos)
                && !current.is(BlockTags.LEAVES);
    }

    /** Mindcraft's world.getNearestFreeSpace: the nearest size by size of air with diggable ground under it. */
    static BlockPos nearestFreeSpace(ServerLevel level, BlockPos from, int size, int distance) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(from.offset(-distance, -distance, -distance), from.offset(distance, distance, distance))) {
            double d = p.distSqr(from);
            if (d >= bestD || d > (double) distance * distance) continue;
            if (free(level, p, size)) {
                bestD = d;
                best = p.immutable();
            }
        }
        return best;
    }

    private static boolean free(ServerLevel level, BlockPos corner, int size) {
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                BlockPos top = corner.offset(x, 0, z), bottom = top.below();
                if (level.getChunkSource().getChunkNow(top.getX() >> 4, top.getZ() >> 4) == null) return false;
                BlockState t = level.getBlockState(top), b = level.getBlockState(bottom);
                if (!t.isAir() || b.isAir() || !b.getFluidState().isEmpty() || b.getDestroySpeed(level, bottom) < 0
                        || b.is(BlockTags.LEAVES)) return false;
            }
        }
        return true;
    }

    @Override
    protected void onStop(Task interruptTask) {
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof BuildGoalTask b && b.goal.name().equals(goal.name());
    }

    @Override
    protected String toDebugString() {
        return "Building the " + goal.name() + (position == null ? "" : " at " + position.toShortString());
    }
}
