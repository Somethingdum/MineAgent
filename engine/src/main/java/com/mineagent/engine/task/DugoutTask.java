package com.mineagent.engine.task;

import com.mineagent.api.entity.AgentPlayer;
import com.mineagent.api.task.CompanionTask;
import com.mineagent.api.task.TaskRecord;
import com.mineagent.api.task.TaskState;
import com.mineagent.engine.act.Placement;
import com.mineagent.engine.pathing.execute.PlayerNav;
import com.mineagent.engine.pathing.util.BlockHelper;
import com.mineagent.engine.pathing.util.BreakCost;
import com.mineagent.engine.pathing.util.BreakSafety;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

/**
 * A home dug into the ground (SMH plan 70, its owner: "For huts. It can dig into a mountain or hill. Make a stairway
 * underground and dig out a room. It has many options. It doesn't need to make an elaborate hut. Even a dirt hut
 * works."): a room three wide, three deep and two high, dug into a hillside from a doorway in its face; or, with no
 * hillside near, down a stairway three steps under the ground it stands on. No materials: the ground is its walls and
 * its roof. Nothing someone built is broken, no liquid let in, nothing that falls brought down; its crafting table and
 * furnace put in if it carries them, and a torch. Kept as its hut.
 */
public class DugoutTask extends CompanionTask<DugoutTask.DugoutRecord> {
    public static final class DugoutRecord extends TaskRecord {
        public DugoutRecord(String toolCallId) {
            super(toolCallId);
        }
    }

    enum Phase { PLAN, DIG, FIT, DONE, FAILED }

    /** How far round it looks for a hillside. */
    static final int REACH = 16;
    /** The room: three wide (one each side of the middle), three deep, two high. */
    static final int ROOM_DEEP = 3;
    /** Steps down to an underground room. */
    static final int STEPS = 3;

    private Phase phase = Phase.PLAN;
    private final List<BlockPos> cells = new ArrayList<>();
    /** Where it stands to start digging; the room's floor cells (for the fittings); which kind of home. */
    private BlockPos entrance;
    private final List<BlockPos> floor = new ArrayList<>();
    private String kind = "";
    private int index, breakTicks, walkTicks, ticks, fitIndex, tries;
    private BlockPos breaking, walkingTo;
    private PlayerNav nav;
    private volatile boolean navFailed;
    private String outcome = "";

    public DugoutTask(AgentPlayer player, DugoutRecord record) {
        super(player, record);
    }

    private ServerPlayer sp() {
        return TaskContext.serverPlayer(player);
    }

    @Override
    protected void onStart() {
        if (nav == null) {
            nav = new PlayerNav(player, TaskContext.navCaches(player));
            nav.setListener(new PlayerNav.NavListener() {
                @Override public void onGoalReached() { }
                @Override public void onNavigationFailed(String reason) { navFailed = true; }
            });
        }
        record.extendDeadlineTo(Math.max(record.deadline(), sp().level().getGameTime() + 12_000L));
        walkingTo = null;
    }

    @Override
    protected TaskState onTick() {
        ServerPlayer sp = sp();
        ServerLevel level = sp.serverLevel();
        ticks++;
        return switch (phase) {
            case PLAN -> plan(sp, level);
            case DIG -> dig(sp, level);
            case FIT -> fit(sp, level);
            case DONE -> TaskState.SUCCESS;
            case FAILED -> TaskState.FAILED;
        };
    }

    // ── Planning ────────────────────────────────────────────────────

    private TaskState plan(ServerPlayer sp, ServerLevel level) {
        if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD) return end(false, "I dig a home only in the Overworld");
        BlockPos here = ProductionTask.standing(sp);
        // A hillside first (a doorway in its face, the hill over the room), the nearest; then a stairway down here.
        BlockPos bestStand = null;
        Direction bestDir = null;
        double bestD = Double.MAX_VALUE;
        for (int dx = -REACH; dx <= REACH; dx++) {
            for (int dz = -REACH; dz <= REACH; dz++) {
                for (int dy = -3; dy <= 3; dy++) {
                    BlockPos s = here.offset(dx, dy, dz);
                    double d = s.distSqr(here);
                    if (d >= bestD || !standable(level, s)) continue;
                    for (Direction dir : Direction.Plane.HORIZONTAL) {
                        if (hillside(level, s, dir)) {
                            bestD = d;
                            bestStand = s.immutable();
                            bestDir = dir;
                            break;
                        }
                    }
                }
            }
        }
        if (bestStand != null) {
            kind = "hillside";
            entrance = bestStand;
            layHillside(bestStand, bestDir);
        } else {
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                if (standable(level, here) && underground(level, here, dir)) {
                    kind = "underground";
                    entrance = here.immutable();
                    layUnderground(here, dir);
                    break;
                }
            }
        }
        if (entrance == null) return end(false, "there's no hillside near to dig a home into, and the ground here won't take a stairway down");
        say(("hillside".equals(kind) ? "Digging a home into the hillside at " : "Digging a home down a stairway at ")
                + entrance.toShortString() + ": a room three by three, two high.");
        phase = Phase.DIG;
        index = 0;
        return TaskState.RUNNING;
    }

    /** Feet and head clear, firm ground under. */
    private static boolean standable(ServerLevel level, BlockPos s) {
        return BlockHelper.isPassable(level.getBlockState(s)) && BlockHelper.isPassable(level.getBlockState(s.above()))
                && BlockHelper.canStandOn(level.getBlockState(s.below()));
    }

    /** Natural ground it may dig: not someone's, breakable, no liquid let in, nothing to fall on it. */
    private static boolean diggable(ServerLevel level, BlockPos p) {
        var st = level.getBlockState(p);
        if (BlockHelper.isPassable(st) || !BlockHelper.isBreakable(st) || BlockHelper.kindOf(st) != BreakCost.Kind.NATURAL) return false;
        if (com.mineagent.engine.world.PlacedBlocks.of(level).isPlaced(p.getX(), p.getY(), p.getZ())) return false;
        float hard = st.getDestroySpeed(level, p);
        if (hard < 0 || hard > 3.0f) return false; // no obsidian, no ancient rock
        return BreakSafety.safeToBreak(at(level), p.getX(), p.getY(), p.getZ());
    }

    /** Solid, and left standing (a wall, a roof, a floor of the room). */
    private static boolean solid(ServerLevel level, BlockPos p) {
        var st = level.getBlockState(p);
        return !BlockHelper.isPassable(st) && st.getFluidState().isEmpty();
    }

    private static BreakSafety.BlockAt at(ServerLevel level) {
        return (x, y, z) -> level.isLoaded(new BlockPos(x, y, z)) ? level.getBlockState(new BlockPos(x, y, z)) : null;
    }

    /** The room's cells from the cell it opens from, {@code d} ahead: three deep, three wide, two high. */
    private static List<BlockPos> room(BlockPos front, Direction d) {
        Direction side = d.getClockWise();
        List<BlockPos> out = new ArrayList<>();
        for (int k = 0; k < ROOM_DEEP; k++) {
            for (int w : new int[] {0, -1, 1}) {
                BlockPos c = front.relative(d, k).relative(side, w);
                out.add(c);
                out.add(c.above());
            }
        }
        return out;
    }

    /** The room's sides (one out from it each side) and its back, at feet and head, all standing: shut in but for the way in. */
    private static boolean enclosed(ServerLevel level, BlockPos front, Direction d) {
        Direction side = d.getClockWise();
        for (int h = 0; h <= 1; h++) {
            for (int k = 0; k < ROOM_DEEP; k++) {
                if (!solid(level, front.relative(d, k).relative(side, 2).above(h))) return false;
                if (!solid(level, front.relative(d, k).relative(side, -2).above(h))) return false;
            }
            for (int w = -1; w <= 1; w++) if (!solid(level, front.relative(d, ROOM_DEEP).relative(side, w).above(h))) return false;
        }
        return true;
    }

    /** A hillside: the doorway and the room all in the ground, its walls and roof standing round them. */
    private static boolean hillside(ServerLevel level, BlockPos s, Direction d) {
        BlockPos door = s.relative(d);
        if (!diggable(level, door) || !diggable(level, door.above()) || !solid(level, door.above(2))) return false;
        BlockPos front = door.relative(d);
        for (BlockPos c : room(front, d)) {
            if (!diggable(level, c)) return false;
            if (!solid(level, c.above()) && !room(front, d).contains(c.above())) return false; // the roof
        }
        for (BlockPos c : room(front, d)) {
            if (c.getY() == front.getY() && !BlockHelper.canStandOn(level.getBlockState(c.below()))) return false; // the floor
        }
        return enclosed(level, front, d);
    }

    private void layHillside(BlockPos s, Direction d) {
        BlockPos door = s.relative(d);
        cells.add(door);
        cells.add(door.above());
        BlockPos front = door.relative(d);
        cells.addAll(room(front, d));
        for (BlockPos c : room(front, d)) if (c.getY() == front.getY()) floor.add(c);
    }

    /** Steps down {@code d} from where it stands, then the room: all in the ground, the roof over it standing. */
    private static boolean underground(ServerLevel level, BlockPos s, Direction d) {
        for (int i = 1; i <= STEPS; i++) {
            BlockPos f = s.relative(d, i).below(i);
            for (int h = 0; h <= 2; h++) if (!diggable(level, f.above(h)) && !BlockHelper.isPassable(level.getBlockState(f.above(h)))) return false;
            if (!BlockHelper.canStandOn(level.getBlockState(f.below()))) return false;
        }
        BlockPos front = s.relative(d, STEPS + 1).below(STEPS);
        for (BlockPos c : room(front, d)) {
            if (!diggable(level, c)) return false;
            if (!solid(level, c.above()) && !room(front, d).contains(c.above())) return false;
        }
        for (BlockPos c : room(front, d)) {
            if (c.getY() == front.getY() && !BlockHelper.canStandOn(level.getBlockState(c.below()))) return false;
        }
        return enclosed(level, front, d);
    }

    private void layUnderground(BlockPos s, Direction d) {
        for (int i = 1; i <= STEPS; i++) {
            BlockPos f = s.relative(d, i).below(i);
            cells.add(f.above(2));
            cells.add(f.above());
            cells.add(f);
        }
        BlockPos front = s.relative(d, STEPS + 1).below(STEPS);
        cells.addAll(room(front, d));
        for (BlockPos c : room(front, d)) if (c.getY() == front.getY()) floor.add(c);
    }

    // ── Digging ─────────────────────────────────────────────────────

    private TaskState dig(ServerPlayer sp, ServerLevel level) {
        if (ticks > 10_000) return end(false, "the digging took too long");
        while (index < cells.size() && BlockHelper.isPassable(level.getBlockState(cells.get(index)))) {
            index++;
            breaking = null;
            tries = 0;
        }
        if (index >= cells.size()) {
            nav.cancel();
            TaskContext.inputDriver(player).clear();
            phase = Phase.FIT;
            return TaskState.RUNNING;
        }
        BlockPos c = cells.get(index);
        if (breaking != null) {
            if (++breakTicks > 600) {
                BlockDigger.abortBreaking(sp, c);
                breaking = null;
                index++; // left: the room a block smaller
            }
            return TaskState.RUNNING;
        }
        // Liquid come near since it planned: that block is left.
        if (!BreakSafety.safeToBreak(at(level), c.getX(), c.getY(), c.getZ())) {
            index++;
            return TaskState.RUNNING;
        }
        if (c.distToCenterSqr(sp.getEyePosition()) <= 4.2 * 4.2 && BlockDigger.startBreaking(sp, c)) {
            nav.cancel();
            walkingTo = null;
            breaking = c;
            breakTicks = 0;
            return TaskState.RUNNING;
        }
        // Nearer: to the dug cell (or where it began) nearest the block, on its floor.
        BlockPos to = entrance;
        double best = entrance.distSqr(c);
        for (int i = 0; i < index; i++) {
            BlockPos s = cells.get(i);
            if (!standable(level, s)) continue;
            double d = s.distSqr(c);
            if (d < best) {
                best = d;
                to = s;
            }
        }
        if (walkingTo == null || !walkingTo.equals(to)) {
            walkingTo = to.immutable();
            walkTicks = 0;
            navFailed = false;
            nav.navigateTo(to.getX(), to.getY(), to.getZ());
        }
        nav.tick();
        if (sp.blockPosition().equals(to) || navFailed || ++walkTicks > 300) {
            // There, or as near as it gets: this block is left if it still won't go (three goes from there).
            if (!sp.blockPosition().equals(to) || c.distToCenterSqr(sp.getEyePosition()) > 4.2 * 4.2 || ++tries > 3) {
                index++;
                tries = 0;
            }
            walkingTo = null;
        }
        return TaskState.RUNNING;
    }

    // ── Fittings ────────────────────────────────────────────────────

    /** A spare crafting table and its furnace in the back corners if it carries them; a torch on the floor by the door. */
    private TaskState fit(ServerPlayer sp, ServerLevel level) {
        var inv = sp.getInventory();
        if (floor.size() >= 9 && fitIndex < 3) {
            BlockPos back1 = floor.get(floor.size() - 2), back2 = floor.get(floor.size() - 1), front = floor.get(0);
            BlockPos at = switch (fitIndex) {
                case 0 -> back1;
                case 1 -> back2;
                default -> front;
            };
            var what = switch (fitIndex) {
                // One table kept on it (its kit carries one: crafting wherever it is), a spare one put down here.
                case 0 -> inv.countItem(Items.CRAFTING_TABLE) > 1 ? Blocks.CRAFTING_TABLE.defaultBlockState() : null;
                case 1 -> inv.countItem(Items.FURNACE) > 0 ? Blocks.FURNACE.defaultBlockState() : null;
                default -> inv.countItem(Items.TORCH) > 0 ? Blocks.TORCH.defaultBlockState() : null;
            };
            fitIndex++;
            // From where it stands at the end of the digging (inside, by the back): out of reach, that one is left.
            if (what != null && BlockHelper.isPassable(level.getBlockState(at)) && !sp.getBoundingBox().intersects(new net.minecraft.world.phys.AABB(at))
                    && at.distToCenterSqr(sp.getEyePosition()) <= 4.2 * 4.2) {
                Placement.placeBlock(sp, at, what);
            }
            return TaskState.RUNNING;
        }
        return end(true, null);
    }

    private TaskState end(boolean ok, String why) {
        if (nav != null) nav.cancel();
        TaskContext.inputDriver(player).clear();
        ServerPlayer sp = sp();
        var loop = TaskContext.agentLoop(player);
        if (ok) {
            BlockPos corner = floor.isEmpty() ? entrance : floor.get(0);
            String line = "Built the hut at " + corner.toShortString() + ": dug " + ("hillside".equals(kind) ? "into the hillside" : "down a stairway")
                    + ", a room three by three and two high; the ground is its walls and roof.";
            outcome = line;
            if (loop != null) {
                loop.say(line);
                loop.journal().record("built", line, sp.level().getGameTime());
                loop.placeKeeper().memory().name("build", "hut_dugout|" + kind + "|none",
                        new com.mineagent.engine.world.RouteMemory.Point(sp.serverLevel().dimension().location().toString(),
                                corner.getX(), corner.getY(), corner.getZ()), sp.level().getGameTime());
            }
            phase = Phase.DONE;
            return TaskState.SUCCESS;
        }
        outcome = why;
        if (loop != null) {
            loop.say("I couldn't dig a home: " + why + ".");
            loop.dugoutFailed();
        }
        phase = Phase.FAILED;
        return TaskState.FAILED;
    }

    private void say(String line) {
        var loop = TaskContext.agentLoop(player);
        if (loop != null) loop.say(line);
    }

    @Override
    protected String successMessage() {
        return "{\"success\":true,\"note\":\"" + outcome.replace("\"", "'") + "\"}";
    }

    @Override
    protected String failureMessage() {
        return "{\"success\":false,\"note\":\"" + (outcome == null ? "" : outcome.replace("\"", "'")) + "\"}";
    }

    @Override
    protected void onInterrupt() {
        if (nav != null) nav.cancel();
        walkingTo = null;
        if (breaking != null) {
            BlockDigger.abortBreaking(sp(), breaking);
            breaking = null;
        }
        TaskContext.inputDriver(player).clear();
    }
}
