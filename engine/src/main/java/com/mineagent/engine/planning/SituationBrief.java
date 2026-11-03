package com.mineagent.engine.planning;

import java.util.ArrayList;
import java.util.List;

/**
 * A short brief built in code for every act, plan and owner-facing talk call (SMH plan 69, MA4.2): what it is doing,
 * the long goal and where it stands, hostiles within 16 blocks, and the last lessons that match. Never longer than
 * {@link #MAX} characters: hostiles keep their room first, lessons give theirs up first.
 */
public final class SituationBrief {
    public static final int MAX = 600;
    public static final int MAX_HOSTILES = 6;

    public record Hostile(String type, int distance) {}

    private SituationBrief() {}

    public static String build(String doing, String goal, List<Hostile> hostiles, List<String> lessons) {
        StringBuilder b = new StringBuilder("[SITUATION] Doing: ").append(clip(blank(doing, "nothing"), 110)).append('.');
        if (goal != null && !goal.isBlank()) b.append(" Goal: ").append(clip(goal, 170)).append('.');
        List<String> near = new ArrayList<>();
        for (Hostile h : hostiles == null ? List.<Hostile>of() : hostiles) {
            if (near.size() >= MAX_HOSTILES) break;
            near.add(clip(h.type().replace('_', ' '), 20) + " " + h.distance());
        }
        b.append(near.isEmpty() ? " No hostiles within 16 blocks." : " Hostiles (blocks away): " + String.join(", ", near) + ".");
        List<String> kept = new ArrayList<>();
        int room = MAX - b.length() - " Lessons: .".length();
        for (String l : lessons == null ? List.<String>of() : lessons) {
            if (l == null || l.isBlank()) continue;
            String one = clip(l, 160);
            int cost = one.length() + (kept.isEmpty() ? 0 : 2);
            if (cost > room) {
                if (room > 40) kept.add(clip(one, room - (kept.isEmpty() ? 0 : 2)));
                break;
            }
            kept.add(one);
            room -= cost;
        }
        if (!kept.isEmpty()) b.append(" Lessons: ").append(String.join("; ", kept)).append('.');
        return b.length() > MAX ? b.substring(0, MAX) : b.toString();
    }

    private static String blank(String s, String otherwise) {
        return s == null || s.isBlank() ? otherwise : s;
    }

    private static String clip(String s, int max) {
        String t = s == null ? "" : s.replaceAll("[\\r\\n]+", " ").strip();
        if (max <= 1) return "";
        return t.length() > max ? t.substring(0, max - 1) + "…" : t;
    }
}
