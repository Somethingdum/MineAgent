package com.mineagent.engine.planning.production;

/**
 * Smelting shared over furnaces by whole pieces of fuel (SMH plan 70, production). A piece of fuel burns in one
 * furnace: a coal smelts 8 items there and nothing anywhere else, so each furnace is given its items in whole fuel's
 * worth (8 for coal) and the fuel they burn, and no more furnaces are lit than there are pieces of fuel to light them.
 * Pure: the planner prices with it and the production task loads its furnaces by it.
 */
public final class FurnaceSplit {
    /**
     * @param items  per furnace, how many it smelts
     * @param fuel   per furnace, the fuel those take
     * @param ticks  how long the busiest furnace takes
     */
    public record Split(int[] items, int[] fuel, int furnaces, int fuelTotal, long ticks) {}

    private FurnaceSplit() {}

    /** {@code items} to smelt at {@code cookTicks} each, in at most {@code furnaces} furnaces, burning fuel of {@code fuelTicks}. */
    public static Split split(int items, int furnaces, int cookTicks, int fuelTicks) {
        return split(items, furnaces, cookTicks, fuelTicks, false);
    }

    /**
     * As {@link #split(int, int, int, int)}; {@code freely}: a fuel cheap to have more of than it burns (a bucket of
     * lava, filled again at the pool), so every furnace asked for is lit, each with a piece, part of it left unburnt.
     */
    public static Split split(int items, int furnaces, int cookTicks, int fuelTicks, boolean freely) {
        int cook = Math.max(1, cookTicks);
        int burn = Math.max(1, fuelTicks);
        // Items a piece of fuel smelts in full (a coal 8; a plank, 300 ticks, one and a half: 1 in full).
        int per = Math.max(1, burn / cook);
        if (freely) per = Math.max(1, Math.min(per, (int) Math.ceil(items / (double) Math.max(1, Math.min(furnaces, items)))));
        int units = (int) Math.ceil(items / (double) per);
        int lit = Math.max(1, Math.min(Math.max(1, furnaces), Math.max(1, units)));
        int[] each = new int[lit];
        int[] fuel = new int[lit];
        int left = items;
        for (int i = 0; i < lit; i++) {
            int u = units / lit + (i < units % lit ? 1 : 0);
            each[i] = Math.min(left, u * per);
            left -= each[i];
        }
        // What does not fit in whole units (wood-like fuel): spread over the furnaces.
        for (int i = 0; left > 0; i = (i + 1) % lit) {
            each[i]++;
            left--;
        }
        int total = 0, most = 0;
        for (int i = 0; i < lit; i++) {
            fuel[i] = each[i] == 0 ? 0 : (int) Math.ceil(each[i] * (double) cook / burn);
            total += fuel[i];
            most = Math.max(most, each[i]);
        }
        return new Split(each, fuel, lit, total, (long) most * cook);
    }
}
