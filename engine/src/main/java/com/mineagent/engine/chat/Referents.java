package com.mineagent.engine.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * What "that", "it", "there" mean (SMH plan 70, MA10.2; Andy Omega §18): a short list of candidates made in code from
 * what its owner is looking at and what is near them, given to the model, which may pick only from it. The first call
 * that names a place, after words like these, must be at one of the candidates; one that isn't is refused with the
 * list. Pure.
 */
public final class Referents {
    private Referents() {}

    /** One thing they may mean: what it is, and where. */
    public record Candidate(String label, String dim, int x, int y, int z) {}

    private static final Pattern DEICTIC = Pattern.compile("\\b(?:that|it|there|this|those|these|here)\\b", Pattern.CASE_INSENSITIVE);

    /** Whether the words point at something ("put it in that", "go there"). */
    public static boolean deictic(String text) {
        return text != null && DEICTIC.matcher(text).find();
    }

    /** The candidates for the model, numbered. */
    public static String brief(List<Candidate> list) {
        if (list == null || list.isEmpty()) return "";
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            Candidate c = list.get(i);
            parts.add("[" + (i + 1) + "] " + c.label() + " at " + c.x() + ", " + c.y() + ", " + c.z());
        }
        return "[REFERENTS] When they say that, it, this or there, they mean one of these; pick only from them, by its "
                + "coordinates: " + String.join("; ", parts) + ".";
    }

    /** The candidate within {@code within} blocks of x, y, z, if any. */
    public static Optional<Candidate> match(List<Candidate> list, int x, int y, int z, double within) {
        if (list == null) return Optional.empty();
        Candidate best = null;
        double bestD = within * within;
        for (Candidate c : list) {
            double dx = c.x() - x, dy = c.y() - y, dz = c.z() - z;
            double d = dx * dx + dy * dy + dz * dz;
            if (d <= bestD) {
                bestD = d;
                best = c;
            }
        }
        return Optional.ofNullable(best);
    }

    /** The refusal when a call names a place that is none of them. */
    public static String refusal(List<Candidate> list) {
        return "That, it or there means one of these, not somewhere else: " + brief(list).replace("[REFERENTS] ", "");
    }
}
