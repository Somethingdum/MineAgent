package com.mineagent.engine.chat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A running summary of its talks with its owner in this world (SMH plan 70, MA10.2): what they ask for most, what they
 * asked for lately, and the words of theirs it has learned, kept with the world and given to the model with its brief,
 * so a long friendship needs no repeating. Built in code from the orders it was given: no model is asked to write it
 * (one prompt per PC), and nothing of anyone's but this world's own talk goes in. Pure. Switch:
 * {@code behaviour.talkSummary}.
 */
public final class TalkSummary {
    /** At most this many things asked for, the least asked dropped first. */
    public static final int TOPICS = 48;
    /** The orders kept as "lately". */
    public static final int LATELY = 6;

    /** What is kept: times asked by topic, and the latest orders, oldest first. */
    public record State(Map<String, Integer> asked, List<String> lately) {}

    private final LinkedHashMap<String, Integer> asked = new LinkedHashMap<>();
    private final ArrayDeque<String> lately = new ArrayDeque<>();

    /** An order its owner gave ("make 2 iron helmets"): counted under its topic, and kept as the latest. */
    public synchronized void order(String words) {
        String t = topic(words);
        if (t.isEmpty()) return;
        asked.merge(t, 1, Integer::sum);
        while (asked.size() > TOPICS) {
            String least = null;
            for (var e : asked.entrySet()) if (least == null || e.getValue() < asked.get(least)) least = e.getKey();
            asked.remove(least);
        }
        String w = words.strip();
        if (w.length() > 80) w = w.substring(0, 80) + "…";
        lately.remove(w);
        lately.addLast(w);
        while (lately.size() > LATELY) lately.removeFirst();
    }

    /**
     * One line for the model's brief, or "" when there has been no talk yet: "Our talks in this world: you ask most for
     * iron ingots (5), food (3); lately: make me 8 food, build a hut; your words: "chop" means "get me logs"."
     */
    public synchronized String summary(Map<String, String> phrases) {
        List<String> parts = new ArrayList<>();
        List<Map.Entry<String, Integer>> top = new ArrayList<>(asked.entrySet());
        top.removeIf(e -> e.getValue() < 2);
        top.sort((a, b) -> b.getValue() - a.getValue());
        if (!top.isEmpty()) {
            List<String> most = new ArrayList<>();
            for (var e : top.subList(0, Math.min(4, top.size()))) most.add(e.getKey() + " (" + e.getValue() + ")");
            parts.add("you ask most for " + String.join(", ", most));
        }
        if (!lately.isEmpty()) {
            List<String> l = new ArrayList<>(lately);
            parts.add("lately: " + String.join("; ", l.subList(Math.max(0, l.size() - 3), l.size())));
        }
        if (phrases != null && !phrases.isEmpty()) {
            List<String> p = new ArrayList<>();
            for (var e : phrases.entrySet()) {
                if (p.size() >= 4) break;
                p.add("\"" + e.getKey() + "\" means \"" + e.getValue() + "\"");
            }
            parts.add("your words: " + String.join(", ", p));
        }
        return parts.isEmpty() ? "" : "Our talks in this world: " + String.join("; ", parts) + ".";
    }

    /** What an order is about, its numbers and the words round them left out: "make me 12 iron ingots" → "iron ingots". */
    static String topic(String words) {
        if (words == null) return "";
        String t = words.toLowerCase(Locale.ROOT).strip();
        int then = t.indexOf(", then ");
        if (then > 0) t = t.substring(0, then); // a queue: its first order stands for it
        t = t.replaceAll("[0-9,]+", " ")
                .replaceAll("^(please |can you |could you )?(go )?(make|get|fetch|gather|collect|bring|craft|smelt|cook|mine|give)( me)?\\b", " ")
                .replaceAll("\\b(a chest full of|a chest of|some|a few|an|a)\\b", " ")
                .replaceAll("\\s+", " ").strip();
        if (t.length() > 40) t = t.substring(0, 40).strip();
        return t;
    }

    public synchronized State exportState() {
        return new State(new LinkedHashMap<>(asked), new ArrayList<>(lately));
    }

    public synchronized void importState(State s) {
        asked.clear();
        lately.clear();
        if (s == null) return;
        if (s.asked() != null) s.asked().forEach((k, v) -> {
            if (k != null && v != null && v > 0 && asked.size() < TOPICS) asked.put(k, v);
        });
        if (s.lately() != null) for (String w : s.lately()) if (w != null && !w.isBlank() && lately.size() < LATELY) lately.addLast(w);
    }
}
