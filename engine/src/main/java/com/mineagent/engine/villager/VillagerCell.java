package com.mineagent.engine.villager;

import java.util.ArrayList;
import java.util.List;

/**
 * A cell that a villager walks into by itself (SMH plan 70, its owner: "Figure out how to trap villagers. They move too
 * much to block in easily."): walls can't be put up round a villager that keeps walking off, so the cell is built first
 * round its job site, open at one end, and the villager comes to work at its job site as villagers do; standing in
 * the cell, one block shuts it in.
 *
 * <p>Along one direction {@code d} from the job site J: the cell C (beside J, where it stands to work), the way in E,
 * then the door D. Walls at feet and head on C's and E's two sides and on every other cell round J it could work from
 * (behind J and its four corners), a roof over C, E and D (so it can't climb on the door block and out), hung from a
 * block over C's side wall, and a block on J (no standing on it; each block goes against one already there, in the order
 * given). The cell is two long, C and E: at work by J the villager's body reaches over into E, and the game puts no block
 * where a body reaches; D, two out, is always clear of it. The door is D's feet block, one placement; D's head stays
 * open, a gap one high that a villager can't get through and that it is traded with through, from outside D. Pure.
 */
public final class VillagerCell {
    private VillagerCell() {}

    /** A block position. */
    public record Pos(int x, int y, int z) {
        Pos plus(int dx, int dy, int dz) {
            return new Pos(x + dx, y + dy, z + dz);
        }
    }

    /**
     * A cell's layout: {@code walls} put down first (one already solid stays as it is), {@code door} put down once it
     * is in ({@code cell} or {@code way}); it comes in by {@code way}; it is traded with from {@code tradeFrom}, through
     * the gap over the door.
     */
    public record Layout(Pos jobSite, Pos cell, Pos way, Pos door, Pos tradeFrom, List<Pos> walls) {}

    /** The layout along {@code (dx, dz)} (one of the four flat directions) from the job site at feet level {@code y}. */
    public static Layout plan(int jx, int jy, int jz, int dx, int dz) {
        if (Math.abs(dx) + Math.abs(dz) != 1) throw new IllegalArgumentException("one of the four flat directions");
        Pos j = new Pos(jx, jy, jz);
        Pos c = j.plus(dx, 0, dz);
        Pos e = c.plus(dx, 0, dz);
        Pos door = e.plus(dx, 0, dz);
        int sx = dz, sz = dx; // a side, square to d
        List<Pos> walls = new ArrayList<>();
        // The cell's sides, the way in's (inside the cell: at work by its job site its body reaches over into the way
        // in, never as far as the door), and every other cell round the job site a villager could work from.
        for (Pos p : List.of(c.plus(sx, 0, sz), c.plus(-sx, 0, -sz), e.plus(sx, 0, sz), e.plus(-sx, 0, -sz), j.plus(-dx, 0, -dz),
                j.plus(sx, 0, sz), j.plus(-sx, 0, -sz), j.plus(-dx + sx, 0, -dz + sz), j.plus(-dx - sx, 0, -dz - sz))) {
            walls.add(p);
            walls.add(p.plus(0, 1, 0));
        }
        // Roofs: over the cell and over the way in (no climbing on the door block), and a block on the job site (no
        // standing on it, and too high to climb from the cell). A block goes against another (the game's rule): first
        // one over the cell's side wall, for the cell's roof to go against, and the way in's against that; the one on the
        // job site against the inside of its side walls, in sight from the way in.
        walls.add(c.plus(sx, 2, sz));
        walls.add(c.plus(0, 2, 0));
        walls.add(j.plus(0, 1, 0));
        walls.add(e.plus(0, 2, 0));
        walls.add(door.plus(0, 2, 0));
        return new Layout(j, c, e, door, door.plus(dx, 0, dz), List.copyOf(walls));
    }

    /** The four flat directions, as {@code (dx, dz)}. */
    public static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
}
