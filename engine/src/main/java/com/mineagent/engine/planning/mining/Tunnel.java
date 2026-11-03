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
