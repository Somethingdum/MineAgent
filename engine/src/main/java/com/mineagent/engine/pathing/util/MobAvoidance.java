package com.mineagent.engine.pathing.util;

import java.util.List;

/**
 * Hostile mobs in the path's cost (SMH plan 70, MA5.10): a cell near one costs more, the most next to it, so a path
 * goes round a creeper rather than past it. The mobs are where they stood when the search began. Pure.
 */
public final class MobAvoidance {
    /** A mob to keep clear of: within {@code radius} a cell costs up to {@code weight} more (blocks of walking). */
    public record Danger(int x, int y, int z, double radius, double weight) {}

    /** Most mobs: past the 4 blocks inside which the fight code takes one on. */
    public static final double RADIUS = 6.0;
    public static final double WEIGHT = 12.0;
    /** Creepers: they explode. */
    public static final double CREEPER_RADIUS = 8.0;
    public static final double CREEPER_WEIGHT = 24.0;

    private MobAvoidance() {}

    public static Danger mob(int x, int y, int z, boolean creeper) {
        return creeper ? new Danger(x, y, z, CREEPER_RADIUS, CREEPER_WEIGHT) : new Danger(x, y, z, RADIUS, WEIGHT);
    }

    /** The extra cost of standing at x, y, z. */
    public static double cost(List<Danger> dangers, int x, int y, int z) {
        if (dangers == null || dangers.isEmpty()) return 0.0;
        double sum = 0.0;
        for (Danger d : dangers) {
            double dx = x - d.x(), dy = (y - d.y()) * 1.5, dz = z - d.z();
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance < d.radius()) sum += d.weight() * (1.0 - distance / d.radius());
        }
        return sum;
    }
}
