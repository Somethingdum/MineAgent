package com.mineagent.engine.task;

import com.mineagent.api.entity.AgentPlayer;
import com.mineagent.api.task.CompanionTask;
import com.mineagent.api.task.TaskRecord;
import com.mineagent.api.task.TaskState;
import com.mineagent.engine.act.Placement;
import com.mineagent.engine.pathing.util.BlockHelper;
import com.mineagent.engine.pathing.util.BreakSafety;
import com.mineagent.engine.world.AfkFarms;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Walled in while its owner is away (SMH plan 70, MA5.4; the owner, Nov 3: "Going AFK, block yourself in, don't die").
 * Where it stands it puts blocks it carries on every open side, at its feet and its head, a roof over it and a floor
 * if there is none, as a player would, one at a time from where it stands. Short of blocks, it digs two down first
 * where that is safe (nothing a person built, no liquid let in, solid ground under), so the ground is most of its
 * walls. Then it waits: nothing it does itself breaks out (its own pathing and its fights are kept off the walls).
 * When they're back, it takes down what it put up and carries on.
 */
public class ShelterTask extends CompanionTask<ShelterTask.ShelterRecord> {
    public static final class ShelterRecord extends TaskRecord {
        public ShelterRecord(String toolCallId) {
            super(toolCallId);
        }
    }

    enum Phase { DIG, BUILD, HOLD, OPEN }

    /** How deep it digs in when it is short of blocks. */
    static final int DIG_DEPTH = 2;
    /** Waiting is long: up to three hours of game time. */
    static final long LONGEST = 216_000L;

    private Phase phase;
    private BlockPos feet;
    private final List<BlockPos> placed = new ArrayList<>();
    private final Set<BlockPos> skipped = new HashSet<>();
    private int dug;
    private BlockPos breaking;
    private int breakingTicks;
    private int wait;
    private int failures;
    private boolean centred;
    private boolean begun;
    private String outcome = "";

    public ShelterTask(AgentPlayer player, ShelterRecord record) {
        super(player, record);
    }

    /** What it does now (the game tests). */
    public String phase() {
        return phase == null ? "" : phase.name();
    }

    @Override
    protected void onStart() {
        ServerPlayer sp = TaskContext.serverPlayer(player);
        if (!begun) {
            begun = true;
            long now = sp.level().getGameTime();
            record.extendDeadlineTo(Math.max(record.deadline(), now + LONGEST));
            feet = sp.blockPosition();
            int need = open(sp.serverLevel(), feet).size();
            phase = Placement.supportBlockCount(sp) >= need ? Phase.BUILD : canDig(sp) ? Phase.DIG : Phase.BUILD;
        }
        TaskContext.inputDriver(player).clear();
    }

    /** The open cells round {@code at} it fills: the floor if there is none, its sides at feet and head, the roof. */
    static List<BlockPos> open(ServerLevel level, BlockPos at) {
        List<BlockPos> cells = new ArrayList<>();
        if (!BlockHelper.canStandOn(level.getBlockState(at.below()))) cells.add(at.below());
        Direction[] sides = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        for (Direction d : sides) cells.add(at.relative(d));
        for (Direction d : sides) cells.add(at.above().relative(d));
        cells.add(at.above(2));
        cells.removeIf(p -> !BlockHelper.isPassable(level.getBlockState(p)));
        return cells;
    }

    private static BreakSafety.BlockAt at(ServerLevel level) {
        return (x, y, z) -> level.isLoaded(new BlockPos(x, y, z)) ? level.getBlockState(new BlockPos(x, y, z)) : null;
    }

    /** Two blocks straight down are its to dig: natural, breakable, no liquid let in, solid ground under them. */
    private static boolean canDig(ServerPlayer sp) {
        ServerLevel level = sp.serverLevel();
        BlockPos f = sp.blockPosition();
        var placed = com.mineagent.engine.world.PlacedBlocks.of(level);
        for (int i = 1; i <= DIG_DEPTH; i++) {
            BlockPos p = f.below(i);
            var state = level.getBlockState(p);
            if (state.isAir() || !BlockHelper.isBreakable(state) || placed.isPlaced(p.getX(), p.getY(), p.getZ())
                    || !BreakSafety.safeToBreak(at(level), p.getX(), p.getY(), p.getZ())) return false;
        }
        return BlockHelper.canStandOn(level.getBlockState(f.below(DIG_DEPTH + 1)));
    }

    @Override
    protected TaskState onTick() {
        ServerPlayer sp = TaskContext.serverPlayer(player);
        var loop = TaskContext.agentLoop(player);
        boolean stay = loop != null && loop.afkSheltering();
        if (!stay && phase != Phase.OPEN) {
            AfkFarms.clear(sp.getUUID());
            breaking = null;
            phase = Phase.OPEN;
        }
        if (wait > 0) {
            wait--;
            return TaskState.RUNNING;
        }
        return switch (phase) {
            case DIG -> dig(sp);
            case BUILD -> build(sp, loop);
            case HOLD -> {
                TaskContext.inputDriver(player).clear();
                yield TaskState.RUNNING;
            }
            case OPEN -> takeDown(sp);
        };
    }

    private TaskState dig(ServerPlayer sp) {
        ServerLevel level = sp.serverLevel();
        if (dug >= DIG_DEPTH) {
            feet = sp.blockPosition();
            phase = Phase.BUILD;
            return TaskState.RUNNING;
        }
        if (breaking != null) {
            breakingTicks++;
            if (level.getBlockState(breaking).isAir()) {
                breaking = null;
                dug++;
                wait = 10; // it drops into the hole
            } else if (breakingTicks > 400) {
                com.mineagent.engine.task.BlockDigger.abortBreaking(sp, breaking);
                breaking = null;
                feet = sp.blockPosition();
                phase = Phase.BUILD;
            }
            return TaskState.RUNNING;
        }
        if (!sp.onGround()) return TaskState.RUNNING;
        BlockPos below = sp.blockPosition().below();
        if (!com.mineagent.engine.task.BlockDigger.startBreaking(sp, below)) {
            feet = sp.blockPosition();
            phase = Phase.BUILD;
            return TaskState.RUNNING;
        }
        breaking = below;
        breakingTicks = 0;
        return TaskState.RUNNING;
    }

    private TaskState build(ServerPlayer sp, com.mineagent.engine.loop.AgentLoop loop) {
        ServerLevel level = sp.serverLevel();
        if (!centred) {
            centred = true;
            // A step to the middle of its block: a body off-centre stands in the way of the blocks beside it.
            double cx = feet.getX() + 0.5, cz = feet.getZ() + 0.5;
            double off = Math.hypot(sp.getX() - cx, sp.getZ() - cz);
            if (off > 0.05 && off < 0.8) sp.connection.teleport(cx, sp.getY(), cz, sp.getYRot(), sp.getXRot());
            wait = 2;
            return TaskState.RUNNING;
        }
        List<BlockPos> todo = open(level, feet);
        todo.removeIf(skipped::contains);
        if (todo.isEmpty() || failures > 30) {
            int gaps = open(level, feet).size();
            AfkFarms.set(sp.getUUID(), new AfkFarms.Area(level.dimension().location().toString(),
                    feet.getX() + 0.5, feet.getY() + 1.0, feet.getZ() + 0.5, 2.5));
            phase = Phase.HOLD;
            outcome = gaps == 0 ? "walled in" : "walled in but for " + gaps + (gaps == 1 ? " gap" : " gaps");
            if (loop != null) {
                loop.say(gaps == 0 ? "Walled in. I'll wait here until you're back."
                        : "Walled in as far as my blocks go (" + gaps + (gaps == 1 ? " gap" : " gaps")
                                + " left). I'll wait here until you're back.");
            }
            return TaskState.RUNNING;
        }
        BlockPos next = todo.get(0);
        if (Placement.placeAnySupportBlock(sp, next)) {
            placed.add(next.immutable());
            failures = 0;
            wait = 3;
        } else {
            failures++;
            // A cell it can't fill from here (nothing to set it against, nothing left to place): on to the next.
            if (failures % 5 == 0 || Placement.supportBlockCount(sp) == 0) skipped.add(next.immutable());
            wait = 4;
        }
        return TaskState.RUNNING;
    }

    private TaskState takeDown(ServerPlayer sp) {
        ServerLevel level = sp.serverLevel();
        while (!placed.isEmpty() && level.getBlockState(placed.get(placed.size() - 1)).isAir()) placed.remove(placed.size() - 1);
        if (placed.isEmpty()) {
            outcome = "out again";
            return TaskState.SUCCESS;
        }
        BlockPos b = placed.get(placed.size() - 1);
        if (breaking == null || !breaking.equals(b)) {
            if (com.mineagent.engine.task.BlockDigger.startBreaking(sp, b)) {
                breaking = b;
                breakingTicks = 0;
            } else {
                placed.remove(placed.size() - 1); // out of reach or not its to break: left
            }
            return TaskState.RUNNING;
        }
        if (++breakingTicks > 400) {
            com.mineagent.engine.task.BlockDigger.abortBreaking(sp, b);
            placed.remove(placed.size() - 1);
            breaking = null;
        }
        return TaskState.RUNNING;
    }

    @Override
    protected String successMessage() {
        return "{\"success\":true,\"note\":\"" + outcome + "\"}";
    }

    @Override
    protected String failureMessage() {
        return "{\"success\":false,\"note\":\"" + (outcome.isEmpty() ? "no shelter" : outcome) + "\"}";
    }

    @Override
    protected void onInterrupt() {
        AfkFarms.clear(TaskContext.serverPlayer(player).getUUID());
        TaskContext.inputDriver(player).clear();
    }
}
