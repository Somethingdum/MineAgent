package com.mineagent.engine.gear;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * God-tier gear (SMH plan 70, its owner: "gear that makes a Minecraft sweat blush ... perfectly optimized enchants, the
 * works"): what each piece wears at its best, and the anvil order that gets every book onto it, cheapest, with no step
 * "Too Expensive". On the game's 1.21.1 anvil rules: a step costs the prior-work penalty of both sides (2^n - 1, n the
 * times each went through an anvil) plus, for each enchantment on the sacrifice, its level times its anvil cost (halved
 * for a book, at least 1); a step of 40 levels or more is refused; what comes out has been through one more time than
 * the more worked of the two. Pure.
 */
public final class GodGear {
    private GodGear() {}

    /** An enchantment at a level, and its anvil cost from a book. */
    public record Ench(String id, int level, int bookCost) {}

    /** The anvil cost of each enchantment from a book (the game's item cost halved, at least 1). */
    static final Map<String, Integer> BOOK_COST = Map.ofEntries(
            Map.entry("protection", 1), Map.entry("fire_protection", 1), Map.entry("feather_falling", 1), Map.entry("blast_protection", 2),
            Map.entry("projectile_protection", 1), Map.entry("thorns", 4), Map.entry("respiration", 2), Map.entry("depth_strider", 2),
            Map.entry("aqua_affinity", 2), Map.entry("sharpness", 1), Map.entry("smite", 1), Map.entry("bane_of_arthropods", 1),
            Map.entry("knockback", 1), Map.entry("fire_aspect", 2), Map.entry("looting", 2), Map.entry("efficiency", 1),
            Map.entry("silk_touch", 4), Map.entry("unbreaking", 1), Map.entry("fortune", 2), Map.entry("power", 1), Map.entry("punch", 2),
            Map.entry("flame", 2), Map.entry("infinity", 4), Map.entry("luck_of_the_sea", 2), Map.entry("lure", 2),
            Map.entry("frost_walker", 2), Map.entry("mending", 2), Map.entry("impaling", 2), Map.entry("riptide", 2),
            Map.entry("loyalty", 1), Map.entry("channeling", 4), Map.entry("multishot", 2), Map.entry("piercing", 1),
            Map.entry("quick_charge", 1), Map.entry("soul_speed", 4), Map.entry("sweeping_edge", 2), Map.entry("swift_sneak", 4),
            Map.entry("density", 1), Map.entry("breach", 2), Map.entry("wind_burst", 2));

    static Ench e(String id, int level) {
        return new Ench(id, level, BOOK_COST.getOrDefault(id, 1));
    }

    /**
     * The best of everything for each piece, as players who max a world put it on (no curses, no Thorns: it wears the
     * armour out; Mending over Infinity on a bow; Silk Touch on one pickaxe, Fortune on another).
     */
    public static final Map<String, List<Ench>> BEST = Map.ofEntries(
            Map.entry("helmet", List.of(e("protection", 4), e("unbreaking", 3), e("mending", 1), e("respiration", 3), e("aqua_affinity", 1))),
            Map.entry("chestplate", List.of(e("protection", 4), e("unbreaking", 3), e("mending", 1))),
            Map.entry("leggings", List.of(e("protection", 4), e("unbreaking", 3), e("mending", 1), e("swift_sneak", 3))),
            Map.entry("boots", List.of(e("protection", 4), e("unbreaking", 3), e("mending", 1), e("feather_falling", 4),
                    e("depth_strider", 3), e("soul_speed", 3))),
            Map.entry("sword", List.of(e("sharpness", 5), e("unbreaking", 3), e("mending", 1), e("looting", 3), e("fire_aspect", 2),
                    e("sweeping_edge", 3))),
            Map.entry("axe", List.of(e("sharpness", 5), e("efficiency", 5), e("unbreaking", 3), e("mending", 1), e("silk_touch", 1))),
            Map.entry("pickaxe", List.of(e("efficiency", 5), e("unbreaking", 3), e("mending", 1), e("fortune", 3))),
            Map.entry("silk_pickaxe", List.of(e("efficiency", 5), e("unbreaking", 3), e("mending", 1), e("silk_touch", 1))),
            Map.entry("shovel", List.of(e("efficiency", 5), e("unbreaking", 3), e("mending", 1), e("silk_touch", 1))),
            Map.entry("hoe", List.of(e("efficiency", 5), e("unbreaking", 3), e("mending", 1), e("fortune", 3))),
            Map.entry("bow", List.of(e("power", 5), e("unbreaking", 3), e("mending", 1), e("punch", 2), e("flame", 1))),
            Map.entry("crossbow", List.of(e("quick_charge", 3), e("multishot", 1), e("unbreaking", 3), e("mending", 1))),
            Map.entry("trident", List.of(e("loyalty", 3), e("impaling", 5), e("channeling", 1), e("unbreaking", 3), e("mending", 1))),
            Map.entry("mace", List.of(e("density", 5), e("wind_burst", 3), e("unbreaking", 3), e("mending", 1))),
            Map.entry("fishing_rod", List.of(e("luck_of_the_sea", 3), e("lure", 3), e("unbreaking", 3), e("mending", 1))),
            Map.entry("shears", List.of(e("efficiency", 5), e("unbreaking", 3), e("mending", 1))),
            Map.entry("shield", List.of(e("unbreaking", 3), e("mending", 1))),
            Map.entry("elytra", List.of(e("unbreaking", 3), e("mending", 1))),
            Map.entry("flint_and_steel", List.of(e("unbreaking", 3), e("mending", 1))));

    /** One anvil step: the target (left) and the sacrifice (right) as lists of what each holds, and its level cost. */
    public record Step(List<String> target, List<String> sacrifice, int cost) {}

    /** The order: steps in turn, the total levels, and whether it can be done (every step under 40). */
    public record Order(List<Step> steps, int total, boolean possible) {}

    /** A step costs 40 or more: the anvil refuses it ("Too Expensive!"). */
    public static final int TOO_EXPENSIVE = 40;

    private record Best(int cost, int work, List<Step> steps) {}

    /**
     * The cheapest order to put these books on a fresh item (never through an anvil), every step under 40 levels: tried
     * over every way of pairing them up (the item always the target where it is part of a step). Up to 8 books.
     */
    public static Order order(List<Ench> books) {
        return order(books, 0);
    }

    /** As above, the item already through the anvil {@code itemWork} times (its penalty counts from there). */
    public static Order order(List<Ench> books, int itemWork) {
        int n = books.size();
        if (n == 0) return new Order(List.of(), 0, true);
        if (n > 8) throw new IllegalArgumentException("at most 8 books");
        int itemBit = 1 << n; // the item is leaf n
        int full = (1 << (n + 1)) - 1;
        // For each set of leaves and each work count it comes out with: the cheapest way to make it.
        Map<Integer, Map<Integer, Best>> memo = new HashMap<>();
        for (int i = 0; i <= n; i++) {
            Map<Integer, Best> m = new HashMap<>();
            int w = i == n ? Math.max(0, itemWork) : 0;
            m.put(w, new Best(0, w, List.of()));
            memo.put(1 << i, m);
        }
        for (int size = 2; size <= n + 1; size++) {
            for (int set = 1; set <= full; set++) {
                if (Integer.bitCount(set) != size) continue;
                Map<Integer, Best> out = new HashMap<>();
                // Every split into two non-empty parts; the part holding the item is the target.
                for (int a = (set - 1) & set; a > 0; a = (a - 1) & set) {
                    int b = set & ~a;
                    if (b == 0) continue;
                    if ((b & itemBit) != 0) continue; // the item is never the sacrifice
                    if ((a & itemBit) == 0 && a > b) continue; // two sets of books: each pairing once either way round
                    for (int order = 0; order < 2; order++) {
                        int tgt = order == 0 ? a : b, sac = order == 0 ? b : a;
                        if ((sac & itemBit) != 0) continue;
                        Map<Integer, Best> ta = memo.get(tgt), sb = memo.get(sac);
                        if (ta == null || sb == null) continue;
                        int value = 0;
                        for (int i = 0; i < n; i++) if ((sac & (1 << i)) != 0) value += books.get(i).level() * books.get(i).bookCost();
                        for (var x : ta.values()) {
                            for (var y : sb.values()) {
                                int cost = (1 << x.work()) - 1 + (1 << y.work()) - 1 + value;
                                if (cost >= TOO_EXPENSIVE) continue;
                                int work = Math.max(x.work(), y.work()) + 1;
                                int total = x.cost() + y.cost() + cost;
                                Best old = out.get(work);
                                if (old == null || total < old.cost()) {
                                    List<Step> steps = new ArrayList<>(x.steps());
                                    steps.addAll(y.steps());
                                    steps.add(new Step(names(books, tgt, n), names(books, sac, n), cost));
                                    out.put(work, new Best(total, work, steps));
                                }
                            }
                        }
                    }
                }
                if (!out.isEmpty()) memo.put(set, out);
            }
        }
        Map<Integer, Best> done = memo.get(full);
        if (done == null || done.isEmpty()) return new Order(List.of(), 0, false);
        Best best = null;
        for (Best b : done.values()) if (best == null || b.cost() < best.cost() || b.cost() == best.cost() && b.work() < best.work()) best = b;
        return new Order(best.steps(), best.cost(), true);
    }

    private static List<String> names(List<Ench> books, int set, int n) {
        List<String> out = new ArrayList<>();
        if ((set & (1 << n)) != 0) out.add("item");
        for (int i = 0; i < n; i++) if ((set & (1 << i)) != 0) out.add(books.get(i).id() + " " + books.get(i).level());
        return out;
    }

    /** What a piece is missing of its best: the enchantments wanted (at their level) it doesn't have at that level. */
    public static List<Ench> missing(String kind, Map<String, Integer> has) {
        List<Ench> out = new ArrayList<>();
        for (Ench want : BEST.getOrDefault(kind, List.of())) {
            if (has.getOrDefault(want.id(), 0) < want.level()) out.add(want);
        }
        return out;
    }

    /** The kind of piece an item is ("minecraft:netherite_boots" is boots), for {@link #BEST}; null for none. */
    public static String kindOf(String itemId) {
        String p = itemId.substring(itemId.indexOf(':') + 1);
        for (String k : List.of("helmet", "chestplate", "leggings", "boots", "sword", "pickaxe", "shovel", "hoe", "bow", "crossbow",
                "trident", "mace", "fishing_rod", "shears", "shield", "elytra", "flint_and_steel")) {
            if (p.equals(k) || p.endsWith("_" + k)) return k;
        }
        if (p.endsWith("_axe")) return "axe";
        return null;
    }
}
