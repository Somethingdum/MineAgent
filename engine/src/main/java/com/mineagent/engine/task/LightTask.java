package com.mineagent.engine.task;

import com.mineagent.api.entity.AgentPlayer;
import com.mineagent.api.task.CompanionTask;
import com.mineagent.api.task.TaskRecord;
import com.mineagent.api.task.TaskState;
import com.mineagent.engine.act.Placement;
import com.mineagent.engine.pathing.execute.PlayerNav;
import com.mineagent.engine.pathing.util.BlockHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;

/**
 * "Light up the area" (SMH plan 70, MA9.4): torches put down until no spot within the radius that a mob could stand
 * on is dark (block light 0, where monsters spawn), the darkest spot furthest from light first. Never in your AFK
 * farm's spawning space. Torches made first if it has none (from what it carries or sees).
 */
public class LightTask extends CompanionTask<LightTask.LightRecord> {
    public static final class LightRecord extends TaskRecord {
        public final BlockPos center;
        public final int radius;

        public LightRecord(String toolCallId, BlockPos center, int radius) {
            super(toolCallId);
            this.center = center.immutable();
            this.radius = Math.max(2, Math.min(24, radius));
        }
    }

    private PlayerNav nav;
    private BlockPos target;
    private int targetTicks;
    private int placed;
    private int settle;
    private CompanionTask<?> sub;
    private boolean triedTorches;
    private String outcome = "";

    public LightTask(AgentPlayer player, LightRecord record) {
        super(player, record);
    }

    @Override
    protected void onStart() {
        if (nav == null) nav = new PlayerNav(player, TaskContext.navCaches(player));
        target = null;
        if (sub != null) sub.resume();
    }

    @Override
    protected TaskState onTick() {
        ServerPlayer sp = TaskContext.serverPlayer(player);
        ServerLevel level = sp.serverLevel();
        var loop = TaskContext.agentLoop(player);
        if (sub != null) {
            TaskState st = sub.tick();
            if (st == TaskState.RUNNING) return TaskState.RUNNING;
            sub = null;
        }
        if (settle > 0) {
            settle--; // light spreads a moment after a torch goes down
            return TaskState.RUNNING;
        }
        if (target == null) {
            target = darkest(level, sp);
            targetTicks = 0;
            if (target == null) return done(sp, true);
        }
        if (ProductionTask.count(sp, Items.TORCH) == 0) {
            if (!triedTorches) {
                triedTorches = true;
                sub = new ProductionTask(player, new ProductionTask.ProduceRecord(record.toolCallId() + ":torches", "minecraft:torch",
                        16, null, false, true));
                sub.start();
                return TaskState.RUNNING;
            }
            return done(sp, false);
        }
        if (++targetTicks > 400) {
            couldNot.add(target.asLong()); // out of its reach: the next darkest
            target = null;
            return TaskState.RUNNING;
        }
        // No nearer after five seconds (behind a fence, over water): not that one.
        double d = target.distToCenterSqr(sp.getEyePosition());
        if (targetTicks == 1 || d < closest - 1.0) {
            closest = d;
            closerAt = targetTicks;
        } else if (targetTicks - closerAt > 100) {
            couldNot.add(target.asLong());
            target = null;
            return TaskState.RUNNING;
        }
        if (target.distToCenterSqr(sp.getEyePosition()) > 4.0 * 4.0) {
            if (targetTicks % 20 == 1) nav.navigateNear(target.getX(), target.getY(), target.getZ(), 2);
            nav.tick();
            return TaskState.RUNNING;
        }
        nav.cancel();
        TaskContext.inputDriver(player).clear();
        if (Placement.placeBlock(sp, target, Blocks.TORCH.defaultBlockState())) {
            placed++;
            settle = 4;
            missed = 0;
        } else {
            couldNot.add(target.asLong()); // the same spot is not tried again, round and round
            if (++missed > 40) return done(sp, true);
        }
        target = null;
        return TaskState.RUNNING;
    }

    /** The dark spot a mob could stand on furthest from any light, within the radius; null when none is dark. */
    private BlockPos darkest(ServerLevel level, ServerPlayer sp) {
        String dim = level.dimension().location().toString();
        BlockPos c = record.center;
        int r = record.radius;
        BlockPos best = null;
        int bestScore = Integer.MIN_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-r, -3, -r), c.offset(r, 3, r))) {
            if (p.distSqr(c) > (double) r * r) continue;
            if (!spawnable(level, p) || level.getBrightness(LightLayer.BLOCK, p) > 0) continue;
            if (com.mineagent.engine.world.AfkFarms.protects(dim, p.getX(), p.getY(), p.getZ())) continue;
            if (couldNot.contains(p.asLong())) continue; // tried, and no torch would go there
            // Furthest from the light already there: the spot whose neighbours are darkest too.
            int score = -(int) Math.round(p.distSqr(sp.blockPosition()) / 64.0);
            for (BlockPos q : new BlockPos[] {p.east(4), p.west(4), p.north(4), p.south(4)}) {
                if (level.getBrightness(LightLayer.BLOCK, q) == 0) score += 4;
            }
            if (score > bestScore) {
                bestScore = score;
                best = p.immutable();
            }
        }
        return best;
    }

    /** A spot a mob could spawn on: solid ground, room for feet and head, no fluid. */
    public static boolean spawnable(ServerLevel level, BlockPos p) {
        var feet = level.getBlockState(p);
        return feet.isAir() && level.getBlockState(p.above()).isAir() && level.getFluidState(p).isEmpty()
                && BlockHelper.canStandOn(level.getBlockState(p.below()));
    }

    /** Spots it tried and couldn't light, and the misses in a row. */
    private final java.util.Set<Long> couldNot = new java.util.HashSet<>();
    /** How near it has come to the spot it is going to light, and when it last came nearer. */
    private double closest;
    private int closerAt;
    private int missed;

    private TaskState done(ServerPlayer sp, boolean lit) {
        nav.cancel();
        TaskContext.inputDriver(player).clear();
        var loop = TaskContext.agentLoop(player);
        outcome = lit ? "lit: " + placed + " torches, no dark spot within " + record.radius + " blocks"
                + (couldNot.isEmpty() ? "" : " but " + couldNot.size() + " it couldn't reach")
                : "out of torches after " + placed + ", and couldn't make more here";
        if (loop != null) loop.say(lit ? "Lit up: " + placed + (placed == 1 ? " torch" : " torches") + "; no dark spot within "
                + record.radius + " blocks" + (couldNot.isEmpty() ? "." : " but " + couldNot.size() + " I couldn't reach.")
                : "I'm out of torches (" + placed + " down) and couldn't make more here.");
        return lit ? TaskState.SUCCESS : TaskState.FAILED;
    }

    @Override
    protected String successMessage() {
        return "{\"success\":true,\"note\":\"" + outcome + "\"}";
    }

    @Override
    protected String failureMessage() {
        return "{\"success\":false,\"note\":\"" + outcome + "\"}";
    }

    @Override
    protected void onInterrupt() {
        if (sub != null) sub.interrupt();
        if (nav != null) nav.cancel();
        TaskContext.inputDriver(player).clear();
    }
}
