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

    /** Where its inside is: the blueprint's open floor cell nearest its middle, at its lowest open layer. */
    public BlockPos inside() {
        if (position == null || orientation == null) return null;
        int sizeX = goal.sizeX(), sizeZ = goal.sizeZ();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (int ry = 1; ry < goal.sizeY() - 1 && best == null; ry++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    int[] r = NpcUtils.rotateXZ(x, z, orientation, sizeX, sizeZ);
                    String here = goal.blocks()[ry][r[1]][r[0]], under = goal.blocks()[ry - 1][r[1]][r[0]],
                            over = goal.blocks()[ry + 1][r[1]][r[0]];
                    if (!here.equals("air") || !over.equals("air") || under.isEmpty() || under.equals("air")) continue;
                    double d = Math.pow(x - (sizeX - 1) / 2.0, 2) + Math.pow(z - (sizeZ - 1) / 2.0, 2);
                    if (d < bestD) {
                        bestD = d;
                        best = position.offset(x, ry + goal.offset(), z);
                    }
                }
            }
        }
        return best;
    }

    /** Whether a spot is inside its walls (Mindcraft's currentBuilding: within the blueprint's bounds). */
    public boolean holds(BlockPos p) {
        if (position == null) return false;
        int dx = p.getX() - position.getX(), dy = p.getY() - position.getY() - goal.offset(), dz = p.getZ() - position.getZ();
        return dx >= 0 && dz >= 0 && dy >= 0 && dx < goal.sizeX() && dz < goal.sizeZ() && dy < goal.sizeY();
    }

    /** Whether a spot is one of its blocks that stands as the blueprint asks (kept from being broken for blocks). */
    public boolean standingPart(ServerLevel level, BlockPos p) {
        if (!holds(p) || orientation == null) return false;
        int x = p.getX() - position.getX(), y = p.getY() - position.getY() - goal.offset(), z = p.getZ() - position.getZ();
        int[] r = NpcUtils.rotateXZ(x, z, orientation, goal.sizeX(), goal.sizeZ());
        String name = goal.blocks()[y][r[1]][r[0]];
        if (name.isEmpty() || name.equals("air")) return false;
        var chunk = level.getChunkSource().getChunkNow(p.getX() >> 4, p.getZ() >> 4);
        return chunk != null && satisfied(name, chunk.getBlockState(p), level, p);
    }

    /** The door of the build, if its blueprint has one and it stands (Mindcraft's getBuildingDoor). */
    public BlockPos door(ServerLevel level) {
        if (position == null || orientation == null) return null;
        for (int y = 0; y < goal.sizeY(); y++) {
            for (int z = 0; z < goal.sizeZ(); z++) {
                for (int x = 0; x < goal.sizeX(); x++) {
                    int[] r = NpcUtils.rotateXZ(x, z, orientation, goal.sizeX(), goal.sizeZ());
                    if (!goal.blocks()[y][r[1]][r[0]].equals("door")) continue;
                    return position.offset(x, y + goal.offset(), z);
                }
            }
        }
        return null;
    }

    public Blueprint goal() {
        return goal;
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
        return plan();
    }

    /** The walk over the blueprint (Mindcraft's executeNext): the step to take now, or null; missing and acted set. */
    public Task plan() {
        if (position == null || orientation == null) onStart();
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
