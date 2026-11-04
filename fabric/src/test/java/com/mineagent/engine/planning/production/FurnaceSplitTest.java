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

    @Test void lavaLightsEveryFurnaceABucketEach() {
        var s = FurnaceSplit.split(200, 13, 200, 20_000, true);
        assertEquals(13, s.furnaces(), "lava is filled again at the pool: every furnace lit");
        assertEquals(13, s.fuelTotal());
        int sum = 0;
        for (int i = 0; i < s.items().length; i++) {
            sum += s.items()[i];
            assertEquals(1, s.fuel()[i]);
        }
        assertEquals(200, sum);
        assertEquals(16L * 200, s.ticks());
        var thrifty = FurnaceSplit.split(200, 13, 200, 20_000);
        assertEquals(2, thrifty.furnaces(), "not freely: no more furnaces than pieces of fuel");
        assertEquals(2, FurnaceSplit.split(150, 1, 200, 20_000, true).fuelTotal(), "more than a bucket burns in one: two");
    }
}
