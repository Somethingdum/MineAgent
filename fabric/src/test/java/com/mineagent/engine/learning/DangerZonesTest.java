package com.mineagent.engine.learning;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Repeat deaths (SMH plan 70, MA8.6). */
final class DangerZonesTest {
    static final String OW = "minecraft:overworld";

    @Test void threeDeathsAtOnePlaceInFifteenMinutesMarkIt() {
        var z = new DangerZones();
        assertTrue(z.died(OW, 100, 12, 100, 0).isEmpty());
        assertTrue(z.died(OW, 104, 12, 98, 3_000).isEmpty());
        var zone = z.died(OW, 101, 11, 103, 6_000);
        assertTrue(zone.isPresent(), "the third, close by, within 15 minutes");
        assertTrue(z.dangerous(OW, 102, 12, 100, 6_100));
        assertFalse(z.dangerous(OW, 160, 12, 100, 6_100), "far from it is not");
        assertFalse(z.dangerous("minecraft:the_nether", 102, 12, 100, 6_100), "nor another dimension");
        assertTrue(z.died(OW, 100, 12, 100, 6_500).isEmpty(), "a fourth makes no second zone");
        assertFalse(z.dangerous(OW, 102, 12, 100, 6_000 + 3 * 24_000L + 1), "three days on, it is forgotten");
    }

    @Test void spreadOutOrSlowDeathsDoNot() {
        var far = new DangerZones();
        far.died(OW, 0, 64, 0, 0);
        far.died(OW, 100, 64, 0, 100);
        assertTrue(far.died(OW, 200, 64, 0, 200).isEmpty(), "three places, not one");
        var slow = new DangerZones();
        slow.died(OW, 0, 64, 0, 0);
        slow.died(OW, 2, 64, 0, 10_000);
        assertTrue(slow.died(OW, 1, 64, 1, 20_000).isEmpty(), "the first was more than 15 minutes before");
    }
}
