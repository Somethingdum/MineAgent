package com.mineagent.engine.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Leaving before the hit that kills (SMH plan 70, MA8.2). */
final class HealthTrendTest {
    @Test void fallingFastEnoughToDieInTwoSecondsMeansLeave() {
        var t = new HealthTrend();
        // 20 health, losing 6 a second (three zombies): gone in about three seconds; at 10 left, in under two.
        double h = 20;
        for (int tick = 0; tick <= 20; tick++) t.sample(tick, h -= 0.3);
        assertEquals(0.3, t.lossPerTick(), 0.02);
        assertFalse(t.lethalSoon(), "about 14 left at 0.3 a tick: 2.3 seconds");
        for (int tick = 21; tick <= 35; tick++) t.sample(tick, h -= 0.3);
        assertTrue(t.lethalSoon(), "under 12 at 0.3 a tick: dead within two seconds");
    }

    @Test void aSingleHitOrHealingIsNotABurst() {
        var t = new HealthTrend();
        for (int tick = 0; tick < 20; tick++) t.sample(tick, 20);
        t.sample(20, 17); // one zombie hit
        assertFalse(t.lethalSoon(), "3 lost over a second, 17 left");
        var healing = new HealthTrend();
        for (int tick = 0; tick < 20; tick++) healing.sample(tick, 5 + tick * 0.1);
        assertEquals(0, healing.lossPerTick());
        assertFalse(healing.lethalSoon());
        assertFalse(new HealthTrend().lethalSoon());
    }
}
