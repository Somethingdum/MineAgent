package com.mineagent.engine.combat;

import java.util.ArrayDeque;

/**
 * How fast its health is falling (SMH plan 70, MA8.2; Andy Omega's LETHAL_BURST): health falling fast enough to be
 * gone within two seconds means leave now, before the hit that kills, whatever the fight looks like. Pure: the defence
 * chain feeds it a sample a tick.
 */
public final class HealthTrend {
    /** How far back it looks, and how far ahead it fears: one second back, two ahead. */
    static final int WINDOW = 20, AHEAD = 40;

    private final ArrayDeque<long[]> samples = new ArrayDeque<>();

    /** Health at a tick (as hundredths, so a sample is two longs). */
    public void sample(long tick, double health) {
        samples.addLast(new long[] {tick, Math.round(health * 100)});
        while (!samples.isEmpty() && tick - samples.peekFirst()[0] > WINDOW) samples.pollFirst();
    }

    /** Health lost a tick, over the window (0 when it is steady or rising). */
    public double lossPerTick() {
        if (samples.size() < 2) return 0;
        long[] first = samples.peekFirst(), last = samples.peekLast();
        long span = Math.max(1, last[0] - first[0]);
        return Math.max(0, (first[1] - last[1]) / 100.0 / span);
    }

    /** At this rate it is dead within two seconds. */
    public boolean lethalSoon() {
        if (samples.isEmpty()) return false;
        double health = samples.peekLast()[1] / 100.0;
        double loss = lossPerTick();
        return loss > 0 && health - loss * AHEAD <= 0;
    }

    public void clear() {
        samples.clear();
    }
}
