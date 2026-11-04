package com.mineagent.engine.chat;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Phrasing it learns (SMH plan 70, MA10.2; Andy Omega §20, §22): when its owner says something code doesn't
 * understand and then, soon after, says it in words it does, that is one reading; three readings the same, and the
 * first words mean the second from then on. Its owner can also say so ("when I say X, I mean Y"), and a correction
 * ("no, that's not what I meant") right after a fixed phrase is used removes it. A phrase is only ever read for its
 * owner's own words, and only into an order they could give anyway: it never gains a permission this way. Pure.
 */
public final class PhraseBook {
    /** Readings the same that make a fixed phrase. */
    public static final int TIMES = 3;
    /** At most this many fixed phrases. */
    public static final int MAX = 64;

    private record Reading(String meaning, int times) {}

    private final Map<String, Reading> readings = new HashMap<>();
    private final Map<String, String> fixed = new LinkedHashMap<>();

    static String norm(String s) {
        return s == null ? "" : s.strip().toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " ");
    }

    /** What these words mean, when they are a fixed phrase. */
    public synchronized Optional<String> meaning(String words) {
        return Optional.ofNullable(fixed.get(norm(words)));
    }

    /**
     * Its owner said {@code words}, not understood, then {@code meaning}, understood. True when that made the phrase
     * fixed just now.
     */
    public synchronized boolean rephrased(String words, String meaning) {
        String w = norm(words), m = norm(meaning);
        if (w.isEmpty() || m.isEmpty() || w.equals(m) || fixed.containsKey(w)) return false;
        Reading r = readings.get(w);
        int times = r != null && r.meaning().equals(m) ? r.times() + 1 : 1;
        if (times < TIMES) {
            readings.put(w, new Reading(m, times));
            return false;
        }
        readings.remove(w);
        put(w, m);
        return true;
    }

    /** "When I say X, I mean Y": fixed at once. */
    public synchronized void teach(String words, String meaning) {
        String w = norm(words), m = norm(meaning);
        if (!w.isEmpty() && !m.isEmpty() && !w.equals(m)) put(w, m);
    }

    /** A correction: the phrase is no longer read that way. Whether it was a fixed phrase. */
    public synchronized boolean correct(String words) {
        String w = norm(words);
        readings.remove(w);
        return fixed.remove(w) != null;
    }

    public synchronized Map<String, String> fixedPhrases() {
        return Map.copyOf(fixed);
    }

    private void put(String w, String m) {
        fixed.remove(w);
        fixed.put(w, m);
        while (fixed.size() > MAX) fixed.remove(fixed.keySet().iterator().next());
    }
}
