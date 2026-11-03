package com.mineagent.engine.pathing.util;

/**
 * Which drops a player survives, by 1.21.1's rules (SMH plan 70, MA5.10). Fall damage is the drop less the
 * {@code safe_fall_distance} attribute (3 by default), times {@code fall_damage_multiplier}, rounded up
 * ({@code LivingEntity#calculateFallDamage}). Landing in water of any depth, even one layer, takes none: the game looks
 * for water at the landing before it applies the fall ({@code LivingEntity#checkFallDamage}). Pure.
 */
public final class FallRules {
    /** The game's default safe fall distance. */
    public static final double SAFE_FALL = 3.0;
    /** The highest drop into water it plans (the scan down stops here). */
    public static final int MAX_WATER_DROP = 64;
    /** The highest drop onto ground with a water bucket it plans, whatever its health. */
    public static final int MAX_BUCKET_DROP = 40;
    /** Health it must keep even if the water from its bucket failed to land. */
    public static final double HEALTH_KEPT_IF_WATER_FAILS = 6.0;

    private FallRules() {}

    /** Hearts lost (half-hearts, as health) falling {@code drop} blocks onto ground. */
    public static int damage(int drop, double safeFall, double multiplier) {
        double over = drop - safeFall;
        if (over <= 0.0 || multiplier <= 0.0) return 0;
        return (int) Math.ceil(over * multiplier);
    }

    /** The highest drop onto ground that costs no health. */
    public static int safeDrop(double safeFall) {
        return (int) Math.max(0, Math.floor(safeFall));
    }

    /**
     * The highest drop onto ground it plans with a water bucket: one it would survive with {@link
     * #HEALTH_KEPT_IF_WATER_FAILS} to spare if the water failed (0: none).
     */
    public static int maxBucketDrop(double health, double safeFall, double multiplier) {
        int best = 0;
        for (int drop = safeDrop(safeFall) + 1; drop <= MAX_BUCKET_DROP; drop++) {
            if (health - damage(drop, safeFall, multiplier) < HEALTH_KEPT_IF_WATER_FAILS) break;
            best = drop;
        }
        return best;
    }
}
