package com.mineagent.engine.gear;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What players took from its chests and haven't given back (SMH plan 70, its owner: "If something is stolen, the agent
 * should confront the other player and demand it's items back. Or, optionally. Kill that player on sight next time they
 * see them."): per player, item to how many. A taking adds to it; what they give back (handed over, or put back in its
 * chest) comes off it, only what they owe; with nothing left owed they are square. At most 32 players, the oldest debt
 * forgotten first. Pure.
 */
public final class Debts {
    /** One player's debt: their name as last seen, what they owe, when they first took, and whether it ends in a fight. */
    public record Debt(String name, Map<String, Integer> owed, long since, boolean revenge) {}

    public static final int MOST_PLAYERS = 32, MOST_KINDS = 64;

    private final LinkedHashMap<String, Debt> byPlayer = new LinkedHashMap<>();

    /** They took these ({@code revenge}: the score is settled by a fight, not by giving back). */
    public synchronized void took(String player, String name, Map<String, Integer> taken, long tick, boolean revenge) {
        if (player == null || taken == null || taken.isEmpty()) return;
        Debt was = byPlayer.remove(player);
        Map<String, Integer> owed = new LinkedHashMap<>(was == null ? Map.of() : was.owed());
        for (var e : taken.entrySet()) {
            if (e.getValue() == null || e.getValue() <= 0) continue;
            if (owed.size() >= MOST_KINDS && !owed.containsKey(e.getKey())) continue;
            owed.merge(e.getKey(), e.getValue(), Integer::sum);
        }
        if (owed.isEmpty()) return;
        byPlayer.put(player, new Debt(name == null ? (was == null ? "someone" : was.name()) : name, owed,
                was == null ? tick : was.since(), revenge || was != null && was.revenge()));
        while (byPlayer.size() > MOST_PLAYERS) {
            var oldest = byPlayer.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
    }

    /**
     * They gave these back: what they owed of them comes off. Returns what was taken off (empty: none of it was owed);
     * {@link #of} says what is left.
     */
    public synchronized Map<String, Integer> gave(String player, Map<String, Integer> given) {
        Debt d = player == null ? null : byPlayer.get(player);
        if (d == null || given == null) return Map.of();
        Map<String, Integer> owed = new LinkedHashMap<>(d.owed());
        Map<String, Integer> off = new LinkedHashMap<>();
        for (var e : given.entrySet()) {
            int o = owed.getOrDefault(e.getKey(), 0), g = e.getValue() == null ? 0 : e.getValue();
            int n = Math.min(o, g);
            if (n <= 0) continue;
            off.put(e.getKey(), n);
            if (o - n > 0) owed.put(e.getKey(), o - n);
            else owed.remove(e.getKey());
        }
        if (owed.isEmpty()) byPlayer.remove(player);
        else byPlayer.put(player, new Debt(d.name(), owed, d.since(), d.revenge()));
        return off;
    }

    public synchronized Optional<Debt> of(String player) {
        return Optional.ofNullable(player == null ? null : byPlayer.get(player));
    }

    /** Square: forgiven by its owner, or settled by a fight. */
    public synchronized void forgive(String player) {
        if (player != null) byPlayer.remove(player);
    }

    /** The player whose debt goes by this name (as last seen), or null. */
    public synchronized String byName(String name) {
        if (name == null) return null;
        for (var e : byPlayer.entrySet()) if (e.getValue().name().equalsIgnoreCase(name.strip())) return e.getKey();
        return null;
    }

    public synchronized Map<String, Debt> all() {
        return Map.copyOf(byPlayer);
    }

    public synchronized boolean isEmpty() {
        return byPlayer.isEmpty();
    }

    /** Back from the save. */
    public synchronized void load(Map<String, Debt> saved) {
        byPlayer.clear();
        if (saved == null) return;
        for (var e : saved.entrySet()) {
            Debt d = e.getValue();
            if (e.getKey() == null || d == null || d.owed() == null || d.owed().isEmpty()) continue;
            byPlayer.put(e.getKey(), new Debt(d.name() == null ? "someone" : d.name(), new LinkedHashMap<>(d.owed()), d.since(), d.revenge()));
            if (byPlayer.size() >= MOST_PLAYERS) break;
        }
    }

    /** "12 diamonds, 3 iron ingots and 1 bow", most first. */
    public static String describe(Map<String, Integer> items) {
        if (items == null || items.isEmpty()) return "nothing";
        List<Map.Entry<String, Integer>> list = new ArrayList<>(items.entrySet());
        list.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        List<String> parts = new ArrayList<>();
        for (var e : list) {
            String name = e.getKey().substring(e.getKey().indexOf(':') + 1).replace('_', ' ');
            int n = e.getValue();
            parts.add(n + " " + (n == 1 || name.endsWith("s") ? name : name + "s"));
        }
        if (parts.size() == 1) return parts.get(0);
        return String.join(", ", parts.subList(0, parts.size() - 1)) + " and " + parts.get(parts.size() - 1);
    }
}
