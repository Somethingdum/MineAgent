package com.mineagent.engine.villager;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a villager is worth keeping, by its offers (SMH plan 70, its owner: "trading MASTERY is required ... because OP
 * is OP"): a librarian selling Mending above all, then books of the best enchantments at their top level, a master's
 * enchanted diamond gear, a cleric's ender pearls and experience bottles, a farmer's golden carrots. On the game's 1.21.1
 * rules (no trade rebalance): any librarian can sell Mending, a never-traded librarian's offers are new each time it
 * takes up its lectern again, and a villager levels at 10, 70, 150 and 250 experience. Pure.
 */
public final class VillagerWorth {
    private VillagerWorth() {}

    /** Experience at which a villager reaches each level, novice (1) to master (5). */
    public static final int[] LEVEL_XP = {0, 10, 70, 150, 250};

    /** One offer as it reads: what it sells, a book's enchantment and level (else null and 0), the emeralds it costs. */
    public record Offer(String result, String enchantment, int level, int maxLevel, int price) {}

    /** The enchantments worth a book at their top level, for gear that lasts. */
    public static final Set<String> GREAT = Set.of("minecraft:unbreaking", "minecraft:protection", "minecraft:efficiency",
            "minecraft:sharpness", "minecraft:fortune", "minecraft:looting", "minecraft:silk_touch", "minecraft:power",
            "minecraft:infinity", "minecraft:feather_falling", "minecraft:depth_strider", "minecraft:respiration",
            "minecraft:aqua_affinity", "minecraft:sweeping_edge", "minecraft:fire_aspect", "minecraft:flame", "minecraft:punch",
            "minecraft:luck_of_the_sea", "minecraft:lure", "minecraft:thorns", "minecraft:frost_walker", "minecraft:smite",
            "minecraft:quick_charge", "minecraft:multishot", "minecraft:piercing", "minecraft:loyalty", "minecraft:riptide",
            "minecraft:impaling", "minecraft:density", "minecraft:breach", "minecraft:wind_burst", "minecraft:blast_protection",
            "minecraft:fire_protection", "minecraft:projectile_protection");

    /** Things worth a keeper on their own: pearls and experience to buy, golden carrots, a master's gear. */
    static final Map<String, Integer> GOODS = Map.ofEntries(
            Map.entry("minecraft:ender_pearl", 50), Map.entry("minecraft:experience_bottle", 50),
            Map.entry("minecraft:golden_carrot", 30), Map.entry("minecraft:glistering_melon_slice", 15),
            Map.entry("minecraft:diamond_chestplate", 35), Map.entry("minecraft:diamond_leggings", 30),
            Map.entry("minecraft:diamond_helmet", 25), Map.entry("minecraft:diamond_boots", 25),
            Map.entry("minecraft:diamond_sword", 25), Map.entry("minecraft:diamond_axe", 25),
            Map.entry("minecraft:diamond_pickaxe", 30), Map.entry("minecraft:diamond_shovel", 15),
            Map.entry("minecraft:name_tag", 10), Map.entry("minecraft:bell", 10), Map.entry("minecraft:arrow", 10));

    /** One offer's worth: 100 for Mending; a great enchantment at its top level 60, a level below 25; goods by the table. */
    public static int worth(Offer o) {
        if (o == null || o.result() == null) return 0;
        if ("minecraft:enchanted_book".equals(o.result()) && o.enchantment() != null) {
            if ("minecraft:mending".equals(o.enchantment())) return 100;
            if (!GREAT.contains(o.enchantment())) return 5;
            if (o.level() >= o.maxLevel()) return 60;
            return o.level() == o.maxLevel() - 1 ? 25 : 5;
        }
        return GOODS.getOrDefault(o.result(), 0);
    }

    /** A villager's worth: its best offer, and a little for each other good one. */
    public static int worth(List<Offer> offers) {
        int best = 0, rest = 0;
        for (Offer o : offers) {
            int w = worth(o);
            if (w > best) {
                rest += best / 4;
                best = w;
            } else {
                rest += w / 4;
            }
        }
        return best + rest;
    }

    /** Worth keeping in a cell and trading to master: 60 or more. */
    public static final int KEEP = 60;

    public static boolean keeper(List<Offer> offers) {
        return worth(offers) >= KEEP;
    }

    /** Whether any offer is the enchantment wanted ("minecraft:mending"), at its top level unless one level is all it has. */
    public static boolean sells(List<Offer> offers, String enchantment) {
        for (Offer o : offers) {
            if ("minecraft:enchanted_book".equals(o.result()) && enchantment.equals(o.enchantment()) && o.level() >= o.maxLevel()) return true;
        }
        return false;
    }

    /** The level (1 novice to 5 master) for so much experience. */
    public static int level(int xp) {
        int l = 1;
        for (int i = 1; i < LEVEL_XP.length; i++) if (xp >= LEVEL_XP[i]) l = i + 1;
        return l;
    }

    /** Experience still to go to master. */
    public static int toMaster(int xp) {
        return Math.max(0, LEVEL_XP[LEVEL_XP.length - 1] - xp);
    }
}
