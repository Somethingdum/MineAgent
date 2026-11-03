package com.mineagent.engine.pathing.util;

import java.util.Locale;
import java.util.Set;

/**
 * What breaking a block costs a path (SMH plan 70, MA5.1), in the pathfinder's units (one walked block = 1). Pure.
 *
 * <p>The time comes from the game's own mining formula: progress per tick = tool speed / hardness / 30 when the block
 * drops with that tool, / 100 when it does not, so a block takes ceil(1 / progress) ticks (Minecraft 1.21.1,
 * {@code BlockBehaviour#getDestroyProgress}). A walked block takes about 4.63 ticks (4.317 blocks a second), so a tick of
 * mining is worth about a fifth of a block of walking.
 *
 * <p>A block someone could have built is never broken to get somewhere: a wall, a door, a window, a chest, a floor of
 * planks. Before, every break cost a flat 6, so a two-block hole through a house wall beat any door more than a dozen
 * steps out of the way. Natural blocks carry a penalty on top of their time for the hole they leave.
 */
public final class BreakCost {
    /** Ticks a player takes to walk one block (4.317 blocks a second). */
    public static final double TICKS_PER_WALK = 20.0 / 4.317;
    /** Aiming at the block and taking what drops, in walked blocks. */
    public static final double OVERHEAD = 1.0;
    /** A hole left in the land, in walked blocks: digging should beat only a real detour. */
    public static final double NATURAL_PENALTY = 3.0;
    /** More falling blocks than this stacked above a block, and it is not broken to get somewhere. */
    public static final int MAX_FALLING_STACK = 8;

    /** NATURAL: the land. BUILT: someone made it. AVOID: breaking it does harm (an infested block lets out silverfish). */
    public enum Kind { NATURAL, BUILT, AVOID }

    private BreakCost() {}

    /**
     * Ticks to break a block, by the game's formula. Hardness below 0 (bedrock, barriers) never breaks: {@link
     * Integer#MAX_VALUE}. Hardness 0 breaks at once.
     */
    public static int ticks(double hardness, double toolSpeed, boolean dropsWithTool) {
        if (hardness < 0 || Double.isNaN(hardness)) return Integer.MAX_VALUE;
        if (hardness == 0) return 0;
        double speed = Math.max(toolSpeed, 1.0e-6);
        double progress = speed / hardness / (dropsWithTool ? 30.0 : 100.0);
        if (progress >= 1.0) return 0;
        double ticks = Math.ceil(1.0 / progress);
        return ticks >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) ticks;
    }

    /** The path cost of breaking one block that takes {@code ticks}: infinite for a built block or one that won't break. */
    public static double cost(int ticks, Kind kind) {
        if (kind != Kind.NATURAL || ticks == Integer.MAX_VALUE) return Double.POSITIVE_INFINITY;
        return ticks / TICKS_PER_WALK + OVERHEAD + NATURAL_PENALTY;
    }

    /** Whole names of blocks someone built (no natural block shares them). */
    private static final Set<String> BUILT_NAMES = Set.of(
            "cobblestone", "mossy_cobblestone", "cobbled_deepslate", "bookshelf", "hay_block", "crafting_table",
            "stonecutter", "grindstone", "loom", "composter", "cauldron", "anvil", "chipped_anvil", "damaged_anvil",
            "lantern", "soul_lantern", "chain", "iron_bars", "ladder", "scaffolding", "beehive", "honeycomb_block",
            "slime_block", "honey_block", "dried_kelp_block", "target", "end_rod", "sea_lantern", "jack_o_lantern",
            "iron_block", "gold_block", "diamond_block", "emerald_block", "lapis_block", "redstone_block",
            "coal_block", "netherite_block", "copper_block", "repeater", "comparator", "observer", "piston",
            "sticky_piston", "lever", "tripwire", "tripwire_hook", "note_block", "tnt", "lightning_rod",
            "redstone_wire", "redstone_torch", "redstone_wall_torch", "redstone_lamp", "torch", "wall_torch",
            "soul_torch", "soul_wall_torch", "flower_pot", "packed_mud", "quartz_block", "quartz_pillar",
            "purpur_block", "purpur_pillar", "carved_pumpkin");

    /** Parts of names that only built blocks carry. */
    private static final String[] BUILT_PARTS = {
            "planks", "glass", "wool", "carpet", "brick", "tiles", "concrete", "glazed_terracotta", "_bed",
            "banner", "sign", "candle", "potted_", "_slab", "_stairs", "_wall", "_fence", "_door", "_trapdoor",
            "_button", "_pressure_plate", "rail", "stripped_", "_table", "purpur", "quartz_bricks", "smooth_quartz",
            "chiseled_quartz"};

    /** Name starts that only built blocks carry. */
    private static final String[] BUILT_STARTS = {"polished_", "smooth_", "chiseled_", "cut_", "waxed_"};

    /** Natural blocks a rule above would otherwise catch. */
    private static final Set<String> NATURAL_EXCEPTIONS = Set.of(
            "smooth_basalt", "raw_copper_block", "raw_iron_block", "raw_gold_block", "bee_nest", "moss_carpet");

    /**
     * Whether a block is one someone built, from its registry name (path, without the namespace) and whether it holds a
     * block entity (chests, furnaces, beds, signs and the like all do). Ores, stone, dirt, logs, leaves and the rest of
     * the land are natural. Blocks a player placed are caught by name too only when their kind says so; the placed-block
     * record (MA5.2) catches the rest.
     */
    public static Kind classify(String id, boolean hasBlockEntity) {
        if (hasBlockEntity) return Kind.BUILT;
        if (id == null || id.isBlank()) return Kind.NATURAL;
        String name = id.toLowerCase(Locale.ROOT);
        int colon = name.indexOf(':');
        if (colon >= 0) name = name.substring(colon + 1);
        if (name.startsWith("infested_")) return Kind.AVOID;
        if (NATURAL_EXCEPTIONS.contains(name)) return Kind.NATURAL;
        if (name.endsWith("_ore")) return Kind.NATURAL;
        if (BUILT_NAMES.contains(name)) return Kind.BUILT;
        if (name.endsWith("_wood") || name.endsWith("_hyphae")) return Kind.BUILT; // bark on all sides is crafted
        if (name.contains("copper") && !name.startsWith("raw_")) return Kind.BUILT;
        if (name.startsWith("redstone_")) return Kind.BUILT;
        for (String start : BUILT_STARTS) if (name.startsWith(start)) return Kind.BUILT;
        for (String part : BUILT_PARTS) if (name.contains(part)) return Kind.BUILT;
        return Kind.NATURAL;
    }
}
