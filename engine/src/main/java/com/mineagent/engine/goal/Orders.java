package com.mineagent.engine.goal;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Orders in a row and orders that stand (SMH plan 70, MA6.3; the owner: "If I tell it to do one long task, then when
 * its done, go do another task until further notice, can it handle that?"). "Fill this chest with stone bricks, then
 * mine diamonds until I say stop" is two goals in that order, the second standing: done again and again until it is
 * stopped. Pure: the agent loop puts them in its goal book, and runs each one code knows by code.
 */
public final class Orders {
    private Orders() {}

    /** The words between two orders: "then", "and then", "after that", "once that's done", "when you're done". */
    private static final Pattern THEN = Pattern.compile(
            "\\s*(?:[,;.]\\s*)?(?:\\band then\\b|\\bthen\\b|\\bafter that\\b|\\bafterwards\\b|\\bonce (?:that'?s|that is|you'?re) done\\b"
                    + "|\\bwhen (?:that'?s|that is|you'?re) done\\b|\\bnext\\b(?=\\s*,))\\s*,?\\s*", Pattern.CASE_INSENSITIVE);
    /** An order that stands until it is stopped. */
    private static final Pattern STANDING = Pattern.compile(
            "\\s*,?\\s*(?:until (?:i (?:say|tell you(?: to)?) stop|further notice|i say otherwise)|till i say stop|forever|"
                    + "over and over|again and again|on repeat|nonstop|non-stop)\\s*$", Pattern.CASE_INSENSITIVE);
    /** The mark a standing goal's words carry in the goal book. */
    public static final String STANDS = " (until you say stop)";

    /** One order of a row, and whether it stands. */
    public record Order(String text, boolean standing) {
        /** As the goal book keeps it. */
        public String goalText() {
            return standing ? text + STANDS : text;
        }
    }

    /**
     * A line of orders in their order, or an empty list when it is one order (or none). "first" at the start is
     * dropped; each part keeps its own "until I say stop".
     */
    public static List<Order> split(String line) {
        List<Order> out = new ArrayList<>();
        if (line == null) return out;
        String t = line.strip().replaceFirst("(?i)^(?:first(?:ly)?|to start(?: with)?)\\s*,?\\s+", "");
        String[] parts = THEN.split(t);
        if (parts.length < 2) return out;
        for (String p : parts) {
            String part = p.strip().replaceFirst("^(?:and|also)\\s+", "").replaceAll("[,;.]+$", "").strip();
            if (part.isEmpty()) continue;
            out.add(order(part));
        }
        return out.size() >= 2 ? out : new ArrayList<>();
    }

    /** One order, with "until I say stop" and the like taken off and kept as its standing. */
    public static Order order(String text) {
        String t = text == null ? "" : text.strip();
        var m = STANDING.matcher(t);
        if (m.find()) return new Order(t.substring(0, m.start()).strip(), true);
        return new Order(t, false);
    }

    /** A goal's words, without the standing mark; and whether it has it. */
    public static boolean stands(String goalText) {
        return goalText != null && goalText.endsWith(STANDS);
    }

    public static String withoutMark(String goalText) {
        return stands(goalText) ? goalText.substring(0, goalText.length() - STANDS.length()) : goalText;
    }
}
