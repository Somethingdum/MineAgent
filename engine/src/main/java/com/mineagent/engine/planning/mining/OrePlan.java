package com.mineagent.engine.planning.mining;

import java.util.List;

/**
 * Where to dig for an ore (SMH plan 70, MA7.8: "the method per ore, from the game's ore settings"). The game places
 * each ore by features: a height range (a trapezoid peaks in its middle; a uniform range is even all through), a count
 * of tries a chunk and a vein size. The best height is the peak of the feature that puts the most ore per block of
 * height there, kept underground: a feature that peaks in the mountains (iron's upper band, emerald) is not where a
 * branch tunnel goes. Pure: {@code OreHeights} reads the features from the running game.
 */
public final class OrePlan {
    private OrePlan() {}

    /** One of the game's ore features: its shape ("trapezoid" or "uniform"), its range, tries a chunk, vein size. */
    public record Feature(String shape, int lo, int hi, double count, int size) {
        /** Ore per block of height at {@code y}: a trapezoid's falls off from its peak; a uniform range's is even. */
        double densityAt(int y) {
            if (y < lo || y > hi) return 0;
            double span = Math.max(1, hi - lo);
            if (!"trapezoid".equals(shape)) return count * Math.max(1, size) / span;
            double half = span / 2.0;
            return count * Math.max(1, size) * 2.0 / span * Math.max(0, 1 - Math.abs(y - peak()) / half);
        }

        int peak() {
            return (lo + hi) / 2;
        }
    }

    /**
     * The height to branch-mine at for an ore placed by these features: the densest feature's peak, kept between 5
     * above the world's floor (bedrock) and {@code highest} (below the surface; the sea is 63).
     */
    public static int bestY(List<Feature> features, int minY, int highest) {
        // Each feature at the height a tunnel can go nearest its peak; the most ore there wins.
        int bestY = Math.max(minY + 5, Math.min(highest, 16));
        double most = 0;
        for (Feature f : features) {
            int y = Math.max(minY + 5, Math.min(highest, f.peak()));
            double d = f.densityAt(y);
            if (d > most) {
                most = d;
                bestY = y;
            }
        }
        return bestY;
    }
}
