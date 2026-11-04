package com.mineagent.engine.planning.end;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** SMH plan 70, MA11.5: the stronghold from eye-of-ender throws. */
final class EyeTriangulationTest {
    private static EyeTriangulation.Throw toward(double x, double z, double sx, double sz) {
        return new EyeTriangulation.Throw(x, z, sx - x, sz - z);
    }

    @Test void twoThrowsCrossAtTheStronghold() {
        var at = EyeTriangulation.locate(List.of(toward(0, 0, 1200, -800), toward(300, 0, 1200, -800))).orElseThrow();
        assertEquals(1200, at[0], 1e-6);
        assertEquals(-800, at[1], 1e-6);
    }

    @Test void noisyThrowsLandNearIt() {
        // Each eye's flight half a degree off, as the game's are read; thrown from places a few hundred blocks apart
        // (the second off to the side, as secondThrowFrom says): within a few dozen blocks of a stronghold 1,000 out.
        double sx = 800, sz = 600;
        double[][] from = {{0, 0}, {500, -200}, {-100, 400}};
        double[] off = {0.009, -0.008, 0.006};
        java.util.List<EyeTriangulation.Throw> eyes = new java.util.ArrayList<>();
        for (int i = 0; i < from.length; i++) {
            double a = Math.atan2(sz - from[i][1], sx - from[i][0]) + off[i];
            eyes.add(new EyeTriangulation.Throw(from[i][0], from[i][1], Math.cos(a), Math.sin(a)));
        }
        var at = EyeTriangulation.locate(eyes).orElseThrow();
        assertTrue(Math.hypot(at[0] - sx, at[1] - sz) < 40, "landed " + Math.hypot(at[0] - sx, at[1] - sz) + " blocks off");
    }

    @Test void parallelThrowsFixNothing() {
        assertTrue(EyeTriangulation.locate(List.of(new EyeTriangulation.Throw(0, 0, 1, 0), new EyeTriangulation.Throw(0, 5, 1, 0.01))).isEmpty());
        assertTrue(EyeTriangulation.locate(List.of(new EyeTriangulation.Throw(0, 0, 1, 0))).isEmpty(), "one throw is a line, not a point");
    }

    @Test void theSecondThrowIsOffToTheSide() {
        var first = new EyeTriangulation.Throw(0, 0, 1, 0);
        double[] from = EyeTriangulation.secondThrowFrom(first, 200);
        assertEquals(0, from[0], 1e-9);
        assertEquals(200, Math.abs(from[1]), 1e-9);
    }
}
