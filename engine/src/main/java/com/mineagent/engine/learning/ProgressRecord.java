package com.mineagent.engine.learning;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The progress record (SMH plan 70, MA10.3; Andy Omega §52.4, §53): per game hour and for the session, goals done and
 * failed, items gained, deaths, close calls, model calls and actions by kind; and a stall alarm when nothing moves for
 * long while it has something to do. Pure: the agent loop tells it what happens; counts only, never content.
 */
public final class ProgressRecord {
    /** A game hour is 1,000 ticks (a day is 24,000). */
    public static final long HOUR = 1_000L;
    /** Nothing gained, done or travelled for this long, with work in hand: stalled (5 minutes). */
    public static final long STALL = 6_000L;

    public static final class Tally {
        public int goalsDone, goalsFailed, deaths, closeCalls, modelCalls;
        public final Map<String, Integer> gained = new TreeMap<>();
        public final Map<String, Integer> actions = new TreeMap<>();

        void add(Tally o) {
            goalsDone += o.goalsDone;
            goalsFailed += o.goalsFailed;
            deaths += o.deaths;
            closeCalls += o.closeCalls;
            modelCalls += o.modelCalls;
            o.gained.forEach((k, v) -> gained.merge(k, v, Integer::sum));
            o.actions.forEach((k, v) -> actions.merge(k, v, Integer::sum));
        }
    }

    private final Map<Long, Tally> hours = new LinkedHashMap<>();
    private long lastMoved = Long.MIN_VALUE;
    private long stallSaid = Long.MIN_VALUE;

    private Tally at(long tick) {
        Tally t = hours.computeIfAbsent(Math.floorDiv(tick, HOUR), h -> new Tally());
        while (hours.size() > 48) hours.remove(hours.keySet().iterator().next()); // two game days kept
        return t;
    }

    public synchronized void goalDone(long tick) {
        at(tick).goalsDone++;
        moved(tick);
    }

    public synchronized void goalFailed(long tick) {
        at(tick).goalsFailed++;
    }

    public synchronized void gained(String item, int count, long tick) {
        if (count <= 0) return;
        at(tick).gained.merge(item, count, Integer::sum);
        moved(tick);
    }

    public synchronized void died(long tick) {
        at(tick).deaths++;
    }

    public synchronized void closeCall(long tick) {
        at(tick).closeCalls++;
    }

    public synchronized void modelCall(long tick) {
        at(tick).modelCalls++;
    }

    public synchronized void action(String kind, long tick) {
        at(tick).actions.merge(kind, 1, Integer::sum);
    }

    /** Something moved: an item gained, a goal done, or a good way travelled. */
    public synchronized void moved(long tick) {
        lastMoved = Math.max(lastMoved, tick);
    }

    /** Busy with work and nothing moved for {@link #STALL}: true once a stall (again only after something moves). */
    public synchronized boolean stalled(boolean busy, long now) {
        if (!busy) {
            lastMoved = now;
            return false;
        }
        if (lastMoved == Long.MIN_VALUE) lastMoved = now;
        if (now - lastMoved < STALL || stallSaid >= lastMoved) return false;
        stallSaid = now;
        return true;
    }

    /** The session's tally (all the hours kept). */
    public synchronized Tally session() {
        Tally all = new Tally();
        for (Tally t : hours.values()) all.add(t);
        return all;
    }

    /** The last game hour's tally. */
    public synchronized Tally hour(long now) {
        Tally t = hours.get(Math.floorDiv(now, HOUR));
        Tally out = new Tally();
        if (t != null) out.add(t);
        return out;
    }

    /** The session in a line, for "progress". */
    public synchronized String summary(long now) {
        Tally s = session();
        Tally h = hour(now);
        List<String> got = new ArrayList<>();
        s.gained.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(5)
                .forEach(e -> got.add(e.getValue() + " " + e.getKey().substring(e.getKey().indexOf(':') + 1).replace('_', ' ')));
        return "This session: " + s.goalsDone + (s.goalsDone == 1 ? " goal" : " goals") + " done, " + s.goalsFailed + " failed; "
                + (got.isEmpty() ? "nothing gained yet" : "gained " + String.join(", ", got)) + "; " + s.deaths
                + (s.deaths == 1 ? " death, " : " deaths, ") + s.closeCalls + (s.closeCalls == 1 ? " close call; " : " close calls; ")
                + s.modelCalls + (s.modelCalls == 1 ? " model call" : " model calls") + ". This hour: " + h.goalsDone + " done, "
                + h.gained.values().stream().mapToInt(Integer::intValue).sum() + " items gained.";
    }
}
