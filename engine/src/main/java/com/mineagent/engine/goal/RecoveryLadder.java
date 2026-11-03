package com.mineagent.engine.goal;

import java.util.List;
import java.util.Locale;

/**
 * What it does when an order fails (SMH plan 70, MA6.4; Andy Omega §13): the nine rungs, in order, none skipped,
 * each passed over only when it can't apply to this failure. Retry; a variation; another method; fix what's missing;
 * other materials; another strategy; ask you; a plan patch from the model; give up cleanly and say why. The tasks
 * already try other materials and sources themselves (a cow gone, the birch instead of oak), so those rungs are theirs.
 * Pure: the agent loop does what the rung says.
 */
public final class RecoveryLadder {
    private RecoveryLadder() {}

    /** Why an order failed, read from what its task said. */
    public enum Failure { NO_PATH, STALLED, NO_TOOL, NO_SOURCE, NO_CHEST, NO_ROOM, DANGER, UNKNOWN }

    public enum Rung { RETRY, VARIATION, METHOD, FIX, MATERIALS, STRATEGY, ASK, PATCH, GIVE_UP }

    /** Rungs in order. */
    public static final List<Rung> ORDER = List.of(Rung.values());

    /** The failure in a task's words ("I can't get to 3, 4, 5", "the furnaces stopped", "I have no shears"). */
    public static Failure classify(String said) {
        String s = said == null ? "" : said.toLowerCase(Locale.ROOT);
        if (s.contains("can't get to") || s.contains("cannot reach") || s.contains("no path")) return Failure.NO_PATH;
        if (s.contains("stopped before") || s.contains("timed out") || s.contains("out of time")) return Failure.STALLED;
        if (s.contains("pickaxe") || s.contains("shears") || s.contains("worn out") || s.contains("no tool")) return Failure.NO_TOOL;
        if (s.contains("no chest") || s.contains("which chest") || s.contains("chest at") && s.contains("gone")) return Failure.NO_CHEST;
        if (s.contains("is already full") || s.contains("pack is full") || s.contains("no room")) return Failure.NO_ROOM;
        if (s.contains("lava") || s.contains("water on every side") || s.contains("danger")) return Failure.DANGER;
        if (s.contains("can't see a way") || s.contains("know no way") || s.contains("couldn't make any") || s.contains("no more")
                || s.contains("couldn't find")) return Failure.NO_SOURCE;
        return Failure.UNKNOWN;
    }

    /** Whether a rung can do anything for this failure. */
    static boolean applies(Rung r, Failure f, boolean ownerHere, boolean model) {
        return switch (r) {
            // Once more, for what may pass by itself: a path blocked a moment, a furnace that stalled, an odd failure.
            case RETRY -> f == Failure.NO_PATH || f == Failure.STALLED || f == Failure.UNKNOWN;
            // The same order, smaller: what it can make here, if not all of it.
            case VARIATION -> f == Failure.NO_SOURCE || f == Failure.NO_ROOM;
            // Its tasks choose the method and the materials themselves, and have.
            case METHOD, MATERIALS, STRATEGY -> false;
            // A tool it lacks: made first.
            case FIX -> f == Failure.NO_TOOL;
            // You decide, if you're here: which chest, or whether to go on.
            case ASK -> ownerHere;
            // The model, with a narrow brief: what was asked, what was tried, why it failed.
            case PATCH -> model && f != Failure.NO_CHEST && f != Failure.DANGER;
            case GIVE_UP -> true;
        };
    }

    /** The next rung to use after {@code tried} rungs of this goal (0: none yet), for this failure. */
    public static Rung next(Failure f, int tried, boolean ownerHere, boolean model) {
        for (int i = Math.max(0, tried); i < ORDER.size(); i++) {
            Rung r = ORDER.get(i);
            if (applies(r, f, ownerHere, model)) return r;
        }
        return Rung.GIVE_UP;
    }

    /** The index after a rung: where the next failure of the same goal starts looking. */
    public static int after(Rung r) {
        return ORDER.indexOf(r) + 1;
    }
}
