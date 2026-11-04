package com.mineagent.engine.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The bow's aim, checked by flying the arrow as the game does. */
class ArrowAimTest {
    /** Fly an arrow from eye toward a yaw and pitch; its closest approach to where the target is when it arrives. */
    static double miss(double[] eye, ArrowAim.Shot s, double[] target, double[] tv, double[] sv) {
        double yaw = Math.toRadians(s.yaw()), pitch = Math.toRadians(s.pitch());
        double vx = -Math.sin(yaw) * Math.cos(pitch) * ArrowAim.SPEED + sv[0];
        double vy = -Math.sin(pitch) * ArrowAim.SPEED + sv[1];
        double vz = Math.cos(yaw) * Math.cos(pitch) * ArrowAim.SPEED + sv[2];
        double x = eye[0], y = eye[1] - ArrowAim.SPAWN_DROP, z = eye[2], best = Double.MAX_VALUE;
        for (int t = 0; t < 200; t++) {
            // Within a tick the arrow and the target both move in a straight line: the closest point on that stretch.
            double gx = target[0] + tv[0] * t, gy = target[1] + tv[1] * t, gz = target[2] + tv[2] * t;
            double rx = x - gx, ry = y - gy, rz = z - gz, dx = vx - tv[0], dy = vy - tv[1], dz = vz - tv[2];
            double dd = dx * dx + dy * dy + dz * dz, f = dd < 1e-12 ? 0 : Math.max(0, Math.min(1, -(rx * dx + ry * dy + rz * dz) / dd));
            double cx = rx + dx * f, cy = ry + dy * f, cz = rz + dz * f;
            best = Math.min(best, Math.sqrt(cx * cx + cy * cy + cz * cz));
            x += vx;
            y += vy;
            z += vz;
            vx *= ArrowAim.DRAG;
            vy = vy * ArrowAim.DRAG - ArrowAim.GRAVITY;
            vz *= ArrowAim.DRAG;
        }
        return best;
    }

    static final double[] ZERO = {0, 0, 0};

    @Test void aStandingTargetIsHitNearAndFar() {
        double[] eye = {0, 65.62, 0};
        for (double d : new double[] {8, 20, 35, 50}) {
            double[] target = {d * 0.6, 65.0, d * 0.8};
            var s = ArrowAim.solve(eye, target, ZERO, ZERO, 0, 0, 0);
            assertNotNull(s, "in range at " + d);
            assertTrue(miss(eye, s, target, ZERO, ZERO) < 0.3, "at " + d + ": missed by " + miss(eye, s, target, ZERO, ZERO));
        }
    }

    @Test void upHillAndDown() {
        double[] eye = {0, 65.62, 0};
        for (double[] target : new double[][] {{25, 75, 0}, {25, 52, 0}, {12, 70, 5}}) {
            var s = ArrowAim.solve(eye, target, ZERO, ZERO, 0, 0, 0);
            assertNotNull(s);
            assertTrue(miss(eye, s, target, ZERO, ZERO) < 0.3, "missed by " + miss(eye, s, target, ZERO, ZERO));
        }
    }

    @Test void aRunningTargetIsLed() {
        double[] eye = {0, 65.62, 0}, target = {0, 65.0, 30}, running = {0.28, 0, 0}; // a sprinting player across
        var s = ArrowAim.solve(eye, target, running, ZERO, 0, 0, 0);
        assertNotNull(s);
        assertTrue(miss(eye, s, target, running, ZERO) < 0.5, "led: missed by " + miss(eye, s, target, running, ZERO));
        var naive = ArrowAim.solve(eye, target, ZERO, ZERO, 0, 0, 0);
        assertTrue(miss(eye, naive, target, running, ZERO) > 1.5, "not led, it would miss");
    }

    @Test void whileStrafingItAimsForItsOwnMotion() {
        double[] eye = {0, 65.62, 0}, target = {0, 65.0, 25}, strafe = {0.2, 0, 0};
        var s = ArrowAim.solve(eye, target, ZERO, strafe, 0, 0, 0);
        assertTrue(miss(eye, s, target, ZERO, strafe) < 0.5, "missed by " + miss(eye, s, target, ZERO, strafe));
    }

    @Test void tooFarItHoldsItsFireAndTheErrorIsSmall() {
        assertNull(ArrowAim.solve(new double[] {0, 64, 0}, new double[] {0, 64, 90}, ZERO, ZERO, 0, 0, 0));
        double[] eye = {0, 65.62, 0}, target = {0, 65.0, 20};
        var exact = ArrowAim.solve(eye, target, ZERO, ZERO, 0, 0, 0);
        var off = ArrowAim.solve(eye, target, ZERO, ZERO, 0.35, 1, 1);
        assertEquals(0.35, Math.abs(off.yaw() - exact.yaw()), 1e-4);
        assertTrue(miss(eye, off, target, ZERO, ZERO) < 0.6, "a fraction of a degree at 20 blocks still hits a body: " + miss(eye, off, target, ZERO, ZERO));
    }

    /** The game's own scatter for a player's bow (inaccuracy 1): each part of the unit direction off by a triangle of 0.0172275. */
    static double[] scattered(ArrowAim.Shot s, double a, double b, double c) {
        double yaw = Math.toRadians(s.yaw()), pitch = Math.toRadians(s.pitch());
        double x = -Math.sin(yaw) * Math.cos(pitch) + a * 0.0172275, y = -Math.sin(pitch) + b * 0.0172275, z = Math.cos(yaw) * Math.cos(pitch) + c * 0.0172275;
        double len = Math.sqrt(x * x + y * y + z * z);
        return new double[] {x / len, y / len, z / len};
    }

    @Test void withinSureRangeTheWorstScatterStillHitsASkeleton() {
        double[] eye = {0, 65.62, 0}, target = {0, 65.0 + 1.99 * 0.55, RangedChoice.SURE};
        var s = ArrowAim.solve(eye, new double[] {target[0], target[1], target[2]}, ZERO, ZERO, 0, 0, 0);
        double worst = 0;
        for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) for (int c = -1; c <= 1; c++) {
            double[] d = scattered(s, a, b, c);
            double vx = d[0] * ArrowAim.SPEED, vy = d[1] * ArrowAim.SPEED, vz = d[2] * ArrowAim.SPEED;
            double x = eye[0], y = eye[1] - ArrowAim.SPAWN_DROP, z = eye[2];
            for (int t = 0; t < 40 && z < target[2]; t++) {
                double f = Math.min(1, (target[2] - z) / vz);
                if (f < 1) {
                    x += vx * f;
                    y += vy * f;
                    z = target[2];
                    break;
                }
                x += vx; y += vy; z += vz;
                vx *= ArrowAim.DRAG; vy = vy * ArrowAim.DRAG - ArrowAim.GRAVITY; vz *= ArrowAim.DRAG;
            }
            // Off its middle sideways, and up or down from where it was aimed.
            worst = Math.max(worst, Math.max(Math.abs(x - target[0]) / 0.6, Math.abs(y - target[1]) / (1.99 * 0.55 + 0.3)));
        }
        assertTrue(worst < 1.0, "the worst scatter at " + RangedChoice.SURE + " blocks lands inside: " + worst);
    }
}
