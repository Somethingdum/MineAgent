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

    // ── The ladder it climbs in free time (its owner: "god-tier gear ... as an end goal. Unless told to speed run") ──

    /** A piece of a kind it has: its tier ({@link #tier}) and its enchantments by name ("sharpness") and level. */
    public record Piece(String kind, int tier, Map<String, Integer> ench) {}

    /** What it does next for a piece: makes one ({@code what} the item), puts its books on, or buys a book ({@code what} the enchantment). */
    public enum Move { MAKE, APPLY_BOOKS, BUY_BOOK, SMITH }

    public record Next(String kind, Move move, String what) {
        /** The key a failed try is remembered by, so the same thing isn't tried again at once. */
        public String key() {
            return kind + ":" + move + ":" + what;
        }
    }

    /** The pieces worked toward, in the order it works on them: what keeps it alive and earns first. */
    public static final List<String> LADDER = List.of("sword", "pickaxe", "chestplate", "leggings", "helmet", "boots", "axe", "bow",
            "shield", "shovel");

    /** Kinds that come in one material only: had (4) or not (0). */
    public static final java.util.Set<String> UNTIERED = java.util.Set.of("bow", "shield", "crossbow", "trident", "mace", "fishing_rod",
            "shears", "elytra", "flint_and_steel");

    /** A piece's tier: 1 wood, gold or leather; 2 stone or chainmail; 3 iron; 4 diamond (and any untiered kind); 5 netherite. */
    public static int tier(String itemId) {
        String p = itemId.substring(itemId.indexOf(':') + 1);
        if (p.startsWith("netherite_")) return 5;
        if (p.startsWith("diamond_")) return 4;
        if (p.startsWith("iron_")) return 3;
        if (p.startsWith("stone_") || p.startsWith("chainmail_")) return 2;
        if (p.startsWith("wooden_") || p.startsWith("golden_") || p.startsWith("leather_") || p.startsWith("turtle_")) return 1;
        return 4;
    }

    /**
     * The next thing to do toward god-tier gear, or null for nothing now. Each piece in {@link #LADDER} order: up to
     * diamond first (iron when diamond can't be made yet), as {@code canMake} says it can be made; then, on diamond or
     * better, the best enchantments ({@link #BEST}) it lacks: its books put on when it carries one of them at the level
     * wanted, else one bought from a villager it keeps that sells it at that level. Nothing in {@code skip} (tries that
     * failed a while ago, by {@link Next#key}).
     *
     * @param books the enchantments it carries as books, by name, at the highest level it has of each
     * @param sold  the enchantments its keepers sell as books, by name, at the highest level on offer
     */
    public static Next next(Map<String, Piece> pieces, Map<String, Integer> books, Map<String, Integer> sold,
                            java.util.function.Predicate<String> canMake, java.util.Set<String> skip) {
        return next(pieces, books, sold, canMake, k -> false, skip);
    }

    /**
     * As above, and a diamond piece upgraded to netherite at a smithing table first when {@code canSmith} says it has a
     * template and an ingot for it (its enchantments go with it, so the order doesn't waste a book).
     */
    public static Next next(Map<String, Piece> pieces, Map<String, Integer> books, Map<String, Integer> sold,
                            java.util.function.Predicate<String> canMake, java.util.function.Predicate<String> canSmith,
                            java.util.Set<String> skip) {
        for (String kind : LADDER) {
            Piece p = pieces.get(kind);
            int t = p == null ? 0 : p.tier();
            if (UNTIERED.contains(kind)) {
                if (p == null) {
                    Next n = new Next(kind, Move.MAKE, "minecraft:" + kind);
                    if (!skip.contains(n.key()) && canMake.test(n.what())) return n;
                    continue;
                }
            } else if (t < 4) {
                Next d = new Next(kind, Move.MAKE, "minecraft:diamond_" + kind);
                if (!skip.contains(d.key()) && canMake.test(d.what())) return d;
                Next i = new Next(kind, Move.MAKE, "minecraft:iron_" + kind);
                if (t < 3 && !skip.contains(i.key()) && canMake.test(i.what())) return i;
                continue; // books go on diamond and better only
            }
            if (t == 4 && !UNTIERED.contains(kind)) {
                Next up = new Next(kind, Move.SMITH, "minecraft:netherite_" + kind);
                if (!skip.contains(up.key()) && canSmith.test(kind)) return up;
            }
            List<Ench> lack = missing(kind, p == null ? Map.of() : p.ench());
            Next apply = new Next(kind, Move.APPLY_BOOKS, kind);
            if (!skip.contains(apply.key())) {
                for (Ench e : lack) if (books.getOrDefault(e.id(), 0) >= e.level()) return apply;
            }
            for (Ench e : lack) {
                Next buy = new Next(kind, Move.BUY_BOOK, e.id());
                if (!skip.contains(buy.key()) && sold.getOrDefault(e.id(), 0) >= e.level()) return buy;
            }
        }
        return null;
    }
}

