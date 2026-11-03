package com.mineagent.engine.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** When it looks again (SMH plan 70, MA5.6, MA5.7). */
final class LookScheduleTest {
    @Test void itLooksStraightAwayInAWorldOfAnyAge() {
        // The timers this replaced started at Long.MIN_VALUE: "now - MIN_VALUE" overflowed and it never looked.
        for (long now : new long[] {0L, 7_880L, 24_000_000L, Long.MAX_VALUE / 2}) {
            assertTrue(new LookSchedule(100, 20, 8.0).due(now, 0, 64, 0), "first look at tick " + now);
        }
    }

    @Test void standingStillItLooksEveryInterval() {
        LookSchedule look = new LookSchedule(100, 20, 8.0);
        assertTrue(look.due(1_000, 0, 64, 0));
        assertFalse(look.due(1_050, 0, 64, 0), "not again so soon");
        assertFalse(look.due(1_099, 0, 64, 0));
        assertTrue(look.due(1_100, 0, 64, 0), "a hundred ticks on");
    }

    @Test void onTheMoveItLooksEveryFewBlocks() {
        LookSchedule look = new LookSchedule(100, 20, 8.0);
        assertTrue(look.due(1_000, 0, 64, 0));
        assertFalse(look.due(1_010, 9, 64, 0), "gone far, but no closer than 20 ticks apart");
        assertTrue(look.due(1_020, 9, 64, 0), "gone 9 blocks and 20 ticks on");
        assertFalse(look.due(1_040, 13, 64, 0), "only 4 blocks since that look");
        // Sprinting (5.6 blocks a second) it is never more than 8 blocks between looks.
        LookSchedule sprint = new LookSchedule(100, 20, 8.0);
        double z = 0;
        double lastZ = Double.NaN;
        double widest = 0;
        for (long t = 0; t <= 400; t++, z += 0.28) {
            if (sprint.due(t, 0, 64, z)) {
                if (!Double.isNaN(lastZ)) widest = Math.max(widest, z - lastZ);
                lastZ = z;
            }
        }
        assertTrue(widest <= 8.0 + 0.28 + 1e-9, "widest gap between looks " + widest);
    }

    @Test void timeThatSeemsToGoBackLooksRatherThanWaits() {
        LookSchedule look = new LookSchedule(100, 20, 8.0);
        assertTrue(look.due(5_000, 0, 64, 0));
        assertTrue(look.due(10, 0, 64, 0), "a new world's clock: look, don't wait 5,000 ticks");
        assertFalse(look.due(20, 0, 64, 0));
    }
}
