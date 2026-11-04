package com.mineagent.engine.world;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

/**
 * What a sign on a chest says it is for (SMH plan 70, its owner's chest sorting system): "Iron", "Tools & Weapons",
 * "Spruce / Logs", "[Food]". Each line, and each part of a line split at commas, ampersands, slashes, pluses and "and",
 * is a purpose when it is a kind ({@link ChestPurposes#KINDS}) or names a thing; two lines that only make sense together
 * ("Spruce", "Logs") are read as one. Words that say nothing ("chest", "storage", a name) are left out. The text is only
 * matched against kinds and things: it never tells the companion to do anything. Pure.
 */
public final class ChestSigns {
    private ChestSigns() {}

    static final Set<String> FILLER = Set.of("chest", "chests", "storage", "box", "barrel", "stuff for", "keep out", "do not touch",
            "items", "item", "misc items");

    /** The purposes a sign's lines give; {@code isThing} says whether words name a thing (an item, or a kind of item). */
    public static List<String> purposes(List<String> lines, Predicate<String> isThing) {
        List<String> parts = new ArrayList<>();
        for (String line : lines) {
            if (line == null) continue;
            String l = line.toLowerCase(Locale.ROOT).replaceAll("[\\[\\]{}()<>*=~#:!?.\"'`|_-]+", " ").strip();
            if (l.isEmpty()) continue;
            for (String p : l.split("\\s*(?:,|&|/|\\+|;|\\band\\b)\\s*")) {
                String t = p.replaceAll("\\s+", " ").strip();
                if (!t.isEmpty()) parts.add(t);
            }
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (int i = 0; i < parts.size(); i++) {
            String one = meaning(parts.get(i), isThing);
            String two = i + 1 < parts.size() ? meaning(parts.get(i) + " " + parts.get(i + 1), isThing) : null;
            // Two parts that make a thing together ("spruce", "logs"), when the first alone is nothing.
            if (one == null && two != null) {
                out.add(two);
                i++;
                continue;
            }
            if (one != null) out.add(one);
        }
        return List.copyOf(out);
    }

    /**
     * What words on a sign mean as a purpose: a kind by its own name ("food", "stone"), else a thing or a sort of thing
     * as written ("cobblestone" is cobblestone, "logs" every log), else a kind by another word for it ("misc", "gear");
     * null for nothing.
     */
    static String meaning(String words, Predicate<String> isThing) {
        if (FILLER.contains(words)) return null;
        if (ChestPurposes.KINDS.containsKey(words)) return words;
        if (isThing.test(words)) return words;
        String p = ChestPurposes.purpose(words);
        return ChestPurposes.KINDS.containsKey(p) ? p : null;
    }
}
