package com.mineagent.engine.planning.production;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Smelting shared over furnaces by whole pieces of fuel (SMH plan 70, production). */
final class FurnaceSplitTest {
    @Test void aCoalBurnsInOneFurnaceSoTwoCoalLightTwo() {
        var s = FurnaceSplit.split(16, 4, 200, 1600);
        assertEquals(2, s.furnaces());
        assertTrue(java.util.Arrays.equals(new int[] {8, 8}, s.items()), java.util.Arrays.toString(s.items()));
        assertEquals(2, s.fuelTotal());
        assertEquals(1600L, s.ticks());
    }

    @Test void aChestOfStoneOverSixteenFurnacesTakesExactlyItsCoal() {
        var s = FurnaceSplit.split(1_728, 16, 200, 1600);
        assertEquals(16, s.furnaces());
        assertEquals(216, s.fuelTotal(), "8 items a coal, none wasted");
        int total = 0;
        for (int n : s.items()) {
            total += n;
            assertEquals(0, n % 8, "whole coals' worth in each");
        }
        assertEquals(1_728, total);
        assertEquals(112L * 200, s.ticks());
    }

    @Test void woodFuelIsSpreadAndRoundedUpPerFurnace() {
        var s = FurnaceSplit.split(10, 3, 200, 300);
        assertEquals(3, s.furnaces());
        int total = 0;
        for (int n : s.items()) total += n;
        assertEquals(10, total);
        assertEquals(7, s.fuelTotal(), "a plank smelts one and a half: 3, 2 and 2");
    }

    @Test void oneFurnaceAtLeast() {
        var s = FurnaceSplit.split(5, 0, 200, 1600);
        assertEquals(1, s.furnaces());
        assertEquals(1, s.fuelTotal());
    }
}
