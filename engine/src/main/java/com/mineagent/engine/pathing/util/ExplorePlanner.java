package com.mineagent.engine.pathing.util;

import com.mineagent.engine.world.ChunkSummaries;

import java.util.Set;

/**
 * Where to look next (SMH plan 70, MA5.6): the nearest chunk it has never seen, ring by ring outward from where it
 * is, skipping any it tried and could not reach. So "find a village" covers new ground in a widening spiral, never
 * the same ground twice. Pure.
 */
public final class ExplorePlanner {
    private ExplorePlanner() {}

    /** The nearest unseen chunk within {@code radius} chunks (as {cx, cz}), or null when all of it has been seen. */
    public static int[] nextUnseen(ChunkSummaries map, String dim, int cx, int cz, int radius, Set<Long> skip) {
        for (int r = 1; r <= radius; r++) {
            int[] best = null;
            double bestD = Double.MAX_VALUE;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue; // this ring only
                    int x = cx + dx, z = cz + dz;
                    if (skip != null && skip.contains(ChunkSummaries.key(x, z))) continue;
                    if (map.seen(dim, x, z)) continue;
                    double d = dx * dx + dz * dz;
                    if (d < bestD) {
                        bestD = d;
                        best = new int[] {x, z};
                    }
                }
            }
            if (best != null) return best;
        }
        return null;
    }
}
