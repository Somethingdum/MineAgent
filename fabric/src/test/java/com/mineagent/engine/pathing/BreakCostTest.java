package com.mineagent.engine.pathing;

import com.mineagent.engine.pathing.util.BreakCost;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Breaking costs a path what it costs a player (SMH plan 70, MA5.1), and nothing built is broken to get somewhere. */
final class BreakCostTest {
    // Tool speeds in 1.21.1: hand 1, wood 2, stone 4, iron 6, diamond 8.
    @Test void miningTimesAreTheGames() {
        assertEquals(150, BreakCost.ticks(1.5, 1, false));  // stone by hand: 7.5 s
        assertEquals(23, BreakCost.ticks(1.5, 2, true));    // stone, wooden pickaxe: 1.15 s
        assertEquals(12, BreakCost.ticks(1.5, 4, true));    // stone, stone pickaxe: 0.6 s
        assertEquals(8, BreakCost.ticks(1.5, 6, true));     // stone, iron pickaxe: 0.4 s
        assertEquals(15, BreakCost.ticks(0.5, 1, true));    // dirt by hand: 0.75 s
        assertEquals(188, BreakCost.ticks(50, 8, true));    // obsidian, diamond pickaxe: 9.4 s
        assertEquals(0, BreakCost.ticks(0, 1, true));       // a torch or tall grass: at once
        assertEquals(Integer.MAX_VALUE, BreakCost.ticks(-1, 8, true)); // bedrock never
    }

    @Test void aNaturalBlockCostsItsTimeAndTheHole() {
        double stoneIron = BreakCost.cost(8, BreakCost.Kind.NATURAL);
        double stoneHand = BreakCost.cost(150, BreakCost.Kind.NATURAL);
        assertTrue(stoneIron > BreakCost.OVERHEAD + BreakCost.NATURAL_PENALTY);
        assertTrue(stoneHand > 30, "stone by hand costs more than 30 blocks of walking");
        assertTrue(stoneIron < stoneHand);
    }

    @Test void nothingBuiltOrHarmfulIsBrokenToGetSomewhere() {
        assertEquals(Double.POSITIVE_INFINITY, BreakCost.cost(1, BreakCost.Kind.BUILT));
        assertEquals(Double.POSITIVE_INFINITY, BreakCost.cost(1, BreakCost.Kind.AVOID));
        assertEquals(Double.POSITIVE_INFINITY, BreakCost.cost(Integer.MAX_VALUE, BreakCost.Kind.NATURAL));
    }

    @Test void builtBlocksAreKnownByName() {
        for (String built : new String[] {"minecraft:oak_planks", "glass", "white_stained_glass_pane", "stone_bricks",
                "cobblestone", "cobblestone_wall", "oak_door", "iron_door", "oak_fence", "spruce_trapdoor", "red_wool",
                "white_carpet", "red_bed", "polished_andesite", "smooth_stone", "cut_sandstone", "bricks",
                "deepslate_tiles", "white_concrete", "stripped_oak_log", "oak_wood", "crafting_table", "bookshelf",
                "iron_block", "cut_copper", "waxed_copper_block", "quartz_block", "torch", "rail", "stone_slab",
                "oak_stairs", "lantern"}) {
            assertEquals(BreakCost.Kind.BUILT, BreakCost.classify(built, false), built);
        }
    }

    @Test void theLandIsNatural() {
        for (String natural : new String[] {"minecraft:stone", "deepslate", "dirt", "grass_block", "sand", "gravel",
                "oak_log", "oak_leaves", "iron_ore", "deepslate_diamond_ore", "copper_ore", "redstone_ore",
                "nether_quartz_ore", "netherrack", "andesite", "granite", "tuff", "calcite", "smooth_basalt",
                "raw_iron_block", "terracotta", "red_sandstone", "sandstone", "snow_block", "ice", "clay",
                "obsidian", "bone_block", "glowstone", "bee_nest", "moss_carpet", "amethyst_block"}) {
            assertEquals(BreakCost.Kind.NATURAL, BreakCost.classify(natural, false), natural);
        }
    }

    @Test void foodThatGrowsBackIsNeverBrokenToGetSomewhere() {
        for (String food : new String[] {"minecraft:sweet_berry_bush", "wheat", "carrots", "potatoes", "beetroots",
                "melon_stem", "pumpkin_stem", "cocoa", "nether_wart", "cave_vines", "farmland"}) {
            assertEquals(BreakCost.Kind.AVOID, BreakCost.classify(food, false), food);
        }
        // The fruit itself is land like any other: a melon or a pumpkin is taken whole.
        assertEquals(BreakCost.Kind.NATURAL, BreakCost.classify("melon", false));
        assertEquals(BreakCost.Kind.NATURAL, BreakCost.classify("pumpkin", false));
    }

    @Test void blocksThatHoldThingsAndInfestedStoneAreLeftAlone() {
        assertEquals(BreakCost.Kind.BUILT, BreakCost.classify("chest", true));
        assertEquals(BreakCost.Kind.BUILT, BreakCost.classify("furnace", true));
        assertEquals(BreakCost.Kind.AVOID, BreakCost.classify("infested_stone", false));
    }
}
