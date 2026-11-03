package com.mineagent.engine.loop;

/**
 * The AFK rules (SMH plan 70, MA5.4) that are numbers and words. While the owner is AFK it keeps working within
 * {@link #WORK_LEASH} blocks of them; at a farm (fishing, or standing at a farm's collection hoppers) within
 * {@link #FARM_LEASH} (the companion counts as a player for mob spawning, and wandering moves where mobs spawn), and
 * it places and breaks nothing within {@link #FARM_QUIET} of them. Questions wait until they're back. Pure.
 */
public final class AfkRules {
    public static final double WORK_LEASH = 32.0;
    public static final double FARM_LEASH = 8.0;
    public static final double FARM_QUIET = 16.0;
    /** Hoppers this close to an AFK player, this many or more, are a farm's collection: they're AFK at a farm. */
    public static final int FARM_HOPPERS = 2;
    public static final int HOPPER_RANGE = 6;

    private AfkRules() {}

    public static boolean atFarm(boolean fishing, int hoppersNear) {
        return fishing || hoppersNear >= FARM_HOPPERS;
    }

    /** How far from an AFK owner it may work. */
    public static double leash(boolean atFarm) {
        return atFarm ? FARM_LEASH : WORK_LEASH;
    }

    /** A line that asks them something, which waits until they're back. */
    public static boolean isQuestion(String text) {
        return text != null && text.strip().endsWith("?");
    }
}
