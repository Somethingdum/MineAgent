package com.mineagent.engine.task;

import com.mineagent.engine.pathing.util.BlockHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A room round where it stands, and the open cells that would let the night into it (SMH plan 70; its owner: "a shabby
 * hut", "waiting motionless until all of the mobs have burned"). From the cell it stands in, the open space a mob could
 * come through is followed out to two blocks each way across, a step under its feet and three over them; each open
 * cell just past that is a way in. A hut has its doorway, a dug room its doorway or the top of its stair, a cave pocket
 * its mouth; out in the open there are dozens, and it is no room. Open is what lets a mob through as the pathing has it:
 * air, plants, water, an open door; a closed door, glass, leaves and lava are walls.
 */
public final class Enclosure {
    /** How far the room runs each way across from where it stands. */
    public static final int ACROSS = 2;
    static final int BELOW = 1;
    static final int ABOVE = 3;
    /** More ways in than this and it is no room: out in the open, or a shell with its walls gone. */
    public static final int MOST_OPENINGS = 6;
    /** More open space than this inside and it is no room either (a cavern, a great hall). */
    static final int MOST_AIR = 60;

    /** Where it stands, the open space round it, and the open cells just past that, nearest first. */
    public record Room(BlockPos stand, Set<BlockPos> air, List<BlockPos> openings) {
        public boolean sealed() {
            return openings.isEmpty();
        }
    }

    private Enclosure() {
    }

    /** A mob could come through it. */
    static boolean open(BlockGetter level, BlockPos p) {
        return BlockHelper.isPassable(level.getBlockState(p));
    }

    static boolean inside(BlockPos stand, BlockPos p) {
        int dx = p.getX() - stand.getX(), dy = p.getY() - stand.getY(), dz = p.getZ() - stand.getZ();
        return Math.abs(dx) <= ACROSS && Math.abs(dz) <= ACROSS && dy >= -BELOW && dy <= ABOVE;
    }

    /** The room round {@code stand}, or null when there is none (too many ways in, or too much open space). */
    public static Room around(BlockGetter level, BlockPos stand) {
        stand = stand.immutable();
        Set<BlockPos> air = new HashSet<>();
        Set<BlockPos> ways = new LinkedHashSet<>();
        ArrayDeque<BlockPos> todo = new ArrayDeque<>();
        air.add(stand);
        todo.add(stand);
        while (!todo.isEmpty()) {
            BlockPos c = todo.poll();
            for (Direction d : Direction.values()) {
                BlockPos n = c.relative(d);
                if (air.contains(n) || ways.contains(n) || !open(level, n)) continue;
                if (inside(stand, n)) {
                    air.add(n);
                    todo.add(n);
                    if (air.size() > MOST_AIR) return null;
                } else {
                    ways.add(n);
                    if (ways.size() > MOST_OPENINGS) return null;
                }
            }
        }
        final BlockPos from = stand;
        List<BlockPos> openings = new ArrayList<>(ways);
        openings.sort((a, b) -> Double.compare(a.distSqr(from), b.distSqr(from)));
        return new Room(stand, Set.copyOf(air), List.copyOf(openings));
    }

    /** Somewhere to stand: open for its feet and head, ground under it. */
    static boolean standable(BlockGetter level, BlockPos p) {
        return open(level, p) && open(level, p.above()) && BlockHelper.canStandOn(level.getBlockState(p.below()));
    }

    /**
     * The best room near a place it remembers (its hut: the hut's own corner, or the front of its dug room): of the
     * cells it could stand in within three across and two up or down, the one whose room has fewest ways in, then the
     * most room inside it, then the nearest. Null when none of them is in a room.
     */
    public static Room bestNear(BlockGetter level, BlockPos near) {
        Room best = null;
        double bestScore = Double.MAX_VALUE;
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    BlockPos c = near.offset(dx, dy, dz);
                    if (!standable(level, c)) continue;
                    Room r = around(level, c);
                    if (r == null) continue;
                    double score = r.openings().size() * 1000.0 - r.air().size() * 10.0 + c.distSqr(near);
                    if (score < bestScore) {
                        bestScore = score;
                        best = r;
                    }
                }
            }
        }
        return best;
    }

    /** The cells of the room that are open to the sky (a roof with holes in it). */
    static List<BlockPos> roofless(BlockGetter level, Room room) {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos p : room.air()) {
            if (level instanceof net.minecraft.world.level.Level l && l.canSeeSky(p)) out.add(p);
        }
        return out;
    }

    /** The open cells of the room beside a way in, from which it could be closed nearer (the doorway itself). */
    static List<BlockPos> insideOf(Room room, BlockPos way) {
        List<BlockPos> out = new ArrayList<>();
        for (Direction d : Direction.values()) {
            BlockPos n = way.relative(d);
            if (room.air().contains(n) && !n.equals(room.stand()) && !n.equals(room.stand().above())) out.add(n);
        }
        return out;
    }

    /** Every cell is open: the set kept small for the tests. */
    static Set<BlockPos> copy(Set<BlockPos> s) {
        return new HashSet<>(s);
    }
}
