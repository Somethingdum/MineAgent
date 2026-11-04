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
        if (nav == null) {
            nav = new PlayerNav(player, TaskContext.navCaches(player));
            nav.core().allowWayOut(false); // round its own cell, never through the ground
        }
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
                    // Never with the cell where the villager stands: walls can't go in where its body reaches, and the
                    // cell's sides would be those. Where it works, if that is a wall's spot, is turned to once the rest
                    // is up (its sides are walls of this cell then, whichever spot by the job site it is).
                    layout = choose(level, job, v.blockPosition());
                    if (layout == null) return end(false, "there's no room round its job site for a cell");
                    phase = Phase.BUILD;
                    ticks = 0;
                    return TaskState.RUNNING;
                }
                case BUILD -> {
                    if (!Placement.hasSupportBlock(sp)) return end(false, "I have no blocks to build the cell with");
                    if (ticks > 6_000) return end(false, "I couldn't build the cell");
                    // It took its job before the cell was up and works from a spot beside it that is to be a wall (a
                    // librarian stays a long while): that spot is the cell, the cell turned to it, what is up kept.
                    BlockPos vAt = v.blockPosition();
                    if (!vAt.equals(at(layout.cell())) && layout.walls().contains(new VillagerCell.Pos(vAt.getX(), vAt.getY(), vAt.getZ()))) {
                        // Once its spot is all that is left to build.
                        if (onlyItsSpotLeft) {
                            var turned = toward(level, at(layout.jobSite()), vAt);
                            if (turned != null) {
                                layout = turned;
                                waitAt = null;
                                stands = null;
                                standIndex = 0;
                                doorSpots = null;
                            }
                            offCell = 0;
                        }
                    } else if (offCell > 0) {
                        offCell--;
                    }
                    onlyItsSpotLeft = false;
                    // On its way to a spot to build from: there first.
                    if (goingTo != null) {
                        if (sp.blockPosition().equals(goingTo) || ++goTicks > 300) {
                            goingTo = null;
                            nav.cancel();
                            TaskContext.inputDriver(player).clear();
                        } else {
                            if (goTicks % 20 == 1 || !nav.isNavigating()) nav.navigateNear(goingTo.getX(), goingTo.getY(), goingTo.getZ(), 0);
                            nav.tick();
                            return TaskState.RUNNING;
                        }
                    }
                    boolean waiting = false, underMe = false, far = false, refused = false;
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
                        if (p.distToCenterSqr(sp.getEyePosition()) > 4.4 * 4.4) {
                            far = true;
                            continue;
                        }
                        var input = TaskContext.inputDriver(player);
                        if (Placement.placeAnySupportBlock(sp, p)) {
                            placed++;
                            input.setJumping(false);
                            jumpFor = null;
                            return TaskState.RUNNING; // one a tick, as a player puts them down
                        }
                        // Over its eyes (a roof on a wall two high): what it goes against is a top face, seen only from
                        // above; from the top of a jump, as a player puts one there. A few jumps, then the next spot.
                        if (sp.onGround()) groundY = sp.getBlockY();
                        if (p.getY() >= groundY + 2) {
                            if (!p.equals(jumpFor)) {
                                jumpFor = p.immutable();
                                jumpTicks = 0;
                            }
                            if (++jumpTicks <= 60) {
                                input.setJumping(true);
                                return TaskState.RUNNING;
                            }
                            input.setJumping(false);
                        }
                        refused = true; // no face to put it against in sight from here
                    }
                    if (Boolean.getBoolean("mineagent.traceWork") && ticks >= traceAt) {
                        traceAt = ticks + 200;
                        int left = 0;
                        StringBuilder which = new StringBuilder();
                        for (var w : layout.walls()) {
                            if (level.getBlockState(at(w)).canBeReplaced()) {
                                left++;
                                if (which.length() < 160) which.append(' ').append(w.x()).append(',').append(w.y()).append(',').append(w.z());
                            }
                        }
                        System.out.println("[MineAgent] cell: job site " + at(layout.jobSite()).toShortString() + ", cell " + at(layout.cell()).toShortString()
                                + ", " + placed + " put in, " + left + " left:" + which + "; it at " + sp.blockPosition().toShortString() + ", the villager at "
                                + v.blockPosition().toShortString() + "; far " + far + ", under it " + underMe + ", refused " + refused + ", waiting " + waiting
                                + ", going to " + (goingTo == null ? "none" : goingTo.toShortString()));
                    }
                    if (far || underMe || refused) {
                        // Somewhere else to build from: the way in first (every wall in reach, none underfoot), then the
                        // other spots round the cell in turn, till each block has a face in sight from one.
                        goingTo = nextStand(level, sp);
                        goTicks = 0;
                        jumpFor = null;
                        TaskContext.inputDriver(player).setJumping(false);
                        if (goingTo == null) return end(false, "there's nowhere to stand to build the cell");
                        return TaskState.RUNNING;
                    }
                    if (waiting) {
                        onlyItsSpotLeft = true; // looked at next tick: turned to where it stands, if that is beside its job site
                        return TaskState.RUNNING; // only spots the villager stands on are left
                    }
                    phase = Phase.WAIT;
                    ticks = 0;
                    traceAt = 0;
                    return TaskState.RUNNING;
                }
                case WAIT -> {
                    BlockPos cell = at(layout.cell()), door = at(layout.door()), way = at(layout.way());
                    // Shoving it in: to the way in (ten seconds at most), against it there a moment, then back to its
                    // spot by the door.
                    if (nudging > 0) {
                        nudging--;
                        boolean there = sp.blockPosition().equals(door);
                        if (there && nudging > 15) nudging = 15; // there: a moment's push, then back
                        if (nudging > 0) {
                            if (nudging > 15) {
                                if (nudging % 10 == 0 || !nav.isNavigating()) nav.navigateNear(door.getX(), door.getY(), door.getZ(), 0);
                                nav.tick();
                            } else {
                                // In the way in, still: two bodies in one another are pushed apart, it into the cell.
                                nav.cancel();
                                TaskContext.inputDriver(player).clear();
                            }
                            return TaskState.RUNNING;
                        }
                        nav.cancel();
                        TaskContext.inputDriver(player).clear();
                    }
                    // In: in the cell or the way in, both walled; the door two out from its job site.
                    boolean inCell = v.blockPosition().equals(cell) || v.blockPosition().equals(way);
                    // In, all of it: the game puts no block where a body reaches, and one coming in or turning round
                    // reaches over into the way in.
                    boolean clear = !v.getBoundingBox().intersects(new net.minecraft.world.phys.AABB(door))
                            && !sp.getBoundingBox().intersects(new net.minecraft.world.phys.AABB(door));
                    if (inCell && !clear && !sp.getBoundingBox().intersects(new net.minecraft.world.phys.AABB(door))) {
                        // Two seconds at the edge: a shove in, as a player would give it (it can go no further than
                        // the job site), and the door put in behind it.
                        if (++edgeTicks > 40 && nudging == 0) {
                            nudging = 215;
                            edgeTicks = 0;
                        }
                    } else if (!inCell) {
                        edgeTicks = 0;
                    }
                    if (inCell && clear) {
                        // In: the door, from where it can reach it (it waits close by for this).
                        boolean put = door.distToCenterSqr(sp.getEyePosition()) <= 4.5 * 4.5 && Placement.placeAnySupportBlock(sp, door);
                        // On its spot and the door won't go in from it (no face in sight): the next spot by the door.
                        if (!put && sp.blockPosition().equals(waitAt) && doorSpots != null && !doorSpots.isEmpty() && ++refusedAt > 10) {
                            waitAt = doorSpots.get(++waitIndex % doorSpots.size());
                            refusedAt = 0;
                        }
                        if (!put && Boolean.getBoolean("mineagent.traceWork") && ticks % 100 == 0) {
                            System.out.println("[MineAgent] cell: the villager is in, no door yet: "
                                    + Placement.whyNot(sp, door, net.minecraft.world.level.block.Blocks.COBBLESTONE.defaultBlockState())
                                    + ", " + String.format("%.1f", Math.sqrt(door.distToCenterSqr(sp.getEyePosition()))) + " off");
                        }
                        if (put) {
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
                    if (Boolean.getBoolean("mineagent.traceWork") && ticks >= traceAt) {
                        traceAt = ticks + 400;
                        var site = v.getBrain().getMemory(MemoryModuleType.JOB_SITE);
                        System.out.println("[MineAgent] cell: waiting, it at " + sp.blockPosition().toShortString() + " (for "
                                + (waitAt == null ? "none" : waitAt.toShortString()) + "), the villager at " + v.blockPosition().toShortString()
                                + ", " + v.getVillagerData().getProfession() + ", job site " + site.map(g -> g.pos().toShortString()).orElse("none")
                                + "; the cell " + cell.toShortString() + ", the way in " + door.toShortString());
                    }
                    // Wait out of its way, by the door, where the door is in reach: outside it, a step off the line in.
                    // Never in the way in or the cell, the only way to its job site: if it can't get to its spot from
                    // there, another one.
                    if (doorSpots == null) doorSpots = doorSpots(level);
                    if (waitAt == null) waitAt = doorSpots.isEmpty() ? waitSpot(level, layout) : doorSpots.get(0);
                    // Not got to in ten seconds: the next spot by the door.
                    boolean away = !sp.blockPosition().equals(waitAt);
                    if (away && ++blockingTicks > 200 && !doorSpots.isEmpty()) {
                        waitAt = doorSpots.get(++waitIndex % doorSpots.size());
                        blockingTicks = 0;
                    } else if (!away) {
                        blockingTicks = 0;
                    }
                    BlockPos waitAt = this.waitAt;
                    // On its spot, or by it; never in the way in or the cell, whatever is near.
                    // On it, exactly: a step off it may be behind a wall from the door.
                    boolean onSpot = sp.blockPosition().equals(waitAt);
                    if (!onSpot) {
                        if (ticks % 40 == 1 || !nav.isNavigating()) nav.navigateNear(waitAt.getX(), waitAt.getY(), waitAt.getZ(), 0);
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
    private static VillagerCell.Layout choose(ServerLevel level, BlockPos job, BlockPos villager) {
        VillagerCell.Layout best = null;
        int bestCost = Integer.MAX_VALUE;
        for (int[] d : VillagerCell.DIRECTIONS) {
            var l = VillagerCell.plan(job.getX(), job.getY(), job.getZ(), d[0], d[1]);
            if (at(l.cell()).equals(villager) || at(l.way()).equals(villager)) continue; // not where it stands
            if (!open(level, at(l.cell())) || !open(level, at(l.way())) || !open(level, at(l.door())) || !open(level, at(l.tradeFrom()))) continue;
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
    private int offCell, traceAt, jumpTicks, blockingTicks, edgeTicks, nudging, waitIndex, refusedAt, groundY = Integer.MIN_VALUE;
    /** Where it may wait for the villager: by the door and in reach of it, never the way in or the cell. */
    private java.util.List<BlockPos> doorSpots;

    /** Open spots on firm ground with the door in reach (3.8 from the middle), none a wall, the way in, the cell or the job site; nearest the door first. */
    private java.util.List<BlockPos> doorSpots(ServerLevel level) {
        BlockPos door = at(layout.door()), cell = at(layout.cell()), way = at(layout.way());
        java.util.List<BlockPos> out = new java.util.ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(door.offset(-3, -1, -3), door.offset(3, 1, 3))) {
            var pos = new VillagerCell.Pos(p.getX(), p.getY(), p.getZ());
            if (p.equals(door) || p.equals(cell) || p.equals(way) || layout.walls().contains(pos) || pos.equals(layout.jobSite())
                    || !open(level, p)) continue;
            double eye = net.minecraft.world.phys.Vec3.atBottomCenterOf(p).add(0, 1.62, 0).distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(door));
            if (eye <= 3.8 * 3.8) out.add(p.immutable());
        }
        out.sort(java.util.Comparator.comparingDouble(p -> p.distSqr(door)));
        return out;
    }
    /** The block over its eyes it is jumping to put in. */
    private BlockPos jumpFor;
    private boolean onlyItsSpotLeft;
    /** The spot it is going to build from, how long it has been on its way, and the spots it builds from in turn. */
    private BlockPos goingTo;
    private int goTicks, standIndex;
    private java.util.List<BlockPos> stands;

    /**
     * The next spot to build from, in turn: the way in, the place to trade from, either side of the way in, then the
     * others two or three out round the job site; each open two high on firm ground and none a wall, the cell or the
     * job site. The one it stands on is passed over.
     */
    private BlockPos nextStand(ServerLevel level, ServerPlayer sp) {
        if (stands == null) {
            stands = new java.util.ArrayList<>();
            var l = layout;
            int dx = l.cell().x() - l.jobSite().x(), dz = l.cell().z() - l.jobSite().z(), sx = dz, sz = dx;
            BlockPos e = at(l.way()), j = at(l.jobSite());
            java.util.List<BlockPos> ordered = new java.util.ArrayList<>(java.util.List.of(e, at(l.tradeFrom()), e.offset(sx, 0, sz), e.offset(-sx, 0, -sz)));
            for (int r = 2; r <= 3; r++) {
                for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) {
                    if (Math.max(Math.abs(x), Math.abs(z)) == r) ordered.add(j.offset(x, 0, z));
                }
            }
            for (BlockPos p : ordered) {
                var pos = new VillagerCell.Pos(p.getX(), p.getY(), p.getZ());
                if (l.walls().contains(pos) || pos.equals(l.cell()) || pos.equals(l.jobSite()) || stands.contains(p)) continue;
                if (open(level, p)) stands.add(p);
            }
        }
        if (stands.isEmpty()) return null;
        for (int i = 0; i < stands.size(); i++) {
            BlockPos p = stands.get(standIndex++ % stands.size());
            if (!p.equals(sp.blockPosition())) return p;
        }
        return stands.get(0);
    }

    /**
     * The cell where the villager stands, when that is beside its job site at its height (the way in and the place to
     * trade from clear); null otherwise.
     */
    private static VillagerCell.Layout toward(ServerLevel level, BlockPos job, BlockPos villager) {
        int dx = villager.getX() - job.getX(), dz = villager.getZ() - job.getZ();
        if (villager.getY() != job.getY() || Math.abs(dx) + Math.abs(dz) != 1) return null;
        var l = VillagerCell.plan(job.getX(), job.getY(), job.getZ(), dx, dz);
        if (!open(level, at(l.way())) || !open(level, at(l.door())) || !open(level, at(l.tradeFrom()))) return null;
        return l;
    }

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
