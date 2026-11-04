package com.mineagent.engine.task;

import com.mineagent.api.entity.AgentPlayer;
import com.mineagent.api.task.CompanionTask;
import com.mineagent.api.task.TaskRecord;
import com.mineagent.api.task.TaskState;
import com.mineagent.engine.act.Placement;
import com.mineagent.engine.pathing.execute.PlayerNav;
import com.mineagent.engine.pathing.util.BlockHelper;
import com.mineagent.engine.villager.VillagerCell;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;

import java.util.UUID;

/**
 * A villager shut in a cell it walked into (SMH plan 70, its owner: "Figure out how to trap villagers. They move too much
 * to block in easily."): the cell is built round its job site first, open at one end ({@link VillagerCell}); the
 * villager goes to work at its job site as villagers do, and once it stands in the cell, one block shuts it in. It is
 * traded with through the gap over the door, from outside. A villager with no job is trapped the same way at the
 * workstation it is about to claim (it walks up to one to take it). Waits up to a game day for it (villagers work by
 * day); the villager is kept.
 */
public class TrapVillagerTask extends CompanionTask<TrapVillagerTask.TrapRecord> {
    public static final class TrapRecord extends TaskRecord {
        public final String villager;
        /** A workstation it will claim, for a villager with no job yet ({@code BlockPos.asLong}); null: its own job site. */
        public final Long jobSite;

        public TrapRecord(String toolCallId, UUID villager) {
            this(toolCallId, villager, null);
        }

        public TrapRecord(String toolCallId, UUID villager, BlockPos jobSite) {
            super(toolCallId);
            this.villager = villager.toString();
            this.jobSite = jobSite == null ? null : jobSite.asLong();
        }
    }

    private enum Phase { PLAN, BUILD, WAIT, DONE }

    /** A game day at most for it to come and work. */
    static final int LONGEST_WAIT = 24_000;

    private PlayerNav nav;
    private Phase phase = Phase.PLAN;
    private VillagerCell.Layout layout;
    private int ticks, placed, walkTicks;
    private String outcome = "";

    public TrapVillagerTask(AgentPlayer player, TrapRecord record) {
        super(player, record);
    }

    @Override
    protected void onStart() {
        if (nav == null) nav = new PlayerNav(player, TaskContext.navCaches(player));
        record.extendDeadlineTo(Math.max(record.deadline(), TaskContext.serverPlayer(player).level().getGameTime() + LONGEST_WAIT + 6_000L));
    }

    private static BlockPos at(VillagerCell.Pos p) {
        return new BlockPos(p.x(), p.y(), p.z());
    }

    @Override
    protected TaskState onTick() {
        ServerPlayer sp = TaskContext.serverPlayer(player);
        ServerLevel level = sp.serverLevel();
        try {
            ticks++;
            Villager v = villager(level);
            if (v == null) return end(false, "the villager is gone");
            switch (phase) {
                case PLAN -> {
                    // Its own job site; else the workstation it is to claim (a villager walks up to one to take it).
                    var site = v.getBrain().getMemory(MemoryModuleType.JOB_SITE);
                    BlockPos job = site.isPresent() && site.get().dimension() == level.dimension() ? site.get().pos()
                            : record.jobSite != null ? BlockPos.of(record.jobSite) : null;
                    if (job == null) return end(false, "it has no job site to build the cell round");
                    layout = choose(level, job);
                    if (layout == null) return end(false, "there's no room round its job site for a cell");
                    phase = Phase.BUILD;
                    ticks = 0;
                    return TaskState.RUNNING;
                }
                case BUILD -> {
                    if (!Placement.hasSupportBlock(sp)) return end(false, "I have no blocks to build the cell with");
                    if (ticks > 2_400) return end(false, "I couldn't build the cell");
                    boolean waiting = false, underMe = false;
                    for (var w : layout.walls()) {
                        BlockPos p = at(w);
                        if (!level.getBlockState(p).canBeReplaced()) continue;
                        var box = new net.minecraft.world.phys.AABB(p);
                        // Never on itself: it steps off once the rest is built.
                        if (sp.getBoundingBox().intersects(box)) {
                            underMe = true;
                            continue;
                        }
                        // Never on the villager either: that one later, the others first (it may stand by its job site a
                        // long while).
                        if (!level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, box, e -> e != sp).isEmpty()) {
                            waiting = true;
                            continue;
                        }
                        if (p.distToCenterSqr(sp.getEyePosition()) > 4.0 * 4.0) {
                            // From the way in, every wall is in reach and none is underfoot; beside the wall if it can't
                            // get there.
                            BlockPos from = at(layout.way());
                            boolean toWay = ++walkTicks < 400;
                            BlockPos to = toWay ? from : p;
                            if (ticks % 20 == 1 || !nav.isNavigating()) nav.navigateNear(to.getX(), to.getY(), to.getZ(), toWay ? 0 : 2);
                            nav.tick();
                            return TaskState.RUNNING;
                        }
                        nav.cancel();
                        TaskContext.inputDriver(player).clear();
                        if (Placement.placeAnySupportBlock(sp, p)) {
                            placed++;
                            return TaskState.RUNNING; // one a tick, as a player puts them down
                        }
                        waiting = true; // it won't go in yet: the next, and this one again after
                    }
                    if (underMe) {
                        // Only spots it stands on (and any the villager does) left: off them, to the way in (no wall).
                        BlockPos off = at(layout.way());
                        if (ticks % 20 == 1 || !nav.isNavigating()) nav.navigateNear(off.getX(), off.getY(), off.getZ(), 0);
                        nav.tick();
                        return TaskState.RUNNING;
                    }
                    if (waiting) return TaskState.RUNNING; // only spots the villager stands on, or that won't take yet, are left
                    phase = Phase.WAIT;
                    ticks = 0;
                    return TaskState.RUNNING;
                }
                case WAIT -> {
                    BlockPos cell = at(layout.cell()), door = at(layout.door());
                    if (v.blockPosition().equals(cell)) {
                        // In: the door, from where it can reach it (it waits close by for this).
                        if (door.distToCenterSqr(sp.getEyePosition()) <= 4.5 * 4.5 && Placement.placeAnySupportBlock(sp, door)) {
                            var loop = TaskContext.agentLoop(player);
                            if (loop != null) {
                                loop.keepVillager(v.getUUID());
                                loop.noteTrapped(v.getUUID());
                                loop.journal().record("mastery", "a villager shut in its cell by its job site", level.getGameTime(),
                                        level.dimension().location().toString(), cell.getX(), cell.getY(), cell.getZ());
                            }
                            return end(true, null);
                        }
                    }
                    if (ticks > LONGEST_WAIT) return end(false, "it never came to its job site");
                    // Wait out of its way, by the door, where the door is in reach: outside it, a step off the line in.
                    if (waitAt == null) waitAt = waitSpot(level, layout);
                    BlockPos waitAt = this.waitAt;
                    if (sp.blockPosition().distSqr(waitAt) > 2) {
                        if (ticks % 40 == 1 || !nav.isNavigating()) nav.navigateNear(waitAt.getX(), waitAt.getY(), waitAt.getZ(), 1);
                        nav.tick();
                    } else {
                        nav.cancel();
                        TaskContext.inputDriver(player).clear();
                    }
                    return TaskState.RUNNING;
                }
                default -> {
                    return end(true, null);
                }
            }
        } catch (RuntimeException e) {
            return end(false, "something went wrong: " + e.getMessage());
        }
    }

    /**
     * The direction with room: the cell and the way in open two high on a floor, the place to trade from on a floor, and
     * no wall to be where a door, a gate or a trapdoor is (a villager opens those). The one with the fewest walls to put
     * down.
     */
    private static VillagerCell.Layout choose(ServerLevel level, BlockPos job) {
        VillagerCell.Layout best = null;
        int bestCost = Integer.MAX_VALUE;
        for (int[] d : VillagerCell.DIRECTIONS) {
            var l = VillagerCell.plan(job.getX(), job.getY(), job.getZ(), d[0], d[1]);
            if (!open(level, at(l.cell())) || !open(level, at(l.way())) || !open(level, at(l.tradeFrom()))) continue;
            int cost = 0;
            boolean ok = true;
            for (var w : l.walls()) {
                var st = level.getBlockState(at(w));
                if (st.getBlock() instanceof net.minecraft.world.level.block.DoorBlock || st.getBlock() instanceof net.minecraft.world.level.block.FenceGateBlock
                        || st.getBlock() instanceof net.minecraft.world.level.block.TrapDoorBlock) {
                    ok = false;
                    break;
                }
                if (st.canBeReplaced()) cost++;
                else if (BlockHelper.isPassable(st)) {
                    ok = false; // something it can't build over that a villager walks through
                    break;
                }
            }
            if (ok && cost < bestCost) {
                bestCost = cost;
                best = l;
            }
        }
        return best;
    }

    private BlockPos waitAt;

    /** Outside the door and a step to one side (off the way in), else two out on the line. */
    private static BlockPos waitSpot(ServerLevel level, VillagerCell.Layout l) {
        int dx = l.cell().x() - l.jobSite().x(), dz = l.cell().z() - l.jobSite().z();
        BlockPos out = at(l.tradeFrom());
        for (BlockPos p : new BlockPos[] {out.offset(dz, 0, dx), out.offset(-dz, 0, -dx), out.offset(dx, 0, dz)}) {
            if (open(level, p)) return p;
        }
        return out;
    }

    /** Open two high on firm ground. */
    private static boolean open(ServerLevel level, BlockPos p) {
        return BlockHelper.isPassable(level.getBlockState(p)) && BlockHelper.isPassable(level.getBlockState(p.above()))
                && BlockHelper.canStandOn(level.getBlockState(p.below())) && level.getFluidState(p).isEmpty();
    }

    private Villager villager(ServerLevel level) {
        var e = level.getEntity(UUID.fromString(record.villager));
        return e instanceof Villager v && v.isAlive() ? v : null;
    }

    private TaskState end(boolean ok, String why) {
        if (nav != null) nav.cancel();
        TaskContext.inputDriver(player).clear();
        phase = Phase.DONE;
        outcome = ok ? "The villager walked into its cell by its job site and is shut in; I trade with it through the gap over the door"
                : (why == null ? "it didn't work" : why);
        var loop = TaskContext.agentLoop(player);
        if (loop != null) loop.say(ok ? outcome + "." : "I couldn't shut the villager in: " + outcome + ".");
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
