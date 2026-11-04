package com.mineagent.engine.combat;

/**
 * Aiming a bow as the game flies an arrow (SMH plan 70, "clutch with a bow"): each tick the arrow moves by its velocity,
 * then its velocity is kept at 0.99 (air) and loses 0.05 downward (gravity); it leaves the bow from the eyes less 0.1,
 * at 3 blocks a tick drawn full, with the shooter's own motion added. The pitch that puts it on a point is found by
 * flying it in memory (the low arc), and a moving target is led by where it will be when the arrow gets there, from how
 * it has been moving. A little error is left in on purpose (a fraction of a degree): a good archer, not a machine.
 * Pure.
 */
public final class ArrowAim {
    private ArrowAim() {}

    public static final double SPEED = 3.0, DRAG = 0.99, GRAVITY = 0.05, SPAWN_DROP = 0.1;
    /** Further than this it holds its fire: the spread at that range makes it a waste of arrows. */
    public static final double MAX_RANGE = 60.0;

    /** A solution: where to look, and how long the arrow takes to get there. */
    public record Shot(float yaw, float pitch, int ticks, double missBy) {}

    /**
     * Height of the arrow ({@code y}) and its distance along the ground ({@code d}) after {@code t} ticks, for a pitch
     * in degrees (the game's: negative is up), launched at {@code speed}.
     */
    public static double[] fly(double pitchDeg, double speed, int t) {
        double p = Math.toRadians(-pitchDeg);
        double vd = Math.cos(p) * speed, vy = Math.sin(p) * speed, d = 0, y = 0;
        for (int i = 0; i < t; i++) {
            d += vd;
            y += vy;
            vd *= DRAG;
            vy = vy * DRAG - GRAVITY;
        }
        return new double[] {d, y};
    }

    /**
     * The arrow's height where it has gone {@code dist} along the ground, and the ticks it took (interpolated); NaN when
     * it falls 64 below first or never gets that far.
     */
    static double[] heightAt(double pitchDeg, double speed, double dist) {
        double p = Math.toRadians(-pitchDeg);
        double vd = Math.cos(p) * speed, vy = Math.sin(p) * speed, d = 0, y = 0;
        for (int i = 0; i < 400; i++) {
            double nd = d + vd, ny = y + vy;
            if (nd >= dist) {
                double f = vd <= 1e-9 ? 0 : (dist - d) / vd;
                return new double[] {y + (ny - y) * f, i + f};
            }
            d = nd;
            y = ny;
            vd *= DRAG;
            vy = vy * DRAG - GRAVITY;
            if (y < -64 || vd < 1e-4) break;
        }
        return new double[] {Double.NaN, Double.NaN};
    }

    /**
     * The pitch (degrees, the game's sign) of the low arc that puts the arrow {@code rise} blocks above where it left at
     * {@code dist} along the ground; NaN when out of reach.
     */
    public static double pitchFor(double dist, double rise, double speed) {
        if (dist < 0.5) return rise >= 0 ? -89 : 89;
        // The low arc: from straight at it upward, the first pitch whose arrow is high enough there.
        double lo = Math.toDegrees(-Math.atan2(rise, dist)) + 30; // well below
        double hi = -45; // the highest of the low arcs (beyond it the arc comes down steeper)
        lo = Math.min(89, lo);
        double hLo = heightAt(lo, speed, dist)[0], hHi = heightAt(hi, speed, dist)[0];
        if (Double.isNaN(hHi) || hHi < rise) return Double.NaN; // can't reach it even at 45 degrees
        if (!Double.isNaN(hLo) && hLo > rise) return lo;
        for (int i = 0; i < 40; i++) {
            double mid = (lo + hi) / 2;
            double h = heightAt(mid, speed, dist)[0];
            if (Double.isNaN(h) || h < rise) lo = mid;
            else hi = mid;
        }
        return (lo + hi) / 2;
    }

    /**
     * Where to look to hit a target: from {@code eye} (the shooter's eyes), at {@code target} (a point on it), moving
     * {@code tv} a tick, the shooter moving {@code sv} a tick (the arrow takes it on; the game adds the shooter's upward
     * or downward motion only while it is off the ground, so the caller gives 0 there when standing). {@code noise} in degrees of error
     * left in (0 for none); {@code rnd} a number from -1 to 1 for each of yaw and pitch. Null when out of range.
     */
    public static Shot solve(double[] eye, double[] target, double[] tv, double[] sv, double noise, double rndYaw, double rndPitch) {
        double ex = eye[0], ey = eye[1] - SPAWN_DROP, ez = eye[2];
        double tx = target[0], ty = target[1], tz = target[2];
        double rx = tv[0] - sv[0], rz = tv[2] - sv[2]; // where it goes, as the arrow sees it (it carries the shooter's motion)
        double ry = tv[1] - sv[1];
        double px = tx, py = ty, pz = tz;
        int ticks = 0;
        double pitch = 0;
        for (int i = 0; i < 4; i++) {
            double dx = px - ex, dz = pz - ez, dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > MAX_RANGE) return null;
            pitch = pitchFor(dist, py - ey, SPEED);
            if (Double.isNaN(pitch)) return null;
            double t = heightAt(pitch, SPEED, dist)[1];
            if (Double.isNaN(t)) return null;
            ticks = (int) Math.ceil(t);
            px = tx + rx * t;
            py = ty + Math.max(-3, ry * t); // a falling target leads down, but not through the floor
            pz = tz + rz * t;
        }
        double dx = px - ex, dz = pz - ez;
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        double dist = Math.sqrt(dx * dx + dz * dz);
        double h = heightAt(pitch, SPEED, dist)[0];
        double miss = Double.isNaN(h) ? Double.POSITIVE_INFINITY : Math.abs(h - (py - ey));
        return new Shot((float) (yaw + noise * rndYaw), (float) (pitch + noise * rndPitch), ticks, miss);
    }
}
