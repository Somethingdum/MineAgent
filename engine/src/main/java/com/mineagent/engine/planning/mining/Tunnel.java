package com.mineagent.engine.planning.mining;

import java.util.ArrayList;
import java.util.List;

/**
 * The way a branch mine is dug (SMH plan 70, MA7.8), as a list of steps: a staircase down to the ore's height (never
 * straight down: a hole underfoot can open on lava or a drop), then a main tunnel two high, with branches to both sides
 * every third block (two blocks of rock between, so every block between them is seen from one or the other), each
 * branch walked back to the main tunnel. Pure: the mining task digs each step's cells and stands on its feet cell.
 */
public final class Tunnel {
    public enum Kind { STAIR, MAIN, BRANCH, RETURN }

    /**
     * Stand at {@code (x, y, z)} once {@code clear} is dug out. A RETURN step only walks back (to the main tunnel);
     * {@code branch} numbers the branch a BRANCH or RETURN step belongs to (-1: the stairs and the main tunnel).
     */
    public record Step(Kind kind, int x, int y, int z, List<int[]> clear, int branch) {}

    /** Main tunnel length, branch length, blocks between branches. */
    public static final int MAIN = 60, BRANCH = 16, EVERY = 3;

    private Tunnel() {}

    /** From feet at {@code (x, y, z)}, heading {@code (dx, dz)} (one of the four), down to {@code targetY}, then branches. */
    public static List<Step> plan(int x, int y, int z, int dx, int dz, int targetY) {
        return plan(x, y, z, dx, dz, targetY, MAIN, BRANCH);
    }

    public static List<Step> plan(int x, int y, int z, int dx, int dz, int targetY, int main, int branch) {
        List<Step> out = new ArrayList<>();
        int cx = x, cy = y, cz = z;
        // Down: each step one forward and one down, with headroom to walk down into it.
        while (cy > targetY) {
            int nx = cx + dx, ny = cy - 1, nz = cz + dz;
            out.add(new Step(Kind.STAIR, nx, ny, nz, List.of(new int[] {nx, ny, nz}, new int[] {nx, ny + 1, nz}, new int[] {nx, ny + 2, nz}), -1));
            cx = nx;
            cy = ny;
            cz = nz;
        }
        // Up, where it starts below the height (it walks up a staircase the same way).
        while (cy < targetY) {
            int nx = cx + dx, ny = cy + 1, nz = cz + dz;
            out.add(new Step(Kind.STAIR, nx, ny, nz, List.of(new int[] {cx, cy + 2, cz}, new int[] {nx, ny, nz}, new int[] {nx, ny + 1, nz}), -1));
            cx = nx;
            cy = ny;
            cz = nz;
        }
        int lx = dz, lz = -dx; // left of the heading: facing east (+x), north (-z)
        int b = 0;
        for (int i = 1; i <= main; i++) {
            int mx = cx + dx * i, mz = cz + dz * i;
            out.add(new Step(Kind.MAIN, mx, cy, mz, two(mx, cy, mz), -1));
            if (i % EVERY != 0) continue;
            for (int side : new int[] {1, -1}) {
                for (int j = 1; j <= branch; j++) {
                    int bx = mx + lx * side * j, bz = mz + lz * side * j;
                    out.add(new Step(Kind.BRANCH, bx, cy, bz, two(bx, cy, bz), b));
                }
                out.add(new Step(Kind.RETURN, mx, cy, mz, List.of(), b));
                b++;
            }
        }
        return out;
    }

    /** A slime room's floor height: slimes spawn in a slime chunk only below Y 40, and a big one is two blocks tall. */
    public static final int SLIME_ROOM_Y = 36;

    /**
     * A room dug out, as for a slime farm (SMH plan 70: "highly favoring of slime"): from feet at {@code (x, y, z)}, a
     * staircase down to {@code targetY} heading toward the room, a tunnel two high at that height to its corner {@code
     * (x0, z0)}, then the room, {@code size} by {@code size} and three high (room for the biggest slime), row by row:
     * each row dug from its first cell out, then walked back to it (a row given up for lava or water goes back the same
     * way); the next row starts beside the last one's first cell.
     */
    public static List<Step> room(int x, int y, int z, int targetY, int x0, int z0, int size) {
        List<Step> out = new ArrayList<>();
        int mid = size / 2;
        int ddx = x0 + mid - x, ddz = z0 + mid - z;
        int dx = Math.abs(ddx) >= Math.abs(ddz) ? Integer.signum(ddx) : 0;
        int dz = dx == 0 ? (ddz >= 0 ? 1 : -1) : 0;
        if (dx == 0 && dz == 0) dx = 1;
        int cx = x, cy = y, cz = z;
        while (cy > targetY) {
            int nx = cx + dx, ny = cy - 1, nz = cz + dz;
            out.add(new Step(Kind.STAIR, nx, ny, nz, List.of(new int[] {nx, ny, nz}, new int[] {nx, ny + 1, nz}, new int[] {nx, ny + 2, nz}), -1));
            cx = nx;
            cy = ny;
            cz = nz;
        }
        while (cy < targetY) {
            int nx = cx + dx, ny = cy + 1, nz = cz + dz;
            out.add(new Step(Kind.STAIR, nx, ny, nz, List.of(new int[] {cx, cy + 2, cz}, new int[] {nx, ny, nz}, new int[] {nx, ny + 1, nz}), -1));
            cx = nx;
            cy = ny;
            cz = nz;
        }
        // Along x, then along z, to the corner.
        while (cx != x0) {
            cx += Integer.signum(x0 - cx);
            out.add(new Step(Kind.MAIN, cx, cy, cz, two(cx, cy, cz), -1));
        }
        while (cz != z0) {
            cz += Integer.signum(z0 - cz);
            out.add(new Step(Kind.MAIN, cx, cy, cz, two(cx, cy, cz), -1));
        }
        for (int r = 0; r < size; r++) {
            int rz = z0 + r;
            for (int i = 0; i < size; i++) {
                int rx = x0 + i;
                out.add(new Step(Kind.BRANCH, rx, cy, rz, List.of(new int[] {rx, cy, rz}, new int[] {rx, cy + 1, rz}, new int[] {rx, cy + 2, rz}), r));
            }
            out.add(new Step(Kind.RETURN, x0, cy, rz, List.of(), r));
        }
        return out;
    }

    /**
     * Chunks out from {@code (cx, cz)}, ring by ring to {@code radius}, nearest first within a ring: the first {@code test}
     * passes ({@code {x, z}}), or null.
     */
    public static int[] nearestChunk(int cx, int cz, int radius, java.util.function.BiPredicate<Integer, Integer> test) {
        for (int r = 0; r <= radius; r++) {
            int[] best = null;
            int bestD = Integer.MAX_VALUE;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    int d = dx * dx + dz * dz;
                    if (d < bestD && test.test(cx + dx, cz + dz)) {
                        bestD = d;
                        best = new int[] {cx + dx, cz + dz};
                    }
                }
            }
            if (best != null) return best;
        }
        return null;
    }

    private static List<int[]> two(int x, int y, int z) {
        return List.of(new int[] {x, y, z}, new int[] {x, y + 1, z});
    }

    /** The index of the RETURN step of the branch {@code steps[from]} is in (to give up the rest of it), or -1. */
    public static int returnOf(List<Step> steps, int from) {
        int branch = steps.get(from).branch();
        if (branch < 0) return -1;
        for (int i = from; i < steps.size(); i++) {
            if (steps.get(i).kind() == Kind.RETURN && steps.get(i).branch() == branch) return i;
        }
        return -1;
    }
}
