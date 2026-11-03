package com.mineagent.engine.world;

/**
 * When it looks again (SMH plan 70, MA5.6, MA5.7): straight away the first time, then at least every {@code every}
 * ticks, and sooner (no closer than {@code moving} ticks apart) once it has gone {@code far} blocks since it last
 * looked, as a player looks about as they walk. A companion sprinting past a bell sees it, where a fixed five-second
 * look let it run 27 blocks between looks. Pure.
 */
public final class LookSchedule {
    private final long every;
    private final long moving;
    private final double farSqr;
    private boolean looked;
    private long last;
    private double lx, ly, lz;

    public LookSchedule(long every, long moving, double far) {
        this.every = Math.max(1, every);
        this.moving = Math.max(1, Math.min(moving, this.every));
        this.farSqr = far * far;
    }

    /** Whether it looks now, at game tick {@code now}, standing at x, y, z; if so, this look is the last one. */
    public boolean due(long now, double x, double y, double z) {
        long since = now - last;
        double dx = x - lx, dy = y - ly, dz = z - lz;
        // Game time never runs backwards in one world; if it seems to, look rather than wait for it to catch up.
        boolean due = !looked || since < 0 || since >= every || (since >= moving && dx * dx + dy * dy + dz * dz >= farSqr);
        if (due) {
            looked = true;
            last = now;
            lx = x;
            ly = y;
            lz = z;
        }
        return due;
    }
}
