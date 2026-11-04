package com.mineagent.engine.planning.production;

import java.util.List;
import java.util.Map;

/**
 * What the production planner works from (SMH plan 70, "Two orders, planned": "Fill this chest with stone bricks"), as
 * plain data so it is tested without the game: the game's own recipes, what blocks drop with which tool, and what the
 * companion has. {@link GameKnowledge} fills these from the running game; the tests from the vanilla numbers.
 */
public final class Production {
    private Production() {}

    /** A source that is an animal, not a block: "entity:minecraft:cow" (its drops, from its loot table). */
    public static final String ENTITY = "entity:";

    public static boolean animal(String block) {
        return block != null && block.startsWith(ENTITY);
    }

    /**
     * In a plan's sources to leave out: everything it hasn't seen. Once a search round it found none of something, what
     * it has never seen is not near either (no trees here: not oak, nor spruce, nor any of the forty others).
     */
    public static final String UNSEEN = "unseen:*";

    /** A source that is a ripe crop, harvested and planted again: "crop:minecraft:wheat". */
    public static final String CROP = "crop:";

    public static boolean crop(String block) {
        return block != null && block.startsWith(CROP);
    }

    /** A source that is shearing an animal, which it leaves: "shear:minecraft:sheep:white". */
    /** A source that is a fluid scooped up in a bucket: "fluid:minecraft:lava" (a lava pool, for fuel; MA7.9). */
    public static final String FLUID = "fluid:";

    public static boolean fluid(String block) {
        return block != null && block.startsWith(FLUID);
    }

    public static final String SHEAR = "shear:";

    public static boolean shearing(String block) {
        return block != null && block.startsWith(SHEAR);
    }

    /** How a recipe is made: a crafting grid (2 by 2 in the inventory, 3 by 3 at a table), a furnace kind, a cutter. */
    /**
     * TRADE: a villager's offer, "trade:&lt;profession&gt;:&lt;villager&gt;:&lt;offer&gt;", its price the needs, its uses left in
     * {@link Have#limited()} and the walk to it in {@link Have#knownDistance()}, both by the recipe's id.
     */
    public enum Kind { CRAFT, SMELT, BLAST, SMOKE, CAMPFIRE, STONECUT, TRADE }

    /** A trade's recipe id: "trade:fletcher:&lt;uuid&gt;:0". */
    public static final String TRADE = "trade:";

    /** The profession in a trade's id ("fletcher"), or null for another recipe. */
    public static String tradeWith(String recipeId) {
        if (recipeId == null || !recipeId.startsWith(TRADE)) return null;
        String[] p = recipeId.split(":");
        return p.length > 1 ? p[1] : null;
    }

    /**
     * One ingredient of a recipe: {@code count} of any one of {@code anyOf} (a tag such as any planks is several).
     */
    public record Need(List<String> anyOf, int count) {}

    /**
     * A recipe of the game's: {@code outputCount} of {@code output} from its needs, made by {@code kind}; a crafting
     * recipe that does not fit 2 by 2 needs a table; a furnace kind takes {@code cookTicks} per item.
     */
    public record Recipe(String id, Kind kind, String output, int outputCount, List<Need> needs, boolean needsTable,
                         int cookTicks) {
        public boolean cooked() {
            return kind == Kind.SMELT || kind == Kind.BLAST || kind == Kind.SMOKE || kind == Kind.CAMPFIRE;
        }
    }

    /**
     * Mining {@code block} yields {@code perBlock} of {@code item} (on average) with a {@code tool} ("pickaxe", "axe",
     * "shovel", "" for any) of at least {@code tier} (0 hand, 1 wood or gold, 2 stone, 3 iron, 4 diamond, 5
     * netherite), Silk Touch when {@code silkTouch}; {@code breakTicks} with that tier.
     */
    public record Source(String item, String block, String tool, int tier, boolean silkTouch, double perBlock, int breakTicks) {}

    /** Where a block to mine is: next to everything (stone underground), seen nearby (its distance), or not known. */
    public enum Where { COMMON, KNOWN, UNKNOWN }

    /** A tool it has: kind, tier, Silk Touch, uses left. */
    public record Tool(String kind, int tier, boolean silkTouch, int usesLeft) {}

    /**
     * What the companion has and knows: items it carries and items in chests it knows, tools, furnaces it can use
     * (placed nearby or carried), whether a crafting table is at hand, and where each mineable block is.
     */
    /**
     * What it has and knows. {@code limited}: for a source there is only so much of near it, how many it may take (a herd
     * less the two of a kind it leaves; the sheep ready to shear; the lava pools in sight); one not in it is not counted
     * (seen before, elsewhere).
     */
    public record Have(Map<String, Integer> carried, Map<String, Integer> stored, List<Tool> tools, int furnaces,
                       boolean table, Map<String, Where> blocks, Map<String, Integer> knownDistance, int smokers,
                       int blastFurnaces, Map<String, Integer> limited) {
        public Have(Map<String, Integer> carried, Map<String, Integer> stored, List<Tool> tools, int furnaces,
                    boolean table, Map<String, Where> blocks, Map<String, Integer> knownDistance) {
            this(carried, stored, tools, furnaces, table, blocks, knownDistance, 0, 0, Map.of());
        }

        public Have(Map<String, Integer> carried, Map<String, Integer> stored, List<Tool> tools, int furnaces,
                    boolean table, Map<String, Where> blocks, Map<String, Integer> knownDistance, int smokers, int blastFurnaces) {
            this(carried, stored, tools, furnaces, table, blocks, knownDistance, smokers, blastFurnaces, Map.of());
        }

        public int count(String item) {
            return carried.getOrDefault(item, 0) + stored.getOrDefault(item, 0);
        }

        public Where where(String block) {
            return blocks.getOrDefault(block, Where.UNKNOWN);
        }
    }

    /** The game's facts it plans with. */
    public record Book(Map<String, List<Recipe>> recipesByOutput, Map<String, List<Source>> sourcesByItem,
                       Map<String, Integer> fuelTicks, Map<String, Integer> durability) {
        /** The book with these recipes as well (the trades the villagers near offer now). */
        public Book with(List<Recipe> more) {
            if (more == null || more.isEmpty()) return this;
            Map<String, List<Recipe>> all = new java.util.HashMap<>(recipesByOutput);
            for (Recipe r : more) {
                List<Recipe> list = new java.util.ArrayList<>(all.getOrDefault(r.output(), List.of()));
                list.add(r);
                all.put(r.output(), list);
            }
            return new Book(all, sourcesByItem, fuelTicks, durability);
        }

        public List<Recipe> recipes(String item) {
            return recipesByOutput.getOrDefault(item, List.of());
        }

        public List<Source> sources(String item) {
            return sourcesByItem.getOrDefault(item, List.of());
        }
    }
}
