package com.mineagent.engine.survival;

/**
 * Whether it still has what the way home needs (SMH plan 70, MA5.6): it turns back while it has 1.4 times the food
 * the way home takes. By 1.21.1's rules walking costs no food and sprinting 0.1 exhaustion a block (4 exhaustion is a
 * food point), so the way home sprinting takes a food point every 40 blocks; on top of that it keeps the 6 food a
 * player needs to sprint at all, and to heal. A way home already walked needs no digging, so no tool life. Pure.
 */
public final class ReturnReserve {
    public static final double MARGIN = 1.4;
    public static final double KEEP = 6.0;
    static final double FOOD_PER_BLOCK_SPRINTING = 0.1 / 4.0;

    private ReturnReserve() {}

    /** Food points the way home takes. */
    public static double needed(double blocksHome) {
        return Math.max(0.0, blocksHome) * FOOD_PER_BLOCK_SPRINTING;
    }

    /**
     * Whether to carry on: {@code food} and {@code saturation} as the game keeps them, {@code carried} the food points
     * (nutrition plus saturation) of the food it carries.
     */
    public static boolean enough(double food, double saturation, double carried, double blocksHome) {
        return food + saturation + carried >= MARGIN * needed(blocksHome) + KEEP;
    }
}
