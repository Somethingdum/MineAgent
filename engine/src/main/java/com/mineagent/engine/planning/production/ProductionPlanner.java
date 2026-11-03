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
    /** Ticks a craft takes (a player's click), and fetching one stack from a chest it knows. */
    static final double CRAFT_TICKS = 4.0;
    static final double FETCH_PER_STACK = 20.0;
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

    /** The job, in order. */
    private final List<Step> steps = new ArrayList<>();
    private final Map<String, Integer> toolsMade = new LinkedHashMap<>();
    private final List<String> problems = new ArrayList<>();

    public enum Action { FETCH, MINE, CRAFT, SMELT }

    /**
     * One step of the job: FETCH {@code count} of {@code item} from its chests; MINE {@code blocks} of {@code block}
     * for {@code count} of {@code item} with {@code tool}; CRAFT {@code batches} of {@code recipe} for {@code count}
     * of {@code item}; SMELT {@code count} of {@code input} into {@code item} in {@code furnaces} furnaces burning
     * {@code fuelCount} {@code fuel}.
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
            if (!possible()) return "I can't see a way to make " + count + " " + name(item) + ": " + String.join("; ", problems) + ".";
            List<String> parts = new ArrayList<>();
            for (Step s : steps) {
                switch (s.action()) {
                    case FETCH -> parts.add(String.format(Locale.ROOT, "%,d %s from my chests", s.count(), plural(s.item(), s.count())));
                    case MINE -> parts.add(String.format(Locale.ROOT, "mine %,d %s", s.blocks(), plural(s.block(), s.blocks())));
                    case SMELT -> parts.add(String.format(Locale.ROOT, "smelt %,d %s into %s in %d %s with %,d %s",
                            s.count(), plural(s.input(), s.count()), name(s.item()), s.furnaces(), s.furnaces() == 1 ? "furnace" : "furnaces",
                            s.fuelCount(), plural(s.fuel(), s.fuelCount())));
                    case CRAFT -> {
                        if (!s.item().equals(item)) parts.add(String.format(Locale.ROOT, "craft %,d %s", s.count(), plural(s.item(), s.count())));
                    }
                }
            }
            String head = String.format(Locale.ROOT, "%,d %s", count, plural(item, count));
            String minutes = ticks < 1_200 ? "under a minute" : String.format(Locale.ROOT, "about %d minutes", Math.round(ticks / 1_200.0));
            return head + ": " + String.join(", ", parts) + "; " + minutes + ".";
        }
    }

    private ProductionPlanner(Book book, Have have) {
        this.book = book;
        this.have = have;
        carried.putAll(have.carried());
        stored.putAll(have.stored());
        tools.addAll(have.tools());
        furnaces = have.furnaces();
        table = have.table();
    }

    /** The cheapest way to have {@code count} of {@code item}, start to finish. */
    public static Plan plan(Book book, Have have, String item, int count) {
        ProductionPlanner p = new ProductionPlanner(book, have);
        if (count > 0) p.make(item, count, 0, new HashSet<>());
        return p.finish(item, count);
    }

    private Plan finish(String item, int count) {
        List<Step> merged = merge(steps);
        Map<String, Integer> mined = new LinkedHashMap<>();
        Map<String, Integer> fuel = new LinkedHashMap<>();
        double ticks = 0;
        int built = 0;
        for (Step s : merged) {
            switch (s.action()) {
                case FETCH -> ticks += FETCH_PER_STACK * Math.ceil(s.count() / 64.0);
                case MINE -> {
                    mined.merge(s.block(), s.blocks(), Integer::sum);
                    ticks += s.blocks() * (breakTicks(s.block(), s.item()) + MINE_WALK);
                }
                case CRAFT -> {
                    ticks += s.batches() * CRAFT_TICKS;
                    if (s.item().equals("minecraft:furnace")) built += s.count();
                }
                case SMELT -> {
                    fuel.merge(s.fuel(), s.fuelCount(), Integer::sum);
                    ticks += Math.ceil(s.count() / (double) Math.max(1, s.furnaces())) * cookTicks(s.recipe());
                }
            }
        }
        return new Plan(item, count, merged, mined, new LinkedHashMap<>(toolsMade), built, fuel, ticks, List.copyOf(problems));
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
        path.remove(item);
        memo.put(item, best);
        return best;
    }

    /** Items whose Silk Touch tool is worn out for this job. */
    private final Set<String> noSilk = new HashSet<>();

    private double mineUnit(Source s, Set<String> path) {
        if (s.perBlock() <= 0 || (s.silkTouch() && noSilk.contains(s.item()))) return Double.POSITIVE_INFINITY;
        double wear = toolWear(s, path);
        if (Double.isInfinite(wear)) return wear;
        Where where = have.where(s.block());
        double reach = where == Where.COMMON ? 0 : where == Where.KNOWN
                ? WALK_PER_BLOCK * have.knownDistance().getOrDefault(s.block(), 32) / 64.0 : UNSEEN;
        return (s.breakTicks() + MINE_WALK + wear + reach) / s.perBlock();
    }

    /** Its share of the tool a block wears out: none by hand; the cheapest tool per use otherwise. */
    private double toolWear(Source s, Set<String> path) {
        if (s.tool().isEmpty() || (s.tier() == 0 && !s.silkTouch())) return 0;
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
        if (r.kind() == Kind.STONECUT || r.kind() == Kind.CAMPFIRE) return Double.POSITIVE_INFINITY; // stations it doesn't run yet
        if ((r.kind() == Kind.BLAST || r.kind() == Kind.SMOKE)) return Double.POSITIVE_INFINITY; // a furnace does those too
        double in = 0;
        for (Need n : r.needs()) {
            double cheapest = Double.POSITIVE_INFINITY;
            for (String alt : n.anyOf()) cheapest = Math.min(cheapest, unit(alt, path));
            in += cheapest * n.count();
            if (Double.isInfinite(in)) return in;
        }
        double work = r.cooked() ? r.cookTicks() / TYPICAL_FURNACES + fuelPerSmelt(path) * r.cookTicks() / SMELT_TICKS : CRAFT_TICKS;
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
            if (!t.endsWith("_" + kind)) continue;
            if (tierOf(t) >= Math.max(1, tier) && !book.recipes(t).isEmpty()) out.add(t);
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
            double c = recipeUnit(r, p);
            if (c < best) {
                best = c;
                bestRecipe = r;
                bestSource = null;
            }
        }
        if (Double.isInfinite(best)) {
            problems.add("I know no way to get " + name(item));
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
        if (bestSource != null) mine(bestSource, left, depth, p);
        else craft(bestRecipe, left, depth, p);
    }

    private int available(String item) {
        return Math.max(0, carried.getOrDefault(item, 0)) + Math.max(0, stored.getOrDefault(item, 0));
    }

    private void mine(Source s, int count, int depth, Set<String> path) {
        int blocks = (int) Math.ceil(count / s.perBlock());
        String toolName = "";
        int extra = 0;
        if (!s.tool().isEmpty() && (s.tier() > 0 || s.silkTouch())) {
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
        steps.add(new Step(Action.MINE, s.item(), count, null, 0, s.block(), blocks, toolName, null, null, 0, 0, false));
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

    private void smelt(Recipe r, int items, int depth, Set<String> path) {
        Need in = r.needs().get(0);
        String input = pick(in, items * in.count(), path);
        make(input, items * in.count(), depth + 1, path);
        // Furnaces and fuel together: each furnace takes whole pieces of fuel's worth (a coal, 8 items), so the
        // furnaces worth lighting depend on the fuel; the cheapest pair of the time saved, the furnaces built and the
        // fuel not already had.
        memo.clear();
        double furnaceCost = unit("minecraft:furnace", new HashSet<>(path));
        String fuel = null;
        FurnaceSplit.Split chosen = null;
        double bestTotal = Double.POSITIVE_INFINITY;
        for (var e : book.fuelTicks().entrySet()) {
            if (e.getValue() < SMELT_TICKS / 2) continue;
            memo.clear();
            double fuelUnit = unit(e.getKey(), new HashSet<>(path));
            int haveFuel = available(e.getKey());
            for (int f = Math.max(1, furnaces); f <= MAX_FURNACES; f++) {
                var sp = FurnaceSplit.split(items, f, r.cookTicks(), e.getValue());
                int extra = Math.max(0, sp.furnaces() - furnaces);
                double build = extra == 0 ? 0 : Double.isInfinite(furnaceCost) ? Double.POSITIVE_INFINITY : extra * furnaceCost;
                int buy = Math.max(0, sp.fuelTotal() - haveFuel);
                double fuelCost = buy == 0 ? 0 : buy * fuelUnit;
                double total = sp.ticks() + build + fuelCost;
                if (total < bestTotal - 1e-9 || (Math.abs(total - bestTotal) < 1e-9 && chosen != null && sp.furnaces() < chosen.furnaces())) {
                    bestTotal = total;
                    fuel = e.getKey();
                    chosen = sp;
                }
                if (sp.furnaces() < f) break; // more furnaces than pieces of fuel light nothing more
            }
        }
        if (fuel == null || chosen == null || Double.isInfinite(bestTotal)) {
            problems.add("I have no fuel and know no way to get any");
            return;
        }
        int best = chosen.furnaces();
        int build = Math.max(0, best - furnaces);
        if (build > 0) make("minecraft:furnace", build, depth + 1, path);
        furnaces = Math.max(furnaces, best);
        int fuelCount = chosen.fuelTotal();
        make(fuel, fuelCount, depth + 1, path);
        steps.add(new Step(Action.SMELT, r.output(), items * Math.max(1, r.outputCount()), r.id(), items, null, 0, null,
                input, fuel, fuelCount, best, false));
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
        String n = id.substring(id.indexOf(':') + 1).replace('_', ' ');
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
        String last = n.contains(" ") ? n.substring(n.lastIndexOf(' ') + 1) : n;
        if (MASS.contains(n) || MASS.contains(last) || n.endsWith("s") || n.endsWith("ore") || n.endsWith("dust")) return n;
        if (n.endsWith("ch") || n.endsWith("sh") || n.endsWith("x")) return n + "es";
        return n + "s";
    }
}
