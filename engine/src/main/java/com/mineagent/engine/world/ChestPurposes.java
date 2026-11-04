package com.mineagent.engine.world;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Chests with a purpose (SMH plan 70, MA9.2): "this chest is for tools", "this is the food chest". Its owner gives a
 * chest a purpose; what it puts away goes by purpose, and what has no chest of its own goes to an overflow chest, at
 * most two. A purpose is a kind ("tools", "food", "ores", "wood", "stone", "crops", "mob drops", "blocks", "overflow")
 * or the name of a thing ("spruce logs"). Kept with the world. Pure.
 */
public final class ChestPurposes {
    /** At most this many chests with a purpose. */
    public static final int MAX = 128;

    /** The kinds a purpose may name, each with the words for it. */
    public static final Map<String, Set<String>> KINDS = Map.ofEntries(
            Map.entry("tools", Set.of("tools", "tool", "gear", "weapons", "weapon", "armour", "armor")),
            Map.entry("food", Set.of("food", "foods", "meat", "snacks")),
            Map.entry("ores", Set.of("ores", "ore", "ingots", "minerals", "gems", "valuables", "metal", "metals")),
            Map.entry("wood", Set.of("wood", "logs", "planks", "lumber", "timber")),
            Map.entry("stone", Set.of("stone", "stones", "cobble", "cobblestone", "rock", "rocks")),
            Map.entry("crops", Set.of("crops", "crop", "farm", "farming", "seeds", "harvest", "plants")),
            Map.entry("mob drops", Set.of("mob drops", "drops", "loot", "mob loot")),
            Map.entry("blocks", Set.of("blocks", "building blocks", "building", "building stuff")),
            Map.entry("overflow", Set.of("overflow", "anything", "everything", "anything else", "spare", "junk", "misc", "stuff", "the rest")));

    /** What is kept: by chest ("dim|x|y|z"), its purpose. */
    public record State(Map<String, String> purposes) {}

    private final LinkedHashMap<String, String> purposes = new LinkedHashMap<>();

    public static String key(String dim, int x, int y, int z) {
        return dim + "|" + x + "|" + y + "|" + z;
    }

    /** "for tools", "the food chest", "spruce logs": the purpose as it is kept ("tools", "food", "spruce logs"). */
    public static String purpose(String words) {
        String w = words == null ? "" : words.toLowerCase(Locale.ROOT).strip()
                .replaceAll("^(?:for |the |my |our |a |an )+", "").replaceAll(" (?:chest|chests|barrel|box)$", "").strip();
        for (var e : KINDS.entrySet()) if (e.getValue().contains(w) || e.getKey().equals(w)) return e.getKey();
        return w;
    }

    public synchronized void set(String dim, int x, int y, int z, String purpose) {
        String k = key(dim, x, y, z);
        purposes.remove(k);
        purposes.put(k, purpose(purpose));
        while (purposes.size() > MAX) purposes.remove(purposes.keySet().iterator().next());
    }

    public synchronized boolean clear(String dim, int x, int y, int z) {
        return purposes.remove(key(dim, x, y, z)) != null;
    }

    public synchronized Optional<String> of(String dim, int x, int y, int z) {
        return Optional.ofNullable(purposes.get(key(dim, x, y, z)));
    }

    public synchronized Map<String, String> all() {
        return Map.copyOf(purposes);
    }

    /**
     * Whether an item fits a purpose: a kind by the kinds the item is ({@code kinds}, from the game: "tools", "food"...),
     * a thing by its name ("spruce logs" takes spruce logs). "overflow" takes anything.
     */
    public static boolean fits(String purpose, String itemId, Set<String> kinds) {
        if (purpose == null || purpose.isBlank()) return false;
        if ("overflow".equals(purpose)) return true;
        if (KINDS.containsKey(purpose)) return kinds.contains(purpose);
        String path = itemId.substring(itemId.indexOf(':') + 1).replace('_', ' ');
        String p = purpose.replaceAll("ies$", "y").replaceAll("(?<=[a-z])s$", "");
        return path.equals(purpose) || path.equals(p) || path.replaceAll("(?<=[a-z])s$", "").equals(p);
    }

    public synchronized State exportState() {
        return new State(new LinkedHashMap<>(purposes));
    }

    public synchronized void importState(State s) {
        purposes.clear();
        if (s == null || s.purposes() == null) return;
        s.purposes().forEach((k, v) -> {
            if (k != null && v != null && k.split("\\|").length == 4 && !v.isBlank() && purposes.size() < MAX) purposes.put(k, purpose(v));
        });
    }
}
