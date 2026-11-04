package com.mineagent.engine.goal;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A job of its own that failed is tried again later, and later each time it fails again (SMH plan 70, its owner: "I will
 * run this near constantly ... it getting stuck in some dumb loop"): a minute after the first failure, then two, four,
 * eight, up to a game day; a success forgets the failures. So a stockpile item it can't get just now, or a piece of gear
 * whose ore isn't reachable, doesn't take every free minute for weeks, and the rest of what it does goes on. At most 256
 * jobs remembered, the oldest forgotten first. Pure.
 */
public final class Backoff {
    /** The first wait, and the longest: a minute and a game day, in ticks. */
    public static final long FIRST = 1_200, MOST = 24_000;
    static final int MOST_KEYS = 256;

    private record Failures(int count, long until) {}

    private final LinkedHashMap<String, Failures> byKey = new LinkedHashMap<>();

    /** Whether the job may be tried now. */
    public synchronized boolean ready(String key, long now) {
        Failures f = key == null ? null : byKey.get(key);
        return f == null || now >= f.until();
    }

    /** It failed now: the wait before the next try doubles. */
    public synchronized void failed(String key, long now) {
        if (key == null) return;
        Failures was = byKey.remove(key);
        int n = was == null ? 1 : Math.min(30, was.count() + 1);
        long wait = Math.min(MOST, FIRST << Math.min(20, n - 1));
        byKey.put(key, new Failures(n, now + wait));
        while (byKey.size() > MOST_KEYS) {
            var oldest = byKey.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
    }

    /** It worked: its failures forgotten. */
    public synchronized void succeeded(String key) {
        if (key != null) byKey.remove(key);
    }

    /** Failures in a row for this job (0: none). */
    public synchronized int failures(String key) {
        Failures f = key == null ? null : byKey.get(key);
        return f == null ? 0 : f.count();
    }

    /** When it may be tried again (0 if now). */
    public synchronized long until(String key) {
        Failures f = key == null ? null : byKey.get(key);
        return f == null ? 0 : f.until();
    }

    public synchronized Map<String, Integer> snapshot() {
        Map<String, Integer> out = new LinkedHashMap<>();
        byKey.forEach((k, f) -> out.put(k, f.count()));
        return out;
    }
}
