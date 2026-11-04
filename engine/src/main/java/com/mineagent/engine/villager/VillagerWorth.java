package com.mineagent.engine.villager;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

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

    // ── Training a keeper to master (its owner: "max out their level over the course of many trades") ──

    /**
     * A trade a villager has on offer now: its place in the list, what it costs (one item kind; two-item trades are not
     * used to train), what it gives, the experience the villager gets for it, and the uses left before it restocks.
     */
    public record Deal(int index, String cost, int costCount, String result, int resultCount, int xp, int usesLeft) {}

    /** Emeralds kept back from training, for the books and gear it trades for. */
    public static final int EMERALD_RESERVE = 16;

    /** Trades in one round of training, at most (the villager restocks at its job site twice a day). */
    public static final int ROUND = 12;

    /** Never paid to train a villager: worth more than the levels. */
    public static final Set<String> NEVER_PAY = Set.of("minecraft:diamond", "minecraft:netherite_ingot", "minecraft:netherite_scrap",
            "minecraft:golden_apple", "minecraft:enchanted_golden_apple", "minecraft:totem_of_undying", "minecraft:nether_star",
            "minecraft:elytra", "minecraft:enchanted_book", "minecraft:diamond_block", "minecraft:emerald_block");

    /**
     * The trade to train a villager with, or null for none: one it has uses left of that gives it experience, paid with
     * something it carries or can make (never one of {@link #NEVER_PAY}). Its goods sold for emeralds first (they earn
     * the emeralds the books cost, from cheap things: paper, sticks, coal, wheat), what it already carries before what it
     * must make, the most experience a trade first; emeralds spent only above {@link #EMERALD_RESERVE}.
     */
    public static Deal toTrain(List<Deal> deals, Map<String, Integer> has, Predicate<String> canMake) {
        Deal best = null;
        int bestScore = Integer.MIN_VALUE;
        int emeralds = has.getOrDefault("minecraft:emerald", 0);
        for (Deal d : deals) {
            if (d == null || d.usesLeft() <= 0 || d.xp() <= 0 || d.cost() == null || d.costCount() <= 0 || NEVER_PAY.contains(d.cost())) continue;
            boolean carried = has.getOrDefault(d.cost(), 0) >= d.costCount();
            int score;
            if ("minecraft:emerald".equals(d.result()) && !"minecraft:emerald".equals(d.cost())) {
                if (!carried && !canMake.test(d.cost())) continue;
                score = 1_000 + d.xp() * 20 + (carried ? 300 : 0) - d.costCount();
            } else if ("minecraft:emerald".equals(d.cost())) {
                if (emeralds - d.costCount() < EMERALD_RESERVE) continue;
                score = d.xp() * 20 - d.costCount() * 5;
            } else {
                if (!carried) continue; // a swap of one thing for another: only with what it has
                score = d.xp() * 20 - 400;
            }
            if (score > bestScore) {
                bestScore = score;
                best = d;
            }
        }
        return best;
    }

    /**
     * How many of that trade this round: no more than it has uses left, than take it to master, than {@link #ROUND}; paid
     * in emeralds, no more than leave the reserve; with what it carries, no more than that pays for (what it must make is
     * made for the whole round).
     */
    public static int trainTimes(Deal d, Map<String, Integer> has, int xpToMaster) {
        if (d == null || xpToMaster <= 0) return 0;
        int n = Math.min(Math.min(d.usesLeft(), ROUND), (xpToMaster + d.xp() - 1) / Math.max(1, d.xp()));
        int carried = has.getOrDefault(d.cost(), 0);
        if ("minecraft:emerald".equals(d.cost())) n = Math.min(n, Math.max(0, (carried - EMERALD_RESERVE) / d.costCount()));
        else if (!"minecraft:emerald".equals(d.result()) || carried >= d.costCount()) n = Math.min(n, carried / d.costCount());
        return Math.max(0, n);
    }
}
