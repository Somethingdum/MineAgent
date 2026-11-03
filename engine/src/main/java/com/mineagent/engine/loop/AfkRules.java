package com.mineagent.engine.loop;

/**
 * The AFK rules (SMH plan 70, MA5.4) that are numbers and words. AFK only means the owner is away: it keeps on with
 * its jobs, as far as they take it, unless the owner set a distance to keep within. At a farm they're AFK at (fishing,
 * or standing at a farm's collection hoppers) it places and breaks nothing within {@link #FARM_QUIET} of them.
 * Questions wait until they're back. Pure.
 */
public final class AfkRules {
    public static final double FARM_QUIET = 16.0;
    /** Hoppers this close to an AFK player, this many or more, are a farm's collection: they're AFK at a farm. */
    public static final int FARM_HOPPERS = 2;
    public static final int HOPPER_RANGE = 6;

    private AfkRules() {}

    public static boolean atFarm(boolean fishing, int hoppersNear) {
        return fishing || hoppersNear >= FARM_HOPPERS;
    }

    /** How far from an AFK owner it may go: the distance they set, or no limit (0, the default). */
    public static double leash(int stayWithin) {
        return stayWithin > 0 ? stayWithin : Double.POSITIVE_INFINITY;
    }

    /** A line that asks them something, which waits until they're back. */
    public static boolean isQuestion(String text) {
        return text != null && text.strip().endsWith("?");
    }
}
