package com.mineagent.engine.pathing.util;

import com.mineagent.engine.world.ChunkSummaries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Long trips planned over chunks first (SMH plan 70, MA5.10): A* over the 16-by-16 columns, by what it has seen of
 * them (land is cheapest, water slower, steep ground and lava dearer, ground it hasn't seen a little dearer than
 * land), then the block pathfinder walks it leg by leg. A trip of a thousand blocks never asks one search for every
 * block. Pure.
 */
public final class CoarsePlanner {
    /** Chunks around the straight line it may go out of its way, each side. */
    static final int MARGIN = 24;
    /** Chunks it looks at at most. */
    static final int MAX_NODES = 40_000;
    /** At most this many chunks between waypoints on a straight stretch. */
    static final int STRETCH = 4;

    private CoarsePlanner() {}

    static double factor(ChunkSummaries.Summary s) {
        return switch (s.kind()) {
            case UNKNOWN -> 1.15;
            case LAND -> s.steep() ? 1.6 : 1.0;
            case WATER -> 2.2;
            case HAZARD -> 8.0;
        };
    }

    /**
     * Waypoints (block x, y, z at chunk centres; y is the surface where known, else {@code fromY}) from one block
     * position to another, the start's chunk left out and the goal's put in; empty when they share a chunk or no way
     * was found within its budget.
     */
    public static List<int[]> plan(ChunkSummaries map, String dim, int fromX, int fromY, int fromZ, int toX, int toZ) {
        int sx = fromX >> 4, sz = fromZ >> 4, gx = toX >> 4, gz = toZ >> 4;
        if (sx == gx && sz == gz) return List.of();
        int minX = Math.min(sx, gx) - MARGIN, maxX = Math.max(sx, gx) + MARGIN;
        int minZ = Math.min(sz, gz) - MARGIN, maxZ = Math.max(sz, gz) + MARGIN;
        Map<Long, Double> cost = new HashMap<>();
        Map<Long, Long> from = new HashMap<>();
        PriorityQueue<double[]> open = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
        long start = ChunkSummaries.key(sx, sz), goal = ChunkSummaries.key(gx, gz);
        cost.put(start, 0.0);
        open.add(new double[] {h(sx, sz, gx, gz), sx, sz});
        int seen = 0;
        boolean found = false;
        while (!open.isEmpty() && seen++ < MAX_NODES) {
            double[] n = open.poll();
            int cx = (int) n[1], cz = (int) n[2];
            long k = ChunkSummaries.key(cx, cz);
            if (k == goal) {
                found = true;
                break;
            }
            double here = cost.get(k);
            if (n[0] > here + h(cx, cz, gx, gz) + 1e-9) continue; // a stale entry
            ChunkSummaries.Summary sHere = map.get(dim, cx, cz);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    int nx = cx + dx, nz = cz + dz;
                    if (nx < minX || nx > maxX || nz < minZ || nz > maxZ) continue;
                    ChunkSummaries.Summary s = map.get(dim, nx, nz);
                    double step = (dx != 0 && dz != 0 ? Math.sqrt(2.0) : 1.0) * factor(s);
                    if (s.kind() != ChunkSummaries.Kind.UNKNOWN && sHere.kind() != ChunkSummaries.Kind.UNKNOWN) {
                        step += Math.abs(s.height() - sHere.height()) / 8.0;
                    }
                    long nk = ChunkSummaries.key(nx, nz);
                    double c = here + step;
                    Double old = cost.get(nk);
                    if (old != null && old <= c) continue;
                    cost.put(nk, c);
                    from.put(nk, k);
                    open.add(new double[] {c + h(nx, nz, gx, gz), nx, nz});
                }
            }
        }
        if (!found) return List.of();
        List<long[]> chunks = new ArrayList<>();
        for (Long k = goal; k != null && k != start; k = from.get(k)) chunks.add(new long[] {k >> 32, (int) (long) k});
        Collections.reverse(chunks);
        List<int[]> out = new ArrayList<>();
        int[] lastDir = null;
        long[] previous = {sx, sz};
        int sinceKept = 0;
        for (int i = 0; i < chunks.size(); i++) {
            long[] c = chunks.get(i);
            int[] dir = {Long.signum(c[0] - previous[0]), Long.signum(c[1] - previous[1])};
            boolean last = i == chunks.size() - 1;
            boolean turn = lastDir != null && (dir[0] != lastDir[0] || dir[1] != lastDir[1]);
            sinceKept++;
            if (turn && i > 0) {
                out.add(centre(map, dim, chunks.get(i - 1), fromY));
                sinceKept = 1;
            } else if (sinceKept >= STRETCH && !last) {
                out.add(centre(map, dim, c, fromY));
                sinceKept = 0;
            }
            if (last) out.add(centre(map, dim, c, fromY));
            lastDir = dir;
            previous = c;
        }
        return out;
    }

    private static int[] centre(ChunkSummaries map, String dim, long[] c, int fallbackY) {
        ChunkSummaries.Summary s = map.get(dim, (int) c[0], (int) c[1]);
        int y = s.kind() == ChunkSummaries.Kind.UNKNOWN ? fallbackY : s.height();
        return new int[] {(int) c[0] * 16 + 8, y, (int) c[1] * 16 + 8};
    }

    /** Straight-line chunks to the goal, at the cost of land. */
    private static double h(int x, int z, int gx, int gz) {
        int dx = Math.abs(x - gx), dz = Math.abs(z - gz);
        return Math.max(dx, dz) + (Math.sqrt(2.0) - 1.0) * Math.min(dx, dz);
    }
}
