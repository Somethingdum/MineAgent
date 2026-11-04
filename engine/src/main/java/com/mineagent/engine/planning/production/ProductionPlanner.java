package com.mineagent.engine.planning.production;

import com.mineagent.engine.planning.production.Production.Book;
import com.mineagent.engine.planning.production.Production.Have;
import com.mineagent.engine.planning.production.Production.Kind;
import com.mineagent.engine.planning.production.Production.Need;
import com.mineagent.engine.planning.production.Production.Recipe;
import com.mineagent.engine.planning.production.Production.Source;
import com.mineagent.engine.planning.production.Production.Tool;
import com.mineagent.engine.planning.production.Production.Where;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The most efficient way to make {@code count} of an item (SMH plan 70, "Two orders, planned"; the owner, Nov 3:
 * "Ensure that it has good crafting knowledge. If I ask for an entire chest of stone bricks, it should find the most
 * efficient way to do that."). Every way is priced in game ticks: what it already has and what is in its chests (free,
 * but a chest is a walk), mining (the break time with its tool, the walk between blocks, a share of the tool worn out,
 * a search if it has not seen the block), and each of the game's recipes (its ingredients priced the same way, plus
 * the work: a craft, or a furnace's cook time shared over the furnaces it runs and the fuel it burns). It takes the
 * cheapest, then writes out the whole job: what to fetch, mine, craft and smelt, how many furnaces are worth building,
 * which fuel, the tools it will wear out and make on the way (a wooden pickaxe first if it has none, then the
 * cheapest per use), and about how long. Pure.
 */
public final class ProductionPlanner {
    /** Ticks walking and picking up per block mined in a tunnel, and per block of walk to a seen block (there and back). */
    static final double MINE_WALK = 10.0;
    static final double WALK_PER_BLOCK = 9.0;
    /** A block it has never seen: a search first, priced as this many ticks for every block mined. */
    static final double UNSEEN = 400.0;
    /** Going to a furnace, loading it, emptying it: about 5 seconds each it lights. */
    static final double TEND_TICKS = 100.0;
    /** Going off to get fuel at all (to the trees, to coal) when what it carries would do: 10 seconds. */
    static final double ERRAND_TICKS = 200.0;
    /** Finding something it hasn't seen (trees from a desert, coal it hasn't come across): about two minutes. */
    static final double SEARCH_TICKS = 2_400.0;
    /** Ticks a craft takes (a player's click), and fetching one stack from a chest it knows. */
    static final double CRAFT_TICKS = 4.0;
    static final double FETCH_PER_STACK = 20.0;
    /** A trade in the villager's menu: about half a second each. */
    static final double TRADE_TICKS = 10.0;
    /** Furnaces it runs at once at most, and the planning guess of how many share a big smelt. */
    public static final int MAX_FURNACES = 16;
    static final double TYPICAL_FURNACES = 8.0;
    static final int SMELT_TICKS = 200;
    static final int MAX_DEPTH = 12;

    private final Book book;
    private final Have have;

    /** What it has, as the job uses it up: items carried (and made left over), in its chests, tools' uses, furnaces. */
    private final Map<String, Integer> carried = new HashMap<>();
    private final Map<String, Integer> stored = new HashMap<>();
    private final List<Tool> tools = new ArrayList<>();
    private int furnaces;
    private boolean table;
    private final Map<String, Double> memo = new HashMap<>();
    /** What it may still take of a source there is only so much of (animals, sheep to shear, lava pools), for this job. */
    private final Map<String, Integer> limitedLeft = new HashMap<>();

    /** The job, in order. */
    private final List<Step> steps = new ArrayList<>();
    private final Map<String, Integer> toolsMade = new LinkedHashMap<>();
    private final List<String> problems = new ArrayList<>();
    /** Sources not to use: found no more of. */
    private final Set<String> without;
    private int smokers, blasts;

    public enum Action { FETCH, MINE, HUNT, SHEAR, HARVEST, CRAFT, SMELT, TRADE, FILL }

    /**
     * One step of the job: FETCH {@code count} of {@code item} from its chests; MINE {@code blocks} of {@code block}
     * for {@code count} of {@code item} with {@code tool}; HUNT {@code blocks} animals ({@code block},
     * "entity:minecraft:cow") for {@code count} of what they drop; CRAFT {@code batches} of {@code recipe} for {@code count}
     * of {@code item}; SMELT {@code count} of {@code input} into {@code item} in {@code furnaces} furnaces burning
     * {@code fuelCount} {@code fuel}; TRADE {@code batches} times the villager's offer {@code recipe} for {@code count} of
     * {@code item}, paying {@code fuelCount} {@code input} in all.
     */
    public record Step(Action action, String item, int count, String recipe, int batches, String block, int blocks,
                       String tool, String input, String fuel, int fuelCount, int furnaces, boolean table) {}

    /** The whole job, what it uses up, and about how long it takes. */
    public record Plan(String item, int count, List<Step> steps, Map<String, Integer> mined, Map<String, Integer> toolsMade,
                       int furnacesBuilt, Map<String, Integer> fuel, double ticks, List<String> problems) {
        public boolean possible() {
            return problems.isEmpty();
        }

        /** The plan in a line a person reads: what makes it, what it uses, about how long. */
        public String summary() {
            if (!possible()) return String.format(Locale.ROOT, "I can't see a way to make %,d %s: %s.", count, plural(item, count),
                    String.join("; ", problems));
            List<String> parts = new ArrayList<>();
            for (Step s : steps) {
                switch (s.action()) {
                    case FETCH -> parts.add(String.format(Locale.ROOT, "%,d %s from my chests", s.count(), plural(s.item(), s.count())));
                    case MINE -> parts.add(Production.fluid(s.block())
                            ? String.format(Locale.ROOT, "fill %,d %s with %s", s.blocks(), s.blocks() == 1 ? "bucket" : "buckets", name(s.block()))
                            : String.format(Locale.ROOT, "mine %,d %s", s.blocks(), plural(s.block(), s.blocks())));
                    case HUNT -> parts.add(String.format(Locale.ROOT, "hunt %,d %s", s.blocks(), plural(s.block(), s.blocks())));
                    case SHEAR -> parts.add(String.format(Locale.ROOT, "shear %,d %s", s.blocks(), plural(s.block(), s.blocks())));
                    case HARVEST -> parts.add(String.format(Locale.ROOT, "harvest %,d ripe %s", s.blocks(), name(s.block())));
                    case SMELT -> {
                        String station = s.recipe() != null && s.recipe().contains("smoking") ? "smoker"
                                : s.recipe() != null && s.recipe().contains("blasting") ? "blast furnace" : "furnace";
                        parts.add(String.format(Locale.ROOT, "%s %,d %s into %s in %d %s with %,d %s",
                                station.equals("smoker") ? "cook" : "smelt", s.count(), plural(s.input(), s.count()), name(s.item()),
                                s.furnaces(), s.furnaces() == 1 ? station : station + "s",
                                s.fuelCount(), plural(s.fuel(), s.fuelCount())));
                    }
                    case CRAFT -> {
                        if (!s.item().equals(item)) parts.add(String.format(Locale.ROOT, "craft %,d %s", s.count(), plural(s.item(), s.count())));
                    }
                    case FILL -> parts.add(s.block() != null && s.block().contains("entity:")
                            ? String.format(Locale.ROOT, "milk %s into %,d %s", name(s.block()) .equals("goat") ? "a goat" : "a " + name(s.block()),
                                    s.count(), s.count() == 1 ? "bucket" : "buckets")
                            : String.format(Locale.ROOT, "fill %,d %s with %s", s.count(), s.count() == 1 ? "bucket" : "buckets", name(s.block())));
                    case TRADE -> {
                        String who = String.valueOf(Production.tradeWith(s.recipe())).replace('_', ' ');
                        parts.add(s.item().equals("minecraft:emerald")
                                ? String.format(Locale.ROOT, "sell %,d %s to %s %s for %,d %s", s.fuelCount(), plural(s.input(), s.fuelCount()),
                                        who.matches("^[aeiou].*") ? "an" : "a", who, s.count(), plural(s.item(), s.count()))
                                : String.format(Locale.ROOT, "buy %,d %s from %s %s for %,d %s", s.count(), plural(s.item(), s.count()),
                                        who.matches("^[aeiou].*") ? "an" : "a", who, s.fuelCount(), plural(s.input(), s.fuelCount())));
                    }
                }
            }
            String head = String.format(Locale.ROOT, "%,d %s", count, plural(item, count));
            if (parts.isEmpty()) {
                // Only the item itself to craft, from what it carries ("4 oak planks: from the logs I have"): not "already".
                if (steps.stream().anyMatch(st -> st.action() == Action.CRAFT)) return head + ": made from what I have; under a minute.";
                return head + ": I have " + (count == 1 ? "it" : "them") + " already.";
            }
            long m = Math.round(ticks / 1_200.0);
            String minutes = ticks < 1_200 ? "under a minute" : m <= 1 ? "about a minute" : String.format(Locale.ROOT, "about %d minutes", m);
            return head + ": " + String.join(", ", parts) + "; " + minutes + ".";
        }
    }

    private ProductionPlanner(Book book, Have have, Set<String> without) {
        this.book = book;
        this.have = have;
        this.without = without;
        carried.putAll(have.carried());
        stored.putAll(have.stored());
        if (have.limited() != null) limitedLeft.putAll(have.limited());
        tools.addAll(have.tools());
        furnaces = have.furnaces();
        smokers = have.smokers();
        blasts = have.blastFurnaces();
        table = have.table();
    }

    /** The cheapest way to have {@code count} of {@code item}, start to finish. */
    public static Plan plan(Book book, Have have, String item, int count) {
        return plan(book, have, item, count, Set.of());
    }

    /**
     * As {@link #plan(Book, Have, String, int)}, without the sources in {@code without} (blocks or animals it found no
     * more of on the way).
     */
    public static Plan plan(Book book, Have have, String item, int count, Set<String> without) {
        ProductionPlanner p = new ProductionPlanner(book, have, without == null ? Set.of() : without);
        // A furnace (or smoker, or blast furnace) it carries is counted as one it can use, not as an item to spend
        // as well; unless furnaces are the order.
        p.stationsNotItems("minecraft:furnace", have.furnaces(), item);
        p.stationsNotItems("minecraft:smoker", have.smokers(), item);
        p.stationsNotItems("minecraft:blast_furnace", have.blastFurnaces(), item);
        if (count > 0) p.make(item, count, 0, new HashSet<>());
        return p.finish(item, count);
    }

    /**
     * The most of {@code item} it can make, up to {@code count}, without those sources: the whole when it can, else
     * the largest part (it ran out of cows; the raw beef it carries is still cooked). 0 when none.
     */
    public static int most(Book book, Have have, String item, int count, Set<String> without) {
        if (plan(book, have, item, count, without).possible()) return count;
        int lo = 0, hi = count - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            if (plan(book, have, item, mid, without).possible()) lo = mid;
            else hi = mid - 1;
        }
        return lo;
    }

    private Plan finish(String item, int count) {
        List<Step> merged = merge(steps);
        Map<String, Integer> mined = new LinkedHashMap<>();
        Map<String, Integer> fuel = new LinkedHashMap<>();
        Set<String> visited = new HashSet<>();
        double ticks = 0;
        int built = 0;
        for (Step s : merged) {
            switch (s.action()) {
                case FETCH -> ticks += FETCH_PER_STACK * Math.ceil(s.count() / 64.0);
                case MINE, HUNT, SHEAR, HARVEST -> {
                    if (!mined.containsKey(s.block())) ticks += getThere(s.block());
                    mined.merge(s.block(), s.blocks(), Integer::sum);
                    ticks += s.blocks() * (breakTicks(s.block(), s.item()) + MINE_WALK);
                }
                case CRAFT -> {
                    ticks += s.batches() * CRAFT_TICKS;
                    if (s.item().equals("minecraft:furnace")) built += s.count();
                }
                case FILL -> {
                    if (visited.add(s.block())) ticks += 2.0 * WALK_PER_BLOCK * have.knownDistance().getOrDefault(s.block(), 16);
                    ticks += s.count() * TRADE_TICKS;
                }
                case TRADE -> {
                    String villager = s.recipe().substring(0, s.recipe().lastIndexOf(':'));
                    if (visited.add(villager)) ticks += 2.0 * WALK_PER_BLOCK * have.knownDistance().getOrDefault(s.recipe(), 32);
                    ticks += s.batches() * TRADE_TICKS;
                }
                case SMELT -> {
                    fuel.merge(s.fuel(), s.fuelCount(), Integer::sum);
                    ticks += Math.ceil(s.count() / (double) Math.max(1, s.furnaces())) * cookTicks(s.recipe());
                }
            }
        }
        return new Plan(item, count, merged, mined, new LinkedHashMap<>(toolsMade), built, fuel, ticks, List.copyOf(problems));
    }

    /** Getting to where a block or animal is, once: nothing for stone; there and back to one it has seen; a search. */
    private double getThere(String block) {
        Where w = have.where(block);
        if (w == Where.COMMON) return 0;
        if (w == Where.KNOWN) return 2.0 * WALK_PER_BLOCK * have.knownDistance().getOrDefault(block, 32);
        return SEARCH_TICKS;
    }

    // ── Pricing ─────────────────────────────────────────────────────

    /** Ticks for one more of {@code item} made the cheapest way, from scratch (what it has is priced elsewhere). */
    double unit(String item, Set<String> path) {
        Double m = memo.get(item);
        if (m != null) return m;
        if (path.contains(item) || path.size() > MAX_DEPTH) return Double.POSITIVE_INFINITY;
        path.add(item);
        double best = Double.POSITIVE_INFINITY;
        for (Source s : book.sources(item)) best = Math.min(best, mineUnit(s, path));
        for (Recipe r : book.recipes(item)) best = Math.min(best, recipeUnit(r, path));
        // No more to be had, but some in hand (the beef it carries once the cows are gone): those are used. Not when
        // pricing more of it than it has (more fuel): there, none to be had is none.
        if (Double.isInfinite(best) && available(item) > 0 && !pricingMore) best = 0;
        path.remove(item);
        memo.put(item, best);
        return best;
    }

    /** Pricing more of an item than it holds (more fuel): what it holds is not "free" more. */
    private boolean pricingMore;
    /** Pricing from what it has seen only (is a search needed for this?). */
    private boolean seenOnly;

    /** Items whose Silk Touch tool is worn out for this job. */
    private final Set<String> noSilk = new HashSet<>();

    private double mineUnit(Source s, Set<String> path) {
        if (s.perBlock() <= 0 || (s.silkTouch() && noSilk.contains(s.item())) || without.contains(s.block())
                || Production.animal(s.block()) && without.contains(Production.ENTITY + "*")) return Double.POSITIVE_INFINITY;
        if (limitedLeft.containsKey(s.block()) && limitedLeft.get(s.block()) <= 0) return Double.POSITIVE_INFINITY; // all taken
        double wear = toolWear(s, path);
        if (Double.isInfinite(wear)) return wear;
        Where where = have.where(s.block());
        if (where == Where.UNKNOWN && (seenOnly || without.contains(Production.UNSEEN))) return Double.POSITIVE_INFINITY;
        if (Production.fluid(s.block()) && where != Where.KNOWN) return Double.POSITIVE_INFINITY; // lava in sight only: never gone looking for
        double reach = where == Where.COMMON ? 0 : where == Where.KNOWN
                ? WALK_PER_BLOCK * have.knownDistance().getOrDefault(s.block(), 32) / 64.0 : UNSEEN * rarity(s.block());
        return (s.breakTicks() + MINE_WALK + wear + reach) / s.perBlock();
    }

    /**
     * How much harder a block it has never seen is to find than most: an emerald ore (in mountains only, a vein of
     * one) or ancient debris far more than iron; diamonds deep and few. 1 for the rest.
     */
    static double rarity(String block) {
        if (block == null) return 1.0;
        String b = block.substring(block.indexOf(':') + 1).replaceFirst("^deepslate_", "");
        return switch (b) {
            case "emerald_ore", "ancient_debris" -> 25.0;
            case "diamond_ore" -> 6.0;
            case "gold_ore", "lapis_ore" -> 3.0;
            case "redstone_ore" -> 2.0;
            case "iron_ore" -> 1.5;
            default -> 1.0;
        };
    }

    /** Its share of the tool a block wears out: none by hand; the cheapest tool per use otherwise. */
    private double toolWear(Source s, Set<String> path) {
        if (s.tool().isEmpty()) return 0;
        for (Tool t : tools) if (fits(t, s) && t.usesLeft() > 0) return 0;
        if (s.silkTouch()) return Double.POSITIVE_INFINITY; // a Silk Touch tool is found, not made
        double best = Double.POSITIVE_INFINITY;
        for (String t : craftableTools(s.tool(), s.tier())) {
            int uses = book.durability().getOrDefault(t, 0);
            if (uses <= 0) continue;
            best = Math.min(best, unit(t, path) / uses);
        }
        return best;
    }

    private double recipeUnit(Recipe r, Set<String> path) {
        return recipeUnit(r, path, 0);
    }

    /**
     * One of what a recipe makes, priced; with {@code batches} &gt; 0, the batches about to be made: an ingredient it holds
     * enough of for all of them costs nothing (the sugar cane it carries makes sugar before honey it would have to find).
     */
    private double recipeUnit(Recipe r, Set<String> path, int batches) {
        if (r.kind() == Kind.STONECUT || r.kind() == Kind.CAMPFIRE) return Double.POSITIVE_INFINITY; // stations it doesn't run yet
        if (r.kind() == Kind.TRADE && limitedLeft.getOrDefault(r.id(), 1) <= 0) return Double.POSITIVE_INFINITY; // sold out
        if (r.kind() == Kind.FILL && have.where(r.id()) != Where.KNOWN) return Double.POSITIVE_INFINITY; // no water or cow near
        // A smoker or blast furnace when it has one (twice as quick for food or ore); it doesn't build them.
        if (r.kind() == Kind.BLAST && blasts == 0 || r.kind() == Kind.SMOKE && smokers == 0) return Double.POSITIVE_INFINITY;
        double in = 0;
        for (Need n : r.needs()) {
            double cheapest = Double.POSITIVE_INFINITY;
            for (String alt : n.anyOf()) {
                cheapest = Math.min(cheapest, batches > 0 && available(alt) >= (long) n.count() * batches ? 0 : unit(alt, path));
            }
            in += cheapest * n.count();
            if (Double.isInfinite(in)) return in;
        }
        double stations = r.kind() == Kind.SMELT ? TYPICAL_FURNACES : Math.max(1, r.kind() == Kind.SMOKE ? smokers : blasts);
        // A trade: the menu, and its share of the walk to the villager (over the trades it makes there).
        double work = r.kind() == Kind.FILL ? TRADE_TICKS + WALK_PER_BLOCK * have.knownDistance().getOrDefault(r.id(), 16) / 2.0
                : r.kind() == Kind.TRADE ? TRADE_TICKS + WALK_PER_BLOCK * have.knownDistance().getOrDefault(r.id(), 32) / 8.0
                : r.cooked() ? r.cookTicks() / stations + fuelPerSmelt(path) * r.cookTicks() / SMELT_TICKS : CRAFT_TICKS;
        return (in + work) / Math.max(1, r.outputCount());
    }

    /** The cheapest fuel per smelt (200 ticks of burning): one it has costs nothing. */
    private double fuelPerSmelt(Set<String> path) {
        double best = Double.POSITIVE_INFINITY;
        for (var e : book.fuelTicks().entrySet()) {
            if (e.getValue() < SMELT_TICKS / 2) continue;
            double perSmelt = (available(e.getKey()) > 0 ? 0 : unit(e.getKey(), path)) * SMELT_TICKS / e.getValue();
            best = Math.min(best, perSmelt);
        }
        return best;
    }

    private boolean fits(Tool t, Source s) {
        return t.kind().equals(s.tool()) && t.tier() >= s.tier() && (!s.silkTouch() || t.silkTouch());
    }

    /** Tools of a kind at least this tier that a recipe makes, cheapest tier first. */
    private List<String> craftableTools(String kind, int tier) {
        List<String> out = new ArrayList<>();
        for (String t : book.durability().keySet()) {
            if (!t.endsWith("_" + kind) && !t.endsWith(":" + kind)) continue; // a stone pickaxe; shears
            boolean tiered = !t.endsWith(":" + kind);
            if ((!tiered || tierOf(t) >= Math.max(1, tier)) && !book.recipes(t).isEmpty()) out.add(t);
        }
        return out;
    }

    /** A tool's tier from its name, as the game tiers them. */
    public static int tierOf(String toolId) {
        String n = toolId.substring(toolId.indexOf(':') + 1);
        if (n.startsWith("wooden_") || n.startsWith("golden_")) return 1;
        if (n.startsWith("stone_")) return 2;
        if (n.startsWith("iron_")) return 3;
        if (n.startsWith("diamond_")) return 4;
        if (n.startsWith("netherite_")) return 5;
        return 0;
    }

    // ── The job ─────────────────────────────────────────────────────

    /** Have {@code count} more of {@code item}: from its chests, then the cheapest way. */
    private void make(String item, int count, int depth, Set<String> path) {
        if (count <= 0) return;
        if (depth > MAX_DEPTH) {
            problems.add("the chain for " + name(item) + " is too long");
            return;
        }
        // What it carries first, then what is in its chests (a fetch), then made.
        int fromCarried = Math.min(count, Math.max(0, carried.getOrDefault(item, 0)));
        if (fromCarried > 0) carried.merge(item, -fromCarried, Integer::sum);
        int left = count - fromCarried;
        int fromChest = Math.min(left, Math.max(0, stored.getOrDefault(item, 0)));
        if (fromChest > 0) {
            stored.merge(item, -fromChest, Integer::sum);
            steps.add(new Step(Action.FETCH, item, fromChest, null, 0, null, 0, null, null, null, 0, 0, false));
            left -= fromChest;
        }
        if (left <= 0) return;

        // The cheapest way for the rest.
        memo.clear();
        Source bestSource = null;
        Recipe bestRecipe = null;
        double best = Double.POSITIVE_INFINITY;
        Set<String> p = new HashSet<>(path);
        p.add(item);
        for (Source s : book.sources(item)) {
            double c = mineUnit(s, p);
            if (c < best) {
                best = c;
                bestSource = s;
                bestRecipe = null;
            }
        }
        for (Recipe r : book.recipes(item)) {
            double c = recipeUnit(r, p, (int) Math.ceil(left / (double) Math.max(1, r.outputCount())));
            if (c < best) {
                best = c;
                bestRecipe = r;
                bestSource = null;
            }
        }
        if (Double.isInfinite(best)) {
            String few = tooFew(item, left);
            problems.add(few != null ? few : whyNot(item, p));
            return;
        }
        if (bestSource != null && bestSource.silkTouch()) {
            // A Silk Touch tool is found, not made: it mines what its uses cover, and the rest is got another way.
            int uses = 0;
            for (Tool t : tools) if (fits(t, bestSource) && t.usesLeft() > 0) uses += t.usesLeft();
            int cover = (int) Math.floor(uses * bestSource.perBlock());
            if (cover < left) {
                if (cover > 0) mine(bestSource, cover, depth, p);
                noSilk.add(item);
                try {
                    make(item, left - cover, depth, path);
                } finally {
                    noSilk.remove(item);
                }
                return;
            }
        }
        if (bestSource != null && limitedLeft.containsKey(bestSource.block())) {
            // So many animals there (or sheep to shear, or lava): what they give, and the rest another way.
            int can = limitedLeft.get(bestSource.block());
            int need = (int) Math.ceil(left / bestSource.perBlock());
            if (need > can) {
                int got = Math.min(left, (int) Math.floor(can * bestSource.perBlock()));
                if (got > 0) mine(bestSource, got, depth, p);
                limitedLeft.put(bestSource.block(), 0);
                make(item, left - got, depth, path);
                return;
            }
            limitedLeft.put(bestSource.block(), can - need);
        }
        if (bestRecipe != null && bestRecipe.kind() == Kind.TRADE && limitedLeft.containsKey(bestRecipe.id())) {
            // A villager's offer has so many uses before it restocks: what they give, and the rest another way.
            int can = limitedLeft.get(bestRecipe.id());
            int per = Math.max(1, bestRecipe.outputCount());
            int need = (int) Math.ceil(left / (double) per);
            if (need > can) {
                int got = Math.min(left, can * per);
                limitedLeft.put(bestRecipe.id(), 0);
                if (got > 0) craft(bestRecipe, got, depth, p);
                make(item, left - got, depth, path);
                return;
            }
            limitedLeft.put(bestRecipe.id(), can - need);
        }
        if (bestSource != null) mine(bestSource, left, depth, p);
        else craft(bestRecipe, left, depth, p);
    }

    /** When the animals near are all spoken for: "not enough cows near for 6 more beef (I leave two of a kind)". */
    private String tooFew(String item, int left) {
        for (Source s : book.sources(item)) {
            if (!limitedLeft.containsKey(s.block()) || limitedLeft.get(s.block()) > 0) continue;
            String b = s.block();
            if (Production.fluid(b)) {
                return String.format(Locale.ROOT, "there isn't enough %s near for %d more %s", name(b), left, plural(item, left));
            }
            String who = Production.shearing(b)
                    ? b.substring(b.lastIndexOf(':') + 1).replace('_', ' ') + " sheep ready to shear"
                    : b.substring(b.lastIndexOf(':') + 1).replace('_', ' ') + "s";
            return String.format(Locale.ROOT, "there aren't enough %s near for %d more %s%s", who, left, plural(item, left),
                    Production.animal(b) ? " (I leave two of a kind)" : "");
        }
        return null;
    }

    /**
     * Why there is no way to {@code item}, a level down: what its nearest recipe lacks ("no coal or charcoal, and no
     * stick to be had"), or that nothing makes it and nothing near drops it.
     */
    private String whyNot(String item, Set<String> path) {
        String best = null;
        int fewest = Integer.MAX_VALUE;
        for (Recipe r : book.recipes(item)) {
            if (r.kind() == Kind.STONECUT || r.kind() == Kind.CAMPFIRE || r.kind() == Kind.TRADE || r.kind() == Kind.FILL) continue;
            List<String> lacking = new ArrayList<>();
            for (Need n : r.needs()) {
                boolean any = false;
                for (String alt : n.anyOf()) {
                    memo.clear();
                    if (!Double.isInfinite(unit(alt, new HashSet<>(path)))) {
                        any = true;
                        break;
                    }
                }
                if (!any) {
                    List<String> names = new ArrayList<>();
                    for (String alt : n.anyOf().subList(0, Math.min(3, n.anyOf().size()))) names.add(name(alt));
                    lacking.add(String.join(" or ", names));
                }
            }
            if (!lacking.isEmpty() && lacking.size() < fewest) {
                fewest = lacking.size();
                best = String.format(Locale.ROOT, "%s needs %s, and I have none and know of none near", plural(item, 2), String.join(" and ", lacking));
            }
        }
        if (best != null) return best;
        if (NETHER.contains(item)) return name(item) + " comes from the Nether, and I have none and know of none stored";
        for (Recipe r : book.recipes(item)) {
            if (r.kind() != Kind.FILL) continue;
            // A bucket fills only at a source block or from an animal: say which is missing.
            return r.id().contains("entity:") ? "there's no cow, mooshroom or goat near to milk" : "there's no still water near to fill a bucket at";
        }
        return book.recipes(item).isEmpty() && book.sources(item).isEmpty()
                ? "I know no way to get " + name(item)
                : "I know of no " + name(item) + " near here, nor anything to make it from";
    }

    /** What only the Nether gives (MA7.2): said so, when there is none to be had here. */
    static final java.util.Set<String> NETHER = java.util.Set.of("minecraft:blaze_rod", "minecraft:nether_wart", "minecraft:quartz",
            "minecraft:glowstone_dust", "minecraft:ghast_tear", "minecraft:magma_cream", "minecraft:netherite_scrap", "minecraft:ancient_debris",
            "minecraft:soul_sand", "minecraft:crimson_stem", "minecraft:warped_stem", "minecraft:shroomlight", "minecraft:wither_skeleton_skull");

    private int available(String item) {
        return Math.max(0, carried.getOrDefault(item, 0)) + Math.max(0, stored.getOrDefault(item, 0));
    }

    private void mine(Source s, int count, int depth, Set<String> path) {
        int blocks = (int) Math.ceil(count / s.perBlock());
        String toolName = "";
        int extra = 0;
        if (!s.tool().isEmpty()) {
            int uses = usesOf(s);
            if (uses == 0 && s.silkTouch()) {
                problems.add("I'd need a Silk Touch " + s.tool());
                return;
            }
            if (uses == 0) {
                // No tool at all: one to start with that doesn't need what it is about to mine (a wooden pickaxe).
                Set<String> without = new HashSet<>(path);
                without.add(s.item());
                String boot = cheapestTool(s, without);
                if (boot == null) {
                    problems.add("I can't make a " + s.tool() + " good enough for " + name(s.block()));
                    return;
                }
                make(boot, 1, depth + 1, path);
                toolsMade.merge(boot, 1, Integer::sum);
                tools.add(new Tool(s.tool(), tierOf(boot), false, book.durability().getOrDefault(boot, 1)));
                uses = usesOf(s);
                toolName = boot;
            }
            if (uses < blocks) {
                // Then the cheapest tool per use as things stand; what it is made of that this mining yields (a stone
                // pickaxe's 3 cobblestone) is mined on top, from the blocks the old tool has left.
                String t = cheapestTool(s, new HashSet<>());
                Recipe tr = t == null ? null : cheapestRecipe(t);
                if (t == null || tr == null) {
                    problems.add("I can't make a " + s.tool() + " good enough for " + name(s.block()));
                    return;
                }
                int per = book.durability().getOrDefault(t, 1);
                int self = 0;
                for (Need n : tr.needs()) if (n.anyOf().contains(s.item())) self += n.count();
                int selfBlocks = (int) Math.ceil(self / s.perBlock());
                if (per <= selfBlocks) {
                    problems.add("a " + name(t) + " wears out before it pays for itself");
                    return;
                }
                int k = (int) Math.ceil((blocks - uses) / (double) (per - selfBlocks));
                for (Need n : tr.needs()) {
                    if (n.anyOf().contains(s.item())) extra += n.count() * k;
                    else make(pick(n, n.count() * k, path), n.count() * k, depth + 1, path);
                }
                if (tr.needsTable() && !table) {
                    table = true;
                    make("minecraft:crafting_table", 1, depth + 1, path);
                }
                steps.add(new Step(Action.CRAFT, t, k, tr.id(), k, null, 0, null, null, null, 0, 0, tr.needsTable()));
                toolsMade.merge(t, k, Integer::sum);
                tools.add(new Tool(s.tool(), tierOf(t), false, k * per));
                blocks += (int) Math.ceil(extra / s.perBlock());
                toolName = t;
            }
            spendUses(s, blocks);
            if (toolName.isEmpty()) toolName = s.tool();
        }
        Action act = Production.animal(s.block()) ? Action.HUNT : Production.shearing(s.block()) ? Action.SHEAR
                : Production.crop(s.block()) ? Action.HARVEST : Action.MINE;
        steps.add(new Step(act, s.item(), count, null, 0, s.block(), blocks, toolName, null, null, 0, 0, false));
        // What is mined beyond the order and the tools made on the way is left over, for later steps.
        int surplus = (int) Math.floor(blocks * s.perBlock()) - count - extra;
        if (surplus > 0) carried.merge(s.item(), surplus, Integer::sum);
    }

    private int usesOf(Source s) {
        int uses = 0;
        for (Tool t : tools) if (fits(t, s) && t.usesLeft() > 0) uses += t.usesLeft();
        return uses;
    }

    private void spendUses(Source s, int blocks) {
        int need = blocks;
        for (int i = 0; i < tools.size() && need > 0; i++) {
            Tool t = tools.get(i);
            if (!fits(t, s) || t.usesLeft() <= 0) continue;
            int use = Math.min(need, t.usesLeft());
            tools.set(i, new Tool(t.kind(), t.tier(), t.silkTouch(), t.usesLeft() - use));
            need -= use;
        }
    }

    private Recipe cheapestRecipe(String item) {
        Recipe best = null;
        double bestCost = Double.POSITIVE_INFINITY;
        memo.clear();
        for (Recipe r : book.recipes(item)) {
            double c = recipeUnit(r, new HashSet<>());
            if (c < bestCost) {
                bestCost = c;
                best = r;
            }
        }
        return best;
    }

    /** The tool for this source that costs least per use, as things stand now; {@code path}: what it may not need. */
    private String cheapestTool(Source s, Set<String> path) {
        String best = null;
        double bestCost = Double.POSITIVE_INFINITY;
        memo.clear();
        for (String t : craftableTools(s.tool(), s.tier())) {
            int uses = book.durability().getOrDefault(t, 0);
            if (uses <= 0) continue;
            Set<String> p = new HashSet<>(path);
            double c = unit(t, p) / uses;
            if (c < bestCost) {
                bestCost = c;
                best = t;
            }
        }
        memo.clear();
        return best;
    }

    private void craft(Recipe r, int count, int depth, Set<String> path) {
        int batches = (int) Math.ceil(count / (double) Math.max(1, r.outputCount()));
        int made = batches * Math.max(1, r.outputCount());
        if (r.cooked()) {
            smelt(r, batches, depth, path);
        } else if (r.kind() == Kind.FILL) {
            for (Need n : r.needs()) make(pick(n, n.count() * batches, path), n.count() * batches, depth + 1, path);
            steps.add(new Step(Action.FILL, r.output(), made, r.id(), batches, r.id(), batches, null, null, null, 0, 0, false));
        } else if (r.kind() == Kind.TRADE) {
            String paid = null;
            int paying = 0;
            for (Need n : r.needs()) {
                String alt = pick(n, n.count() * batches, path);
                make(alt, n.count() * batches, depth + 1, path);
                if (paid == null) {
                    paid = alt;
                    paying = n.count() * batches;
                }
            }
            steps.add(new Step(Action.TRADE, r.output(), made, r.id(), batches, null, 0, null, paid, null, paying, 0, false));
        } else {
            for (Need n : r.needs()) make(pick(n, n.count() * batches, path), n.count() * batches, depth + 1, path);
            boolean wantTable = r.needsTable() && !table;
            if (wantTable) {
                table = true; // one is placed and used for the rest of the job
                make("minecraft:crafting_table", 1, depth + 1, path);
            }
            steps.add(new Step(Action.CRAFT, r.output(), made, r.id(), batches, null, 0, null, null, null, 0, 0, r.needsTable()));
        }
        if (made > count) carried.merge(r.output(), made - count, Integer::sum);
    }

    /** The fuel and furnaces for a smelt, and what they cost all told (the time, the furnaces built, fuel got). */
    private record Cook(Recipe recipe, String fuel, FurnaceSplit.Split split, double total) {}

    private int stationsFor(Kind kind) {
        return kind == Kind.SMELT ? furnaces : kind == Kind.SMOKE ? smokers : kind == Kind.BLAST ? blasts : 0;
    }

    /**
     * The cheapest fuel and number of stations for {@code items} of {@code r}: each takes whole pieces of fuel's worth
     * (a coal, 8 items; a smoker or blast furnace burns fuel twice as fast as it cooks twice as fast), so the stations
     * worth lighting depend on the fuel. Furnaces may be built; smokers and blast furnaces are only the ones it has.
     */
    private Cook bestCook(Recipe r, int items, Set<String> path) {
        boolean furnace = r.kind() == Kind.SMELT;
        int haveStations = stationsFor(r.kind());
        if (!furnace && haveStations == 0) return null;
        int mostStations = furnace ? MAX_FURNACES : haveStations;
        Map<Integer, Double> buildCost = new HashMap<>();
        Cook best = null;
        for (var e : book.fuelTicks().entrySet()) {
            if (e.getValue() < SMELT_TICKS / 2) continue;
            int burn = furnace ? e.getValue() : e.getValue() / 2;
            memo.clear();
            pricingMore = true; // what more of this fuel than it has would cost (infinite: none to be had)
            double fuelUnit = unit(e.getKey(), new HashSet<>(path));
            // More of it only from something it hasn't seen (trees from a treeless field): that errand is a search.
            memo.clear();
            seenOnly = true;
            double seenUnit = unit(e.getKey(), new HashSet<>(path));
            seenOnly = false;
            pricingMore = false;
            memo.clear();
            double errand = Double.isInfinite(seenUnit) && !Double.isInfinite(fuelUnit) ? SEARCH_TICKS : ERRAND_TICKS;
            int haveFuel = available(e.getKey());
            boolean freely = freely(e.getKey());
            int most = limitedMost(e.getKey());
            Map<Integer, Double> bought = new HashMap<>();
            for (int f = 1; f <= Math.max(mostStations, haveStations); f++) {
                var sp = FurnaceSplit.split(items, f, r.cookTicks(), burn, freely);
                int extra = Math.max(0, sp.furnaces() - haveStations);
                double build = extra == 0 ? 0 : !furnace ? Double.POSITIVE_INFINITY
                        : buildCost.computeIfAbsent(extra, n -> furnacesCost(n, path));
                int buy = Math.max(0, sp.fuelTotal() - haveFuel);
                double fuelCost = buy == 0 ? 0 : Double.isInfinite(fuelUnit) || buy > most ? Double.POSITIVE_INFINITY
                        : bought.computeIfAbsent(buy, n -> buyCost(e.getKey(), n, path)) + errand;
                double total = sp.ticks() + build + fuelCost + sp.furnaces() * TEND_TICKS;
                if (best == null || total < best.total() - 1e-9
                        || (Math.abs(total - best.total()) < 1e-9 && sp.furnaces() < best.split().furnaces())) {
                    best = new Cook(r, e.getKey(), sp, total);
                }
                if (sp.furnaces() < f) break; // more stations than pieces of fuel light nothing more
            }
        }
        return best == null || Double.isInfinite(best.total()) ? null : best;
    }

    /**
     * What {@code n} more of an item cost, all told: its cheapest sources first, each one there is only so much of (the
     * sheep ready to shear, the herd it may take from) up to what is left of it, the rest from the next. Sixteen wool
     * from two sheep ready to shear is four shorn, then sheep killed, then wool looked for: not sixteen at the shearing
     * price. Priced as more than it holds (what it holds is not free more).
     */
    private double buyCost(String item, int n, Set<String> path) {
        boolean wasMore = pricingMore;
        pricingMore = true;
        memo.clear();
        try {
            List<double[]> ways = new ArrayList<>(); // {price each, how many}
            for (Source s : book.sources(item)) {
                double u = mineUnit(s, new HashSet<>(path));
                if (Double.isInfinite(u)) continue;
                Integer left = limitedLeft.get(s.block());
                ways.add(new double[] {u, left == null ? Double.POSITIVE_INFINITY : Math.floor(left * s.perBlock())});
            }
            for (Recipe r : book.recipes(item)) {
                double u = recipeUnit(r, new HashSet<>(path));
                if (!Double.isInfinite(u)) ways.add(new double[] {u, Double.POSITIVE_INFINITY});
            }
            ways.sort((a, b) -> Double.compare(a[0], b[0]));
            double cost = 0;
            double left = n;
            for (double[] w : ways) {
                if (left <= 0) break;
                double take = Math.min(left, w[1]);
                cost += take * w[0];
                left -= take;
            }
            return left > 0 ? Double.POSITIVE_INFINITY : cost;
        } finally {
            pricingMore = wasMore;
            memo.clear();
        }
    }

    /** A fuel cheap to have more of than it burns: a bucket of lava (MA7.9). The task splits by the same rule. */
    public static boolean freely(String fuel) {
        return "minecraft:lava_bucket".equals(fuel);
    }

    /**
     * The most of an item it can get when all its sources are ones there is only so much of (the lava pools in sight);
     * unbounded otherwise.
     */
    private int limitedMost(String item) {
        long most = 0;
        for (Source s : book.sources(item)) {
            Integer left = limitedLeft.get(s.block());
            if (left == null) return Integer.MAX_VALUE;
            most += (long) Math.floor(left * s.perBlock());
        }
        if (!book.recipes(item).isEmpty()) return Integer.MAX_VALUE;
        return (int) Math.min(Integer.MAX_VALUE, most);
    }

    private void stationsNotItems(String station, int stations, String order) {
        if (station.equals(order)) return;
        int carriedN = carried.getOrDefault(station, 0);
        if (carriedN > 0) carried.put(station, Math.max(0, carriedN - Math.max(0, stations)));
    }

    /** Nested plans for furnaces' cost, at most this deep. */
    private int nesting;

    /**
     * What {@code n} more furnaces cost from what it has now, all of it (a first pickaxe when it has none, the
     * cobblestone mined for them): a plan of their own, so the price is the job's and not an average per furnace.
     */
    private double furnacesCost(int n, Set<String> path) {
        if (nesting >= 2) {
            memo.clear();
            return n * unit("minecraft:furnace", new HashSet<>(path));
        }
        Have now = new Have(new HashMap<>(carried), new HashMap<>(stored), new ArrayList<>(tools), furnaces, table,
                have.blocks(), have.knownDistance(), smokers, blasts, new HashMap<>(limitedLeft));
        ProductionPlanner sub = new ProductionPlanner(book, now, without);
        sub.nesting = nesting + 1;
        sub.make("minecraft:furnace", n, 0, new HashSet<>(path));
        Plan p = sub.finish("minecraft:furnace", n);
        return p.possible() ? p.ticks() : Double.POSITIVE_INFINITY;
    }

    private void smelt(Recipe r, int items, int depth, Set<String> path) {
        // What goes in first (every way of cooking it takes the same), then the way: the same thing cooked another
        // way it has the station for (beef in its smoker, iron in its blast furnace), whichever costs less for this
        // many, the furnaces it would build counted from what is left.
        Need in = r.needs().get(0);
        String input = pick(in, items * in.count(), path);
        make(input, items * in.count(), depth + 1, path);
        Cook chosen = bestCook(r, items, path);
        for (Recipe alt : book.recipes(r.output())) {
            if (alt == r || !alt.cooked() || alt.kind() == Kind.CAMPFIRE || !alt.needs().equals(r.needs())) continue;
            Cook c = bestCook(alt, items, path);
            if (c != null && (chosen == null || c.total() < chosen.total())) chosen = c;
        }
        if (chosen == null) {
            // Which it lacks: a furnace (none near, none to be made) or the fuel.
            boolean fuel = false;
            for (var e : book.fuelTicks().entrySet()) if (e.getValue() >= SMELT_TICKS / 2 && available(e.getKey()) > 0) fuel = true;
            problems.add(fuel && furnaces == 0 && r.kind() == Kind.SMELT ? "I have no furnace near and can't make one"
                    : "I have no fuel and know no way to get any");
            return;
        }
        r = chosen.recipe();
        int best = chosen.split().furnaces();
        if (r.kind() == Kind.SMELT) {
            int build = Math.max(0, best - furnaces);
            if (build > 0) make("minecraft:furnace", build, depth + 1, path);
            furnaces = Math.max(furnaces, best);
        }
        int fuelCount = chosen.split().fuelTotal();
        make(chosen.fuel(), fuelCount, depth + 1, path);
        steps.add(new Step(Action.SMELT, r.output(), items * Math.max(1, r.outputCount()), r.id(), items, null, 0, null,
                input, chosen.fuel(), fuelCount, best, false));
    }

    /** Of an ingredient's choices, one it has enough of for the whole job, else the cheapest. */
    private String pick(Need n, int total, Set<String> path) {
        for (String alt : n.anyOf()) if (available(alt) >= total) return alt;
        memo.clear();
        String best = n.anyOf().get(0);
        double bestCost = Double.POSITIVE_INFINITY;
        for (String alt : n.anyOf()) {
            double c = unit(alt, new HashSet<>(path));
            if (c < bestCost) {
                bestCost = c;
                best = alt;
            }
        }
        return best;
    }

    private double breakTicks(String block, String item) {
        for (Source s : book.sources(item)) if (s.block().equals(block)) return s.breakTicks();
        return 20;
    }

    private int cookTicks(String recipeId) {
        for (var list : book.recipesByOutput().values()) for (Recipe r : list) if (r.id().equals(recipeId)) return r.cookTicks();
        return SMELT_TICKS;
    }

    /** Steps of the same kind on the same thing, one step; in the order first needed. */
    static List<Step> merge(List<Step> in) {
        Map<String, Step> byKey = new LinkedHashMap<>();
        for (Step s : in) {
            String key = s.action() + "|" + s.item() + "|" + s.recipe() + "|" + s.block() + "|" + s.input() + "|" + s.fuel();
            Step old = byKey.get(key);
            if (old == null) {
                byKey.put(key, s);
            } else {
                byKey.put(key, new Step(s.action(), s.item(), old.count() + s.count(), s.recipe(), old.batches() + s.batches(),
                        s.block(), old.blocks() + s.blocks(), old.tool() != null && !old.tool().isEmpty() ? old.tool() : s.tool(),
                        s.input(), s.fuel(), old.fuelCount() + s.fuelCount(), Math.max(old.furnaces(), s.furnaces()),
                        old.table() || s.table()));
            }
        }
        return new ArrayList<>(byKey.values());
    }

    /** "minecraft:stone_bricks" as a person says it: "stone bricks". */
    public static String name(String id) {
        if (id == null) return "";
        if (Production.shearing(id)) {
            String[] p = id.split(":");
            return (p.length > 3 ? p[3].replace('_', ' ') + " " : "") + (p.length > 2 ? p[2].replace('_', ' ') : "sheep");
        }
        String n = id.substring(id.lastIndexOf(':') + 1).replace('_', ' ');
        return n;
    }

    /** Things counted, said as more than one: "28 sticks", "16 furnaces", "216 coal". */
    static final java.util.Set<String> MASS = java.util.Set.of("coal", "charcoal", "stone", "cobblestone", "dirt", "sand",
            "gravel", "glass", "wool", "iron", "gold", "clay", "netherrack", "deepslate", "cobbled deepslate", "redstone",
            "lapis lazuli", "quartz", "obsidian", "snow", "ice", "bread", "sugar", "paper", "leather", "string", "flint",
            "gunpowder", "bone meal", "wheat", "beef", "pork", "mutton", "chicken", "cod", "salmon", "kelp", "dried kelp");

    public static String plural(String id, int count) {
        String n = name(id);
        if (count == 1) return n;
        if (Production.animal(id) || Production.shearing(id)) return n.endsWith("sheep") || n.endsWith("fish") ? n : n + "s"; // 5 cows, 3 sheep
        String last = n.contains(" ") ? n.substring(n.lastIndexOf(' ') + 1) : n;
        if (MASS.contains(n) || MASS.contains(last) || n.endsWith("s") || n.endsWith("ore") || n.endsWith("dust")) return n;
        if (n.endsWith("ch") || n.endsWith("sh") || n.endsWith("x")) return n + "es";
        return n + "s";
    }
}
