package com.mineagent.engine.combat;

/**
 * Where an arrow will pass the companion, and whether it will pass close enough to block. Ported from
 * altoclef's ProjectileHelper and MobDefenseChain (MIT, Copyright (c) 2020 Adris Jautakas; see
 * THIRD_PARTY_NOTICES.md), in plain coordinates. One change: altoclef subtracts the vertical velocity when
 * it projects the arrow's height, which bends a rising arrow down; here height is y0 + vy*t - g*t^2/2.
 */
public final class ArrowPredictor {
    /** An arrow's gravity, blocks per tick squared (altoclef's measured value). */
    public static final double ARROW_GRAVITY = 0.05000000074505806;
    /** Thrown things (snowballs, potions). */
    public static final double THROWN_GRAVITY = 0.03;
    /** An arrow passing within this many blocks sideways and this many up or down is one to block. */
    public static final double KEEP_HORIZONTAL = 2.0;
    public static final double KEEP_VERTICAL = 10.0;
    /** Arrows farther than this (squared) are not looked at yet. */
    public static final double LOOK_RANGE_SQR = 150.0;

    private ArrowPredictor() {}

    /** The point of the arrow's path closest to the player's x-z, with the arrow's height there: {x, y, z}. */
    public static double[] closestApproach(double sx, double sy, double sz, double vx, double vy, double vz,
                                           double gravity, double px, double pz) {
        double flat = vx * vx + vz * vz;
        double t = flat <= 1e-9 ? 0.0 : ((vx * (px - sx)) + (vz * (pz - sz))) / flat;
        double hx = sx + vx * t;
        double hz = sz + vz * t;
        double travelled = Math.hypot(hx - sx, hz - sz);
        double horizontal = Math.sqrt(flat);
        double ticks = horizontal <= 1e-9 ? 0.0 : travelled / horizontal;
        double hy = sy + vy * ticks - 0.5 * gravity * ticks * ticks;
        return new double[] {hx, hy, hz};
    }

    /**
     * Whether an arrow at (s) with velocity (v) is coming, not going, and will pass within the keep distances
     * of the player at (p): then the shield goes up, facing it.
     */
    public static boolean incoming(double sx, double sy, double sz, double vx, double vy, double vz,
                                   double gravity, double px, double py, double pz) {
        double dx = px - sx, dy = py - sy, dz = pz - sz;
        if (dx * dx + dy * dy + dz * dz > LOOK_RANGE_SQR) return false;
        // Going away: the next tick it is farther.
        double nx = px - (sx + vx), ny = py - (sy + vy), nz = pz - (sz + vz);
        if (dx * dx + dy * dy + dz * dz < nx * nx + ny * ny + nz * nz) return false;
        double[] hit = closestApproach(sx, sy, sz, vx, vy, vz, gravity, px, pz);
        double hx = px - hit[0], hz = pz - hit[2];
        return hx * hx + hz * hz < KEEP_HORIZONTAL * KEEP_HORIZONTAL && Math.abs(py - hit[1]) < KEEP_VERTICAL;
    }
}
