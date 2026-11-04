package com.mineagent.engine.goal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BackoffTest {
    @Test void eachFailureWaitsTwiceAsLongUpToADay() {
        var b = new Backoff();
        assertTrue(b.ready("hoard:minecraft:diamond", 0), "never failed: ready");
        b.failed("hoard:minecraft:diamond", 0);
        assertFalse(b.ready("hoard:minecraft:diamond", 1_199));
        assertTrue(b.ready("hoard:minecraft:diamond", 1_200), "a minute after the first");
        b.failed("hoard:minecraft:diamond", 1_200);
        assertEquals(1_200L + 2_400L, b.until("hoard:minecraft:diamond"), "two after the second");
        for (int i = 0; i < 40; i++) b.failed("hoard:minecraft:diamond", 10_000);
        assertEquals(10_000L + Backoff.MOST, b.until("hoard:minecraft:diamond"), "never more than a game day, however many");
        assertTrue(b.ready("kit:minecraft:shield", 10_000), "one job's failures hold up no other");
    }

    @Test void aSuccessForgetsTheFailures() {
        var b = new Backoff();
        b.failed("xp", 0);
        b.failed("xp", 0);
        b.succeeded("xp");
        assertTrue(b.ready("xp", 0));
        assertEquals(0, b.failures("xp"));
    }

    @Test void atMostTwoHundredFiftySixJobsRemembered() {
        var b = new Backoff();
        for (int i = 0; i < 300; i++) b.failed("job" + i, 0);
        assertEquals(Backoff.MOST_KEYS, b.snapshot().size());
        assertTrue(b.ready("job0", 0), "the oldest forgotten");
        assertFalse(b.ready("job299", 0));
    }
}
