package com.mineagent.engine.act;

/**
 * How a player tosses an item, as the game moves it: it leaves 0.3 below the eyes at 0.3 blocks a tick along where they
 * look, with 0.1 more upward; each tick it falls 0.04 faster, moves, and keeps 98% of its speed. From that, how far a toss
 * at a pitch goes before it has fallen to a surface, and the pitch that lands it in a spot. Pure.
 */
public final class TossAim {
    private TossAim() {}

    /** Below the eyes, where a tossed item leaves the hand. */
    public static final double BELOW_EYES = 0.3;
    /** The top of a still lava or water block, above its floor. */
    public static final double FLUID_TOP = 8.0 / 9.0;

    static final double SPEED = 0.3, LIFT = 0.1, GRAVITY = 0.04, DRAG = 0.98;

    /**
     * How far (horizontally) a toss at {@code pitch} degrees (the game's: down is positive) goes before it is {@code drop}
     * blocks below where it left the hand; NaN if it never gets there within ten seconds.
     */
    public static double reach(double pitch, double drop) {
        double r = Math.toRadians(pitch);
        double vx = SPEED * Math.cos(r), vy = -SPEED * Math.sin(r) + LIFT;
        double x = 0, y = 0;
        for (int t = 0; t < 200; t++) {
            vy -= GRAVITY;
            double nx = x + vx, ny = y + vy;
            if (ny <= -drop) {
                double f = (y + drop) / (y - ny); // the part of this tick before it reached the surface
                return x + (nx - x) * f;
            }
            x = nx;
            y = ny;
            vx *= DRAG;
            vy *= DRAG;
        }
        return Double.NaN;
    }

    /**
     * The pitch (60 up to 60 down) whose toss lands {@code distance} blocks off and {@code drop} below the hand, the
     * flattest that lands within a twentieth of a block of it; NaN if none reaches.
     */
    public static double pitchFor(double distance, double drop) {
        double flattest = Double.NaN, best = Double.NaN, bestErr = Double.MAX_VALUE;
        for (double p = -60.0; p <= 60.0; p += 0.25) {
            double r = reach(p, drop);
            if (Double.isNaN(r)) continue;
            double err = Math.abs(r - distance);
            if (err <= 0.05 && (Double.isNaN(flattest) || Math.abs(p) < Math.abs(flattest))) flattest = p;
            if (err < bestErr) {
                bestErr = err;
                best = p;
            }
        }
        if (!Double.isNaN(flattest)) return flattest;
        return bestErr <= 0.25 ? best : Double.NaN;
    }
}
