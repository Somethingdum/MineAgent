package com.mineagent.engine.gear;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Getting ready for a boss (SMH plan 70, its owner: "able to swiftly get prepared for boss fights"; never fighting one
 * without its owner unless told to in so many words): what to bring, as players who beat them bring it. The dragon: the
 * best armour and sword, a bow with a stack and more of arrows (the crystals on the pillars), golden apples, golden
 * carrots, ender pearls, blocks to pillar with, a water bucket, potions of slow falling and healing. The wither: milk
 * (it takes the wither off), golden apples, healing and strength, a bow and arrows, blocks. Pure.
 */
public final class BossKit {
    private BossKit() {}

    /** An item and how many to have. */
    public record Want(String item, int count) {}

    /** Potions to brew for a boss, by the brewing order's words. */
    public record Brew(String potion, int count) {}

    static final Map<String, List<Want>> ITEMS = Map.of(
            "dragon", List.of(new Want("minecraft:bow", 1), new Want("minecraft:arrow", 192), new Want("minecraft:golden_apple", 8),
                    new Want("minecraft:golden_carrot", 64), new Want("minecraft:ender_pearl", 16), new Want("minecraft:cobblestone", 128),
                    new Want("minecraft:water_bucket", 1), new Want("minecraft:shield", 1)),
            "wither", List.of(new Want("minecraft:bow", 1), new Want("minecraft:arrow", 128), new Want("minecraft:golden_apple", 8),
                    new Want("minecraft:golden_carrot", 32), new Want("minecraft:milk_bucket", 3), new Want("minecraft:cobblestone", 64),
                    new Want("minecraft:shield", 1)));

    static final Map<String, List<Brew>> BREWS = Map.of(
            "dragon", List.of(new Brew("slow falling", 2), new Brew("healing", 3)),
            "wither", List.of(new Brew("healing", 4), new Brew("strength", 2), new Brew("regeneration", 2)));

    /** The boss a person means ("the ender dragon", "wither"), or null. */
    public static String boss(String words) {
        if (words == null) return null;
        String w = words.toLowerCase(java.util.Locale.ROOT);
        if (w.contains("dragon") || w.contains("the end")) return "dragon";
        if (w.contains("wither") && !w.contains("skeleton")) return "wither";
        return null;
    }

    /** What is short of the kit for a boss, given how many of each it has. */
    public static List<Want> short_(String boss, Map<String, Integer> has) {
        List<Want> out = new ArrayList<>();
        for (Want w : ITEMS.getOrDefault(boss, List.of())) {
            int n = w.count() - has.getOrDefault(w.item(), 0);
            if (n > 0) out.add(new Want(w.item(), n));
        }
        return out;
    }

    public static List<Brew> brews(String boss) {
        return BREWS.getOrDefault(boss, List.of());
    }
}
