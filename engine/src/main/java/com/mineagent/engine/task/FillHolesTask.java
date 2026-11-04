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
 * Leaving no trace (SMH plan 70, MA9.4): the holes it dug near home, and the craters creepers blew there, filled, each
 * with the block kept for it when it carries one (for a crater, what the ground round it is mostly made of on top), else
 * plain earth or rock (dirt, cobblestone), as a player puts a block back; what a blast dropped is picked up first. A
 * hole someone filled, or one it can't get to, is let be.
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
            if (gathering(sp, level)) return TaskState.RUNNING;
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
            // Where it stands: the hole its feet are in (the last of a crater, filled round it) is filled as a player
            // gets out of a pit, a jump and the block put under its feet; else a step aside first.
            if (sp.getBoundingBox().intersects(new net.minecraft.world.phys.AABB(p))) {
                BlockPos feet = sp.blockPosition();
                if (feet.getX() == p.getX() && feet.getZ() == p.getZ() && feet.getY() <= p.getY() + 1 && ticks < 120) {
                    ticks++;
                    sp.setXRot(90.0f);
                    var input = TaskContext.inputDriver(player);
                    input.setForward(0.0f);
                    input.setStrafe(0.0f);
                    input.setJumping(true);
                    if (!sp.onGround() && sp.getY() >= p.getY() + 1.0 && level.getBlockState(p).canBeReplaced()) {
                        var state = ((BlockItem) with).getBlock().defaultBlockState();
                        if (Placement.placeBlock(sp, p, state)) {
                            input.setJumping(false);
                            filled++;
                            if (loop != null) loop.holes().filled(dim, p.getX(), p.getY(), p.getZ());
                            next();
                        }
                    }
                    return TaskState.RUNNING;
                }
                if (++ticks > 320) next();
                else if (ticks % 20 == 1) nav.navigateNear(p.getX() + 2, p.getY(), p.getZ(), 1);
                nav.tick();
                return TaskState.RUNNING;
            }
            var state = ((BlockItem) with).getBlock().defaultBlockState();
            // Sand or gravel over a hole not yet filled would fall into it: that one waits for the next round.
            if (state.getBlock() instanceof net.minecraft.world.level.block.FallingBlock && level.getBlockState(p.below()).canBeReplaced()) {
                next();
                return TaskState.RUNNING;
            }
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

    /** Ticks spent picking up what lies round the holes (a blast drops some of what it blew), and the drops left. */
    private int gatherTicks;
    private boolean gathered;

    /**
     * First, the blocks lying round the holes that would fill them (a creeper's blast drops a share of what it took),
     * picked up as a player would, by walking over them; ten seconds at most. True while it does.
     */
    private boolean gathering(ServerPlayer sp, ServerLevel level) {
        if (gathered) return false;
        int[] first = record.holes.get(0);
        var around = new net.minecraft.world.phys.AABB(first[0], first[1], first[2], first[0] + 1, first[1] + 1, first[2] + 1).inflate(8, 4, 8);
        net.minecraft.world.entity.item.ItemEntity near = null;
        for (var e : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, around,
                e -> e.isAlive() && e.getItem().getItem() instanceof BlockItem bi
                        && com.mineagent.engine.world.Craters.ground(BuiltInRegistries.BLOCK.getKey(bi.getBlock()).toString())
                        && com.mineagent.engine.survival.Manners.mayTake(e, sp))) {
            if (near == null || e.distanceToSqr(sp) < near.distanceToSqr(sp)) near = e;
        }
        if (near == null || ++gatherTicks > 200) {
            gathered = true;
            nav.cancel();
            return false;
        }
        if (gatherTicks % 10 == 1) nav.navigateTo(near.getBlockX(), near.getBlockY(), near.getBlockZ());
        nav.tick();
        return true;
    }

    private void next() {
        index++;
        ticks = 0;
    }

    /** The block that was there if it carries it (grass gives dirt), else the plainest block it has. */
    private static Item filler(ServerPlayer sp, String was) {
        List<String> order = new ArrayList<>();
        if (was != null) {
            order.add(was); // the block itself when it carries it (stone for stone), else what a player puts back
            order.add(com.mineagent.engine.world.Holes.filler(was));
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
        if (loop != null && filled > 0) loop.say("Filled " + filled + (filled == 1 ? " hole" : " holes") + " near home.");
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
