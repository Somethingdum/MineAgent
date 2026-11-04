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
        /** Night, its owner away and no bed (MA8.3): walled in until morning, not until they're back. */
        public final boolean untilMorning;

        public ShelterRecord(String toolCallId) {
            this(toolCallId, false);
        }

        /**
         * Its home for the night (a hut it made, SMH plan 70): it walks into it before dusk and closes its ways in, rather
         * than walling in where it is. Null: where it stands.
         */
        public final BlockPos home;

        public ShelterRecord(String toolCallId, boolean untilMorning) {
            this(toolCallId, untilMorning, null);
        }

        /** Blocks of a shelter of its own left standing (its last one ended with them up): taken down, nothing else. */
        public final List<BlockPos> takeDown;

        public ShelterRecord(String toolCallId, boolean untilMorning, BlockPos home) {
            this(toolCallId, untilMorning, home, List.of());
        }

        public ShelterRecord(String toolCallId, boolean untilMorning, BlockPos home, List<BlockPos> takeDown) {
            super(toolCallId);
            this.untilMorning = untilMorning;
            this.home = home == null ? null : home.immutable();
            this.takeDown = takeDown == null ? List.of() : List.copyOf(takeDown);
        }
    }

    enum Phase { GO, DIG, BUILD, HOLD, OPEN }

    /** Closed in at home for the night: the sleep chain may lie it down in the bed there. */
    private static final Set<java.util.UUID> AT_HOME = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** On its way home for the night: the sleep chain waits (a bed put down out on the way is no night at home). */
    private static final Set<java.util.UUID> GOING_HOME = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static boolean sealedAtHome(java.util.UUID id) {
        return id != null && AT_HOME.contains(id);
    }

    public static boolean goingHome(java.util.UUID id) {
        return id != null && GOING_HOME.contains(id);
    }

    /** By here in the day it is home for the night: the walk home starts sooner by the length of the way. */
    public static final long DUSK = 11_000L;
    /** From when a shelter till morning holds. */
    public static final long EVENING = 9_500L;
    /** The earliest a walk home for the night starts (a long way home); a shelter begun that early holds from then. */
    public static final long EARLIEST_HOME = 8_000L;
    /** The sun well up: the undead out in it are burning. */
    static final long SUN_UP = 600L;
    /** And gone by now, or in the shade where waiting on them does nothing. */
    static final long LATEST = 2_400L;
    /** How far one leg of a long walk home goes. */
    static final int LEG = 48;
    private int legs;

    /** The next leg towards {@code to}: all of it when it is near, else a point on the ground {@link #LEG} blocks along. */
    private static BlockPos leg(ServerPlayer sp, BlockPos to) {
        BlockPos at = sp.blockPosition();
        double dx = to.getX() - at.getX(), dz = to.getZ() - at.getZ(), d = Math.sqrt(dx * dx + dz * dz);
        if (d <= LEG + 8) return to;
        int x = at.getX() + (int) Math.round(dx / d * LEG), z = at.getZ() + (int) Math.round(dz / d * LEG);
        var level = sp.serverLevel();
        if (!level.hasChunk(x >> 4, z >> 4)) return to;
        int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return new BlockPos(x, y, z);
    }

    /** The longest walk home it starts, in ticks; past that it shelters where it is. */
    static final int GO_LIMIT = 2_400;

    /**
     * Still night for one sheltering till morning (its owner: "waiting motionless until all of the mobs have burned"):
     * from dusk until the sun is well up, and after that while the undead near are still about in it, up to the middle
     * of the morning. A dark sky by day (a storm) is night too.
     */
    public static boolean stillNight(ServerLevel level, ServerPlayer sp) {
        long t = level.getDayTime() % 24_000L;
        if (t >= EVENING || t < SUN_UP || !level.isDay()) return true;
        if (t >= LATEST || sp == null) return false;
        return !level.getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class, sp.getBoundingBox().inflate(16.0),
                m -> m.isAlive() && (m.isInvertedHealAndHarm() || m instanceof net.minecraft.world.entity.monster.Creeper)
                        && m.distanceToSqr(sp) < 16.0 * 16.0).isEmpty();
    }

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
    /** Ticks left in a jump made to put a block down out of sight from its feet (a roof's). */
    private int jumpTicks;
    private boolean centred;
    private boolean begun;
    /** Where it stood before it dug in, and the block it jumped from to put one under itself on the way back up. */
    private BlockPos start;
    private BlockPos jumpFrom;
    private int climbTries;
    private String outcome = "";
    /** How deep it digs in here (three when the ground goes that far, so the roof sits between earth walls). */
    private int digTo = DIG_DEPTH;
    /** Closing a room round it (its hut, a cave pocket) rather than walling in its own two cells. */
    private boolean roomMode;
    private Enclosure.Room room;
    private BlockPos target;
    private com.mineagent.engine.pathing.execute.PlayerNav nav;
    private boolean navFailed;
    private int walkTicks;
    private int walks;
    private boolean triedDig;
    /** Gone to ground it could dig in, from where it couldn't (once). */
    private boolean relocated;
    private BlockPos relocateTo;
    private int holdTicks;
    private int backTicks;
    /** From when in the day this shelter holds: the evening, or earlier when it set out for home earlier. */
    private long holdsFrom = EVENING;
    /** The block it is breaking is what stood in its own cell (snow, grass), not the ground under it. */
    private boolean clearing;
    /** Its blocks it couldn't take down: handed on, to come down when its body is free. */
    private final List<BlockPos> leftUp = new ArrayList<>();

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
            long t = sp.serverLevel().getDayTime() % 24_000L;
            if (record.untilMorning && t >= EARLIEST_HOME && t < EVENING) holdsFrom = t;
            if (!record.takeDown.isEmpty()) {
                // Walls of its own left up: down they come, from here.
                AfkFarms.clear(sp.getUUID());
                placed.addAll(record.takeDown);
                feet = sp.blockPosition();
                start = feet.immutable();
                phase = Phase.OPEN;
            } else if (record.home != null && com.mineagent.engine.settings.Switches.homeAtNight()) {
                phase = Phase.GO;
                GOING_HOME.add(sp.getUUID());
            } else {
                decide(sp);
            }
        }
        if (phase == Phase.GO && nav == null) {
            newNav();
            target = null;
        }
        TaskContext.inputDriver(player).clear();
    }

    private void newNav() {
        nav = new com.mineagent.engine.pathing.execute.PlayerNav(player, TaskContext.navCaches(player));
        nav.setListener(new com.mineagent.engine.pathing.execute.PlayerNav.NavListener() {
            @Override public void onGoalReached() { }
            @Override public void onNavigationFailed(String reason) { navFailed = true; }
        });
    }

    private static boolean trace() {
        return Boolean.getBoolean("mineagent.traceWork");
    }

    /**
     * What it does where it stands now. In a room (its hut, a cave pocket: few ways in) it closes the ways in. Out in
     * the open, sheltering for the night, it digs in where the ground lets it (the earth its walls, the dug blocks its
     * roof, as a player's first night); short of ground to dig, it walls in with what it carries. Walled in for its
     * owner (AFK), walls first while it carries enough, as they asked.
     */
    private void decide(ServerPlayer sp) {
        ServerLevel level = sp.serverLevel();
        feet = sp.blockPosition();
        start = feet.immutable(); // where it stands before it digs in (after a walk home or to earth, there)
        int have = Placement.supportBlockCount(sp);
        Enclosure.Room r = Enclosure.around(level, feet);
        int need = open(level, feet).size();
        int depth = digDepth(sp);
        if (record.untilMorning && !relocated && (sp.isInWater() || !level.getFluidState(feet).isEmpty())
                && (relocateTo = groundToDig(sp)) != null) {
            // In the water at nightfall (the shore its walk reached): no wall goes up in water and nothing digs there,
            // so to dry ground first, and in there.
            relocated = true;
            if (nav == null) newNav();
            target = relocateTo;
            walkTicks = 0;
            navFailed = false;
            nav.navigateTo(target.getX(), target.getY(), target.getZ());
            phase = Phase.GO;
        } else if (r != null && !r.sealed() && (record.home != null || r.openings().size() <= Math.min(have, need - 1))) {
            room = r;
            roomMode = true;
            phase = Phase.BUILD;
        } else if (r != null && r.sealed()) {
            room = r;
            roomMode = true;
            phase = Phase.BUILD; // closed already: nothing to put up
        } else if (record.untilMorning && depth > 0) {
            digTo = depth;
            phase = Phase.DIG;
        } else if (record.untilMorning && have < need && !relocated && (relocateTo = groundToDig(sp)) != null) {
            // Nothing to dig here with what it has, too little to wall in with: to the earth near by, and in there.
            relocated = true;
            if (nav == null) newNav();
            target = relocateTo;
            walkTicks = 0;
            navFailed = false;
            nav.navigateTo(target.getX(), target.getY(), target.getZ());
            phase = Phase.GO;
        } else {
            digTo = Math.max(depth, DIG_DEPTH);
            phase = have >= need ? Phase.BUILD : depth > 0 ? Phase.DIG : Phase.BUILD;
        }
        if (trace()) {
            System.out.println("[MineAgent] shelter: at " + feet.toShortString() + ", " + need + " cells open, " + have + " blocks, "
                    + (r == null ? "no room" : "a room of " + r.air().size() + " with " + r.openings().size() + " ways in") + ": " + phase
                    + (phase == Phase.DIG ? " " + digTo + " down" : "")
                    + (phase == Phase.GO ? " to ground it can dig at " + relocateTo.toShortString() : "")
                    + (phase == Phase.BUILD && !roomMode && have < need ? " (no dig: under it " + level.getBlockState(feet.below()).getBlock()
                    + ", " + level.getBlockState(feet.below(2)).getBlock() + ", " + level.getBlockState(feet.below(3)).getBlock() + ")" : ""));
        }
    }

    /** Home: into its hut by the way it knows, to the cell of it with fewest ways in. */
    private TaskState go(ServerPlayer sp) {
        ServerLevel level = sp.serverLevel();
        if (target == null) {
            Enclosure.Room r = relocateTo == null ? Enclosure.bestNear(level, record.home) : null;
            target = relocateTo != null ? relocateTo : r != null ? r.stand() : record.home;
            walkTicks = 0;
            navFailed = false;
            BlockPos leg = leg(sp, target);
            nav.navigateTo(leg.getX(), leg.getY(), leg.getZ());
            if (trace()) System.out.println("[MineAgent] shelter: going home to " + target.toShortString() + " from "
                    + sp.blockPosition().toShortString() + (relocateTo != null ? " (ground to dig in)" : r == null ? " (no room there now)" : ", a room of " + r.air().size()
                    + " with " + r.openings().size() + " ways in"));
        }
        nav.tick();
        walkTicks++;
        // A long way home goes in legs: at the end of one (or its search for it failing), the next from there.
        if (!sp.blockPosition().equals(target) && sp.blockPosition().distSqr(target) > LEG * LEG && (!nav.isNavigating() || navFailed)
                && walkTicks > 20 && legs < 12) {
            legs++;
            navFailed = false;
            BlockPos leg = leg(sp, target);
            nav.navigateTo(leg.getX(), leg.getY(), leg.getZ());
            return TaskState.RUNNING;
        }
        boolean there = sp.blockPosition().equals(target);
        if (!there && sp.onGround() && !nav.isNavigating() && sp.blockPosition().distSqr(target) <= 2) {
            // Short of the cell it went for: in the room all the same, or another go at it (in its doorway it would
            // dig in on its own threshold).
            if (Enclosure.around(level, sp.blockPosition()) != null) there = true;
            else if (++walks < 4) {
                target = null;
                return TaskState.RUNNING;
            }
        }
        if (there || navFailed || walkTicks > GO_LIMIT || walks >= 4) {
            if (!there && navFailed && ++walks < 3 && walkTicks < GO_LIMIT) {
                target = null; // the way again, from where it got to
                return TaskState.RUNNING;
            }
            nav.cancel();
            GOING_HOME.remove(sp.getUUID());
            if (trace() && !there) System.out.println("[MineAgent] shelter: didn't get home (" + (navFailed ? "no way" : "too long")
                    + "), at " + sp.blockPosition().toShortString() + "; sheltering here");
            decide(sp);
        }
        return TaskState.RUNNING;
    }

    /**
     * The open cells round {@code at} it fills: the floor if there is none, its sides at feet and head, and the roof
     * (with one block on a wall first to set it against, when nothing else touches it).
     */
    static List<BlockPos> open(ServerLevel level, BlockPos at) {
        List<BlockPos> cells = new ArrayList<>();
        if (!BlockHelper.canStandOn(level.getBlockState(at.below()))) cells.add(at.below());
        Direction[] sides = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        for (Direction d : sides) cells.add(at.relative(d));
        for (Direction d : sides) cells.add(at.above().relative(d));
        // A block is set against a face of one beside it, and the walls are only corner to corner with the roof: as a
        // player does, one block on top of a wall first, then the roof against it.
        BlockPos roof = at.above(2);
        boolean roofHasFace = false;
        for (Direction d : sides) roofHasFace |= !BlockHelper.isPassable(level.getBlockState(roof.relative(d)));
        roofHasFace |= !BlockHelper.isPassable(level.getBlockState(roof.above()));
        if (!roofHasFace) cells.add(roof.relative(Direction.NORTH));
        cells.add(roof);
        cells.removeIf(p -> !BlockHelper.isPassable(level.getBlockState(p)));
        return cells;
    }

    private static BreakSafety.BlockAt at(ServerLevel level) {
        return (x, y, z) -> level.isLoaded(new BlockPos(x, y, z)) ? level.getBlockState(new BlockPos(x, y, z)) : null;
    }

    /** How deep it can dig in here: three when the ground goes that far, else two, else not at all. */
    static int digDepth(ServerPlayer sp) {
        for (int d = DIG_DEPTH + 1; d >= DIG_DEPTH; d--) if (canDig(sp, d)) return d;
        return 0;
    }

    /**
     * The blocks straight down are its to dig: natural, no liquid let in, solid ground under them, and each one quick to
     * dig with what it has (earth, sand or gravel by hand; stone only with a pickaxe that takes it).
     */
    private static boolean canDig(ServerPlayer sp, int depth) {
        return canDigAt(sp, sp.blockPosition(), depth);
    }

    private static boolean canDigAt(ServerPlayer sp, BlockPos f, int depth) {
        ServerLevel level = sp.serverLevel();
        var placed = com.mineagent.engine.world.PlacedBlocks.of(level);
        for (int i = 1; i <= depth; i++) {
            BlockPos p = f.below(i);
            var state = level.getBlockState(p);
            if (state.isAir() || !BlockHelper.isBreakable(state) || placed.isPlaced(p.getX(), p.getY(), p.getZ())
                    || !BreakSafety.safeToBreak(at(level), p.getX(), p.getY(), p.getZ())) return false;
            float hardness = state.getDestroySpeed(level, p);
            if (hardness > 0.8f && !(hardness <= 3.0f && canTake(sp, state))) return false;
        }
        return BlockHelper.canStandOn(level.getBlockState(f.below(depth + 1)));
    }

    /**
     * The nearest cell within eight blocks it could dig in at, when it can't where it stands and has too little to wall
     * in with (bare stone and no pickaxe): earth, sand or gravel near by, as a player runs to the dirt at dusk.
     */
    private static BlockPos groundToDig(ServerPlayer sp) {
        ServerLevel level = sp.serverLevel();
        BlockPos at = sp.blockPosition(), best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-8, -3, -8), at.offset(8, 3, 8))) {
            if (!level.isLoaded(p) || !Enclosure.standable(level, p) || !canDigAt(sp, p, DIG_DEPTH)
                    || !level.getFluidState(p).isEmpty() || !level.getFluidState(p.above()).isEmpty()) continue;
            double d = p.distSqr(at);
            if (d < bestDistance) {
                bestDistance = d;
                best = p.immutable();
            }
        }
        return best;
    }

    /** It carries a tool that takes this block (a pickaxe for stone). */
    private static boolean canTake(ServerPlayer sp, net.minecraft.world.level.block.state.BlockState state) {
        var inv = sp.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var st = inv.getItem(i);
            if (!st.isEmpty() && st.isCorrectToolForDrops(state) && st.getDestroySpeed(state) > 1.0f) return true;
        }
        return false;
    }

    @Override
    protected TaskState onTick() {
        ServerPlayer sp = TaskContext.serverPlayer(player);
        var loop = TaskContext.agentLoop(player);
        // On its way home it is not yet sheltering: nothing to open. Set out early (a long way home), it holds from then.
        long t = sp.serverLevel().getDayTime() % 24_000L;
        boolean stay = !record.takeDown.isEmpty() ? false
                : record.untilMorning ? phase == Phase.GO || t >= holdsFrom || stillNight(sp.serverLevel(), sp)
                : loop != null && loop.afkSheltering();
        if (!stay && phase != Phase.OPEN) {
            AfkFarms.clear(sp.getUUID());
            AT_HOME.remove(sp.getUUID());
            GOING_HOME.remove(sp.getUUID());
            if (nav != null) nav.cancel();
            if (breaking != null) com.mineagent.engine.task.BlockDigger.abortBreaking(sp, breaking);
            breaking = null;
            phase = Phase.OPEN;
        }
        if (wait > 0) {
            wait--;
            return TaskState.RUNNING;
        }
        return switch (phase) {
            case GO -> go(sp);
            case DIG -> dig(sp);
            case BUILD -> build(sp, loop);
            case HOLD -> hold(sp);
            case OPEN -> takeDown(sp);
        };
    }

    private TaskState dig(ServerPlayer sp) {
        ServerLevel level = sp.serverLevel();
        triedDig = true;
        if (dug >= digTo) {
            feet = sp.blockPosition();
            centred = false;
            phase = Phase.BUILD;
            return TaskState.RUNNING;
        }
        if (breaking != null) {
            breakingTicks++;
            if (level.getBlockState(breaking).isAir()) {
                if (!clearing) dug++;
                clearing = false;
                breaking = null;
                wait = 10; // it drops into the hole
            } else if (breakingTicks > 400) {
                com.mineagent.engine.task.BlockDigger.abortBreaking(sp, breaking);
                if (trace()) System.out.println("[MineAgent] shelter: couldn't dig " + level.getBlockState(breaking).getBlock() + " in 400 ticks");
                breaking = null;
                feet = sp.blockPosition();
                phase = Phase.BUILD;
            }
            return TaskState.RUNNING;
        }
        if (!sp.onGround()) return TaskState.RUNNING;
        // In the middle of its block: on the edge of two it would stand on the next one with the hole dug under it.
        BlockPos here = sp.blockPosition();
        double cx = here.getX() + 0.5, cz = here.getZ() + 0.5;
        double off = Math.hypot(sp.getX() - cx, sp.getZ() - cz);
        if (off > 0.05 && off < 0.8) {
            sp.connection.teleport(cx, sp.getY(), cz, sp.getYRot(), sp.getXRot());
            wait = 2;
            return TaskState.RUNNING;
        }
        // Snow, grass or a flower where it stands is in the way of its look at the ground under it: that first.
        BlockPos target = !level.getBlockState(here).isAir() && BlockHelper.isPassable(level.getBlockState(here)) ? here : here.below();
        var tried = com.mineagent.engine.task.BlockDigger.startBreakingDetailed(sp, target);
        if (!tried.allowed()) {
            if (trace()) System.out.println("[MineAgent] shelter: couldn't dig " + level.getBlockState(target).getBlock() + " at "
                    + target.toShortString() + " (" + tried.status() + ": " + tried.detail() + "), " + dug + " of " + digTo + " down");
            feet = sp.blockPosition();
            centred = false;
            phase = Phase.BUILD;
            return TaskState.RUNNING;
        }
        breaking = target;
        clearing = target.equals(here);
        breakingTicks = 0;
        return TaskState.RUNNING;
    }

    /** The ways in still open: its room's, or its own two cells' and its roof. */
    private List<BlockPos> stillOpen(ServerLevel level) {
        if (!roomMode) return open(level, feet);
        Enclosure.Room r = Enclosure.around(level, feet);
        if (r == null) {
            // No room round it after all (its walls broken in): its own two cells, as out in the open.
            roomMode = false;
            skipped.clear();
            failures = 0;
            return open(level, feet);
        }
        room = r;
        List<BlockPos> todo = new ArrayList<>(r.openings());
        // A way in it can't close from here: the open cell of the room beside it, nearer (the doorway itself).
        for (BlockPos w : r.openings()) {
            if (!skipped.contains(w)) continue;
            for (BlockPos in : Enclosure.insideOf(r, w)) if (!todo.contains(in)) todo.add(in);
        }
        return todo;
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
        List<BlockPos> todo = stillOpen(level);
        todo.removeIf(skipped::contains);
        if (todo.isEmpty() || failures > 30) {
            int gaps = roomMode ? (room == null ? 0 : room.openings().size()) : open(level, feet).size();
            if (gaps > 0 && roomMode && Placement.supportBlockCount(sp) > 0) {
                // The room's ways in won't all close from here: its own two cells inside it, then.
                if (trace()) System.out.println("[MineAgent] shelter: " + gaps + " of the room's ways in left open; walling in my own cells");
                roomMode = false;
                skipped.clear();
                failures = 0;
                return TaskState.RUNNING;
            }
            if (gaps > 0 && !roomMode && record.untilMorning && !relocated && digDepth(sp) == 0 && (relocateTo = groundToDig(sp)) != null) {
                // Its walls won't close here (a cliff's edge, a tree top: nothing to set them against) and the ground
                // here won't dig: to ground that will, and in there. Its blocks put up here come with it.
                if (trace()) System.out.println("[MineAgent] shelter: " + gaps + " gaps left in my walls; to ground I can dig at " + relocateTo.toShortString());
                relocated = true;
                skipped.clear();
                failures = 0;
                if (nav == null) newNav();
                target = relocateTo;
                walkTicks = 0;
                walks = 0;
                navFailed = false;
                nav.navigateTo(target.getX(), target.getY(), target.getZ());
                phase = Phase.GO;
                return TaskState.RUNNING;
            }
            if (gaps > 0 && !roomMode && !triedDig && record.untilMorning && digDepth(sp) > 0) {
                if (trace()) System.out.println("[MineAgent] shelter: " + gaps + " gaps left in my walls; digging in instead");
                digTo = digDepth(sp);
                skipped.clear();
                failures = 0;
                phase = Phase.DIG;
                return TaskState.RUNNING;
            }
            AfkFarms.set(sp.getUUID(), new AfkFarms.Area(level.dimension().location().toString(),
                    feet.getX() + 0.5, feet.getY() + 1.0, feet.getZ() + 0.5, roomMode ? 4.5 : 2.5));
            if (gaps == 0 && roomMode && record.home != null) {
                AT_HOME.add(sp.getUUID());
                putBedDown(sp, room, placed);
            }
            holdTicks = 0;
            phase = Phase.HOLD;
            outcome = gaps == 0 ? "walled in" : "walled in but for " + gaps + (gaps == 1 ? " gap" : " gaps");
            if (loop != null) {
                String until = record.untilMorning ? " I'll wait here till morning." : " I'll wait here until you're back.";
                loop.say(gaps == 0 ? "Walled in." + until
                        : "Walled in as far as my blocks go (" + gaps + (gaps == 1 ? " gap" : " gaps") + " left)." + until);
            }
            return TaskState.RUNNING;
        }
        BlockPos next = todo.get(0);
        var input = TaskContext.inputDriver(player);
        if (jumpTicks > 0) {
            jumpTicks--;
            input.setJumping(false);
            if (!sp.onGround() && Placement.placeAnySupportBlock(sp, next)) {
                placed.add(next.immutable());
                failures = 0;
                jumpTicks = 0;
                wait = 3;
            } else if (jumpTicks == 0) {
                failures++;
                if (failures % 5 == 0) skipped.add(next.immutable());
                wait = 4;
            }
            return TaskState.RUNNING;
        }
        if (Placement.placeAnySupportBlock(sp, next)) {
            placed.add(next.immutable());
            failures = 0;
            wait = 3;
        } else if (next.getY() >= feet.getY() + 2 && sp.onGround() && Placement.supportBlockCount(sp) > 0) {
            // Standing, its eyes are below the top of its walls: the face to set this against is out of sight. As a
            // player does in a hole one block wide, it jumps and puts the block down at the top of the jump.
            input.setJumping(true);
            jumpTicks = 12;
        } else {
            failures++;
            if (trace() && failures % 5 == 1) System.out.println("[MineAgent] shelter: can't put a block at " + next.toShortString() + " ("
                    + Placement.whyNot(sp, next, net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState()) + "), "
                    + Placement.supportBlockCount(sp) + " blocks, " + (roomMode ? "room" : "own cells"));
            // A cell it can't fill from here (nothing to set it against, nothing left to place): on to the next.
            if (failures % 5 == 0 || Placement.supportBlockCount(sp) == 0) skipped.add(next.immutable());
            wait = 4;
        }
        return TaskState.RUNNING;
    }

    /**
     * Waiting, still (its owner: "waiting motionless until all of the mobs have burned"). Now and then it looks at its
     * walls again: one broken in (a creeper, an enderman taking a block) is put back from where it stands.
     */
    private TaskState hold(ServerPlayer sp) {
        TaskContext.inputDriver(player).clear();
        if (++holdTicks % 100 == 0 && !sp.isSleeping() && Placement.supportBlockCount(sp) > 0) {
            ServerLevel level = sp.serverLevel();
            Enclosure.Room r = roomMode ? Enclosure.around(level, feet) : null;
            List<BlockPos> gaps = new ArrayList<>(r != null ? r.openings() : open(level, feet));
            gaps.removeIf(skipped::contains);
            if (!gaps.isEmpty()) {
                if (trace()) System.out.println("[MineAgent] shelter: a wall is open again at " + gaps.get(0).toShortString() + "; putting it back");
                failures = 0;
                phase = Phase.BUILD;
            }
        }
        return TaskState.RUNNING;
    }

    /** A bed it carries, put down in its hut for the night (the sleep chain lies it down in it); kept there after. */
    private static void putBedDown(ServerPlayer sp, Enclosure.Room room, List<BlockPos> sealed) {
        if (!com.mineagent.engine.settings.Switches.sleepWhenAway()) return;
        var inv = sp.getInventory();
        boolean carried = false;
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).getItem() instanceof net.minecraft.world.item.BedItem) carried = true;
        if (!carried) return;
        BlockPos at = sp.blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-3, -1, -3), at.offset(3, 2, 3))) {
            if (sp.serverLevel().getBlockState(p).getBlock() instanceof net.minecraft.world.level.block.BedBlock) return; // one here already
        }
        // On the floor of its room, off the cells it closed its ways in beside (its doorway).
        final Set<BlockPos> air = room == null ? Set.of() : room.air();
        BlockPos put = com.mineagent.engine.survival.SleepChain.putBedDown(sp, p -> {
            if (room != null && !air.contains(p)) return false;
            for (Direction d : Direction.values()) if (sealed.contains(p.relative(d))) return false;
            return true;
        });
        if (trace()) System.out.println("[MineAgent] shelter: " + (put == null ? "no room for my bed beside me" : "my bed put down at " + put.toShortString()));
    }

    private TaskState takeDown(ServerPlayer sp) {
        ServerLevel level = sp.serverLevel();
        while (!placed.isEmpty() && level.getBlockState(placed.get(placed.size() - 1)).isAir()) placed.remove(placed.size() - 1);
        if (placed.isEmpty()) {
            // Dug in: back up to where it stood, a block put under it at the top of each jump (the dug blocks), which
            // fills the hole as well. The third benchmark run sat in a hole from its first night on.
            // A jump under way is finished first: half way up it is level with where it stood, and is not out yet.
            if (jumpFrom != null) return climb(sp);
            if (!sp.onGround()) return TaskState.RUNNING;
            if (dug > 0 && start != null && sp.blockPosition().getY() < start.getY() && climbTries <= dug * 4) return climb(sp);
            outcome = sp.blockPosition().getY() < (start == null ? Integer.MIN_VALUE : start.getY()) ? "out again but still in the hole" : "out again";
            AT_HOME.remove(sp.getUUID());
            GOING_HOME.remove(sp.getUUID());
            return TaskState.SUCCESS; // what it couldn't take down is handed on as it ends (onInterrupt)
        }
        BlockPos b = placed.get(placed.size() - 1);
        if (breaking == null || !breaking.equals(b)) {
            if (com.mineagent.engine.task.BlockDigger.startBreaking(sp, b)) {
                breaking = b;
                breakingTicks = 0;
                if (nav != null) nav.cancel();
            } else if (feet != null && !sp.blockPosition().equals(feet) && backTicks++ < 300) {
                // Up from its bed, beside it: back where it stood to close them, and from there they come down.
                if (nav == null) newNav();
                if (backTicks == 1 || !nav.isNavigating() && backTicks % 40 == 0) nav.navigateTo(feet.getX(), feet.getY(), feet.getZ());
                nav.tick();
            } else {
                if (trace()) {
                    var why = com.mineagent.engine.task.BlockDigger.assessBreak(sp, b);
                    System.out.println("[MineAgent] shelter: can't take down " + sp.serverLevel().getBlockState(b).getBlock() + " at "
                            + b.toShortString() + " (" + why.status() + ": " + why.detail() + "), from " + sp.blockPosition().toShortString());
                }
                leftUp.add(placed.remove(placed.size() - 1)); // out of reach or not its to break: left, for now
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

    /** One step up out of its hole: a jump, and a block put where its feet were at the top of it. */
    private TaskState climb(ServerPlayer sp) {
        var input = TaskContext.inputDriver(player);
        if (Boolean.getBoolean("mineagent.traceWork") && (jumpFrom == null || jumpTicks == 12)) {
            System.out.println("[MineAgent] shelter: climbing, at " + sp.blockPosition().toShortString() + String.format(" (y %.2f)", sp.getY())
                    + " from " + start.toShortString() + ", try " + climbTries + " of " + (dug * 4) + ", on the ground " + sp.onGround()
                    + ", blocks " + Placement.supportBlockCount(sp) + ", jumped from " + (jumpFrom == null ? "none" : jumpFrom.toShortString())
                    + ", over its head " + sp.serverLevel().getBlockState(sp.blockPosition().above(2)).getBlock());
        }
        if (jumpFrom == null) {
            if (!sp.onGround()) return TaskState.RUNNING;
            if (Placement.supportBlockCount(sp) == 0) {
                climbTries = Integer.MAX_VALUE; // nothing to climb with: its paths take it from here
                return TaskState.RUNNING;
            }
            jumpFrom = sp.blockPosition().immutable();
            climbTries++;
            input.setJumping(true);
            jumpTicks = 12;
            return TaskState.RUNNING;
        }
        input.setJumping(false);
        if (sp.getY() >= jumpFrom.getY() + 1.01 && Placement.placeAnySupportBlock(sp, jumpFrom)) {
            jumpFrom = null;
            jumpTicks = 0;
            wait = 4;
            return TaskState.RUNNING;
        }
        if (--jumpTicks <= 0) {
            if (Boolean.getBoolean("mineagent.traceWork")) {
                System.out.println("[MineAgent] shelter: no block put under it from that jump, at " + String.format("y %.2f", sp.getY())
                        + ", the cell " + sp.serverLevel().getBlockState(jumpFrom).getBlock() + ", under it "
                        + sp.serverLevel().getBlockState(jumpFrom.below()).getBlock());
            }
            jumpFrom = null;
            wait = 4;
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
        var sp = TaskContext.serverPlayer(player);
        if (trace()) System.out.println("[MineAgent] shelter: ended in " + phase + " with " + placed.size() + " of its blocks up, at "
                + sp.blockPosition().toShortString());
        AfkFarms.clear(sp.getUUID());
        AT_HOME.remove(sp.getUUID());
        GOING_HOME.remove(sp.getUUID());
        if (nav != null) nav.cancel();
        TaskContext.inputDriver(player).clear();
        // Cut short with its walls up (a death, a fight's end, its owner's word): they're taken down later, never left
        // to shut it in (the three-world run stood a day and a half inside walls of its own).
        List<BlockPos> up = new ArrayList<>(placed);
        up.addAll(leftUp);
        leaveWalls(sp, up);
    }

    /** Its own blocks still standing, handed to its loop to take down when its body is free. */
    private void leaveWalls(ServerPlayer sp, List<BlockPos> blocks) {
        var loop = TaskContext.agentLoop(player);
        if (loop == null || blocks.isEmpty() || !record.takeDown.isEmpty() && blocks.size() >= record.takeDown.size()) return;
        List<BlockPos> standing = new ArrayList<>();
        for (BlockPos b : blocks) if (!sp.serverLevel().getBlockState(b).isAir() && !standing.contains(b)) standing.add(b.immutable());
        if (!standing.isEmpty()) loop.wallsLeft(sp.serverLevel().dimension().location().toString(), standing);
    }
}
