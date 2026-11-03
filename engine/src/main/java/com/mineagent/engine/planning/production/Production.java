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
    public static final String SHEAR = "shear:";

    public static boolean shearing(String block) {
        return block != null && block.startsWith(SHEAR);
    }

    /** How a recipe is made: a crafting grid (2 by 2 in the inventory, 3 by 3 at a table), a furnace kind, a cutter. */
    public enum Kind { CRAFT, SMELT, BLAST, SMOKE, CAMPFIRE, STONECUT }

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
    public record Have(Map<String, Integer> carried, Map<String, Integer> stored, List<Tool> tools, int furnaces,
                       boolean table, Map<String, Where> blocks, Map<String, Integer> knownDistance, int smokers,
                       int blastFurnaces) {
        public Have(Map<String, Integer> carried, Map<String, Integer> stored, List<Tool> tools, int furnaces,
                    boolean table, Map<String, Where> blocks, Map<String, Integer> knownDistance) {
            this(carried, stored, tools, furnaces, table, blocks, knownDistance, 0, 0);
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
        public List<Recipe> recipes(String item) {
            return recipesByOutput.getOrDefault(item, List.of());
        }

        public List<Source> sources(String item) {
            return sourcesByItem.getOrDefault(item, List.of());
        }
    }
}
