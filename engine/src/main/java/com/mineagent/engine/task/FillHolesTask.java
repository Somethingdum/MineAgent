package com.mineagent.engine.task;

import com.mineagent.api.entity.AgentPlayer;
import com.mineagent.api.task.CompanionTask;
import com.mineagent.api.task.TaskRecord;
import com.mineagent.api.task.TaskState;
import com.mineagent.engine.act.Placement;
import com.mineagent.engine.pathing.execute.PlayerNav;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Leaving no trace (SMH plan 70, MA9.4): the holes it dug near home filled, each with the block that was there when it
 * carries one, else plain earth or rock (dirt, cobblestone), as a player puts a block back. A hole someone filled, or
 * one it can't get to, is let be.
 */
public class FillHolesTask extends CompanionTask<FillHolesTask.FillRecord> {
    public static final class FillRecord extends TaskRecord {
        public final List<int[]> holes;

        public FillRecord(String toolCallId, List<int[]> holes) {
            super(toolCallId);
            this.holes = List.copyOf(holes);
        }
    }

    /** What it fills with when it lacks the block itself: the plainest first. */
    static final List<String> PLAIN = List.of("minecraft:dirt", "minecraft:cobblestone", "minecraft:cobbled_deepslate", "minecraft:netherrack",
            "minecraft:andesite", "minecraft:diorite", "minecraft:granite", "minecraft:gravel", "minecraft:sand");

    private PlayerNav nav;
    private int index, ticks, filled;
    private String outcome = "";

    public FillHolesTask(AgentPlayer player, FillRecord record) {
        super(player, record);
    }

    @Override
    protected void onStart() {
        if (nav == null) nav = new PlayerNav(player, TaskContext.navCaches(player));
    }

    @Override
    protected TaskState onTick() {
        ServerPlayer sp = TaskContext.serverPlayer(player);
        ServerLevel level = sp.serverLevel();
        var loop = TaskContext.agentLoop(player);
        String dim = level.dimension().location().toString();
        try {
            if (index >= record.holes.size()) return end(true);
            int[] h = record.holes.get(index);
            BlockPos p = new BlockPos(h[0], h[1], h[2]);
            if (!level.getBlockState(p).canBeReplaced()) {
                if (loop != null) loop.holes().filled(dim, p.getX(), p.getY(), p.getZ());
                next();
                return TaskState.RUNNING;
            }
            String was = loop == null ? null : loop.holes().near(dim, p.getX(), p.getY(), p.getZ(), 0.5, 1).stream().findFirst()
                    .map(com.mineagent.engine.world.Holes.Hole::block).orElse(null);
            Item with = filler(sp, was);
            if (with == null) {
                outcome = "nothing plain to fill them with";
                return end(filled > 0);
            }
            if (p.distToCenterSqr(sp.getEyePosition()) > 4.0 * 4.0) {
                if (++ticks > 400) {
                    next(); // can't get to that one
                    return TaskState.RUNNING;
                }
                if (ticks % 20 == 1) nav.navigateNear(p.getX(), p.getY(), p.getZ(), 2);
                nav.tick();
                return TaskState.RUNNING;
            }
            nav.cancel();
            TaskContext.inputDriver(player).clear();
            // Not where it stands: a step aside first.
            if (sp.getBoundingBox().intersects(new net.minecraft.world.phys.AABB(p))) {
                if (++ticks > 200) next();
                else if (ticks % 20 == 1) nav.navigateNear(p.getX() + 2, p.getY(), p.getZ(), 1);
                nav.tick();
                return TaskState.RUNNING;
            }
            var state = ((BlockItem) with).getBlock().defaultBlockState();
            if (Placement.placeBlock(sp, p, state)) {
                filled++;
                if (loop != null) loop.holes().filled(dim, p.getX(), p.getY(), p.getZ());
                next();
            } else if (++ticks > 40) {
                next();
            }
            return TaskState.RUNNING;
        } catch (RuntimeException e) {
            outcome = "something went wrong: " + e.getMessage();
            return end(false);
        }
    }

    private void next() {
        index++;
        ticks = 0;
    }

    /** The block that was there if it carries it (grass gives dirt), else the plainest block it has. */
    private static Item filler(ServerPlayer sp, String was) {
        List<String> order = new ArrayList<>();
        if (was != null) {
            order.add(com.mineagent.engine.world.Holes.filler(was));
            order.add(was);
        }
        order.addAll(PLAIN);
        var inv = sp.getInventory();
        for (String id : order) {
            Item it = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
            if (!(it instanceof BlockItem)) continue;
            for (int i = 0; i < Math.min(36, inv.getContainerSize()); i++) {
                ItemStack st = inv.getItem(i);
                if (st.is(it)) return it;
            }
        }
        return null;
    }

    private TaskState end(boolean ok) {
        if (nav != null) nav.cancel();
        TaskContext.inputDriver(player).clear();
        if (filled > 0) outcome = "filled " + filled + (filled == 1 ? " hole" : " holes") + " near home" + (outcome.isEmpty() ? "" : "; " + outcome);
        var loop = TaskContext.agentLoop(player);
        if (loop != null && filled > 0) loop.say("Filled " + filled + (filled == 1 ? " hole" : " holes") + " I dug near home.");
        return ok ? TaskState.SUCCESS : TaskState.FAILED;
    }

    @Override
    protected String successMessage() {
        return "{\"success\":true,\"note\":\"" + outcome.replace("\"", "'") + "\"}";
    }

    @Override
    protected String failureMessage() {
        return "{\"success\":false,\"note\":\"" + outcome.replace("\"", "'") + "\"}";
    }

    @Override
    protected void onInterrupt() {
        if (nav != null) nav.cancel();
        TaskContext.inputDriver(player).clear();
    }
}
