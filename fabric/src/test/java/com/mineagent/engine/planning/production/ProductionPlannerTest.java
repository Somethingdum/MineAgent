package com.mineagent.engine.planning.production;

import com.mineagent.engine.planning.production.Production.Book;
import com.mineagent.engine.planning.production.Production.Have;
import com.mineagent.engine.planning.production.Production.Kind;
import com.mineagent.engine.planning.production.Production.Need;
import com.mineagent.engine.planning.production.Production.Recipe;
import com.mineagent.engine.planning.production.Production.Source;
import com.mineagent.engine.planning.production.Production.Tool;
import com.mineagent.engine.planning.production.Production.Where;
import com.mineagent.engine.planning.production.ProductionPlanner.Action;
import com.mineagent.engine.planning.production.ProductionPlanner.Plan;
import com.mineagent.engine.planning.production.ProductionPlanner.Step;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The most efficient way to make things (SMH plan 70; the owner: "If I ask for an entire chest of stone bricks, it
 * should find the most efficient way to do that"), on the game's own numbers for 1.21.1.
 */
final class ProductionPlannerTest {
    static String m(String id) {
        return "minecraft:" + id;
    }

    static final List<String> PLANKS = List.of(m("oak_planks"), m("spruce_planks"));

    /** The vanilla recipes, drops, fuels and durabilities these jobs touch. */
    static Book vanilla() {
        Map<String, List<Recipe>> r = new HashMap<>();
        java.util.function.Consumer<Recipe> add = x -> r.computeIfAbsent(x.output(), k -> new ArrayList<>()).add(x);
        add.accept(new Recipe(m("stone_bricks"), Kind.CRAFT, m("stone_bricks"), 4, List.of(new Need(List.of(m("stone")), 4)), false, 0));
        add.accept(new Recipe(m("stone_bricks_from_stonecutting"), Kind.STONECUT, m("stone_bricks"), 1, List.of(new Need(List.of(m("stone")), 1)), false, 0));
        add.accept(new Recipe(m("stone"), Kind.SMELT, m("stone"), 1, List.of(new Need(List.of(m("cobblestone")), 1)), false, 200));
        add.accept(new Recipe(m("furnace"), Kind.CRAFT, m("furnace"), 1, List.of(new Need(List.of(m("cobblestone")), 8)), true, 0));
        add.accept(new Recipe(m("crafting_table"), Kind.CRAFT, m("crafting_table"), 1, List.of(new Need(PLANKS, 4)), false, 0));
        add.accept(new Recipe(m("oak_planks"), Kind.CRAFT, m("oak_planks"), 4, List.of(new Need(List.of(m("oak_log")), 1)), false, 0));
        add.accept(new Recipe(m("stick"), Kind.CRAFT, m("stick"), 4, List.of(new Need(PLANKS, 2)), false, 0));
        add.accept(new Recipe(m("wooden_pickaxe"), Kind.CRAFT, m("wooden_pickaxe"), 1,
                List.of(new Need(PLANKS, 3), new Need(List.of(m("stick")), 2)), true, 0));
        add.accept(new Recipe(m("stone_pickaxe"), Kind.CRAFT, m("stone_pickaxe"), 1,
                List.of(new Need(List.of(m("cobblestone")), 3), new Need(List.of(m("stick")), 2)), true, 0));
        add.accept(new Recipe(m("torch"), Kind.CRAFT, m("torch"), 4,
                List.of(new Need(List.of(m("coal"), m("charcoal")), 1), new Need(List.of(m("stick")), 1)), false, 0));
        add.accept(new Recipe(m("charcoal"), Kind.SMELT, m("charcoal"), 1, List.of(new Need(List.of(m("oak_log")), 1)), false, 200));
        Map<String, List<Source>> s = new HashMap<>();
        java.util.function.Consumer<Source> src = x -> s.computeIfAbsent(x.item(), k -> new ArrayList<>()).add(x);
        src.accept(new Source(m("cobblestone"), m("stone"), "pickaxe", 1, false, 1.0, 12));
        src.accept(new Source(m("stone"), m("stone"), "pickaxe", 1, true, 1.0, 12));
        src.accept(new Source(m("oak_log"), m("oak_log"), "axe", 0, false, 1.0, 60));
        src.accept(new Source(m("coal"), m("coal_ore"), "pickaxe", 1, false, 1.0, 23));
        Map<String, Integer> fuel = Map.of(m("coal"), 1600, m("charcoal"), 1600, m("oak_log"), 300, m("oak_planks"), 300,
                m("spruce_planks"), 300, m("stick"), 100);
        Map<String, Integer> uses = Map.of(m("wooden_pickaxe"), 59, m("stone_pickaxe"), 131, m("iron_pickaxe"), 250,
                m("diamond_pickaxe"), 1561);
        return new Book(r, s, fuel, uses);
    }

    static Have have(Map<String, Integer> carried, Map<String, Integer> stored, List<Tool> tools, int furnaces, boolean table,
                     Map<String, Where> where) {
        Map<String, Where> w = new HashMap<>(Map.of(m("stone"), Where.COMMON, m("oak_log"), Where.COMMON));
        w.putAll(where);
        return new Have(carried, stored, tools, furnaces, table, w, Map.of());
    }

    static Step only(Plan p, Action a, String item) {
        var list = p.steps().stream().filter(s -> s.action() == a && s.item().equals(item)).toList();
        assertEquals(1, list.size(), a + " " + item + " in " + p.steps());
        return list.get(0);
    }

    @Test void aChestOfStoneBricksTheWayTheGameMakesThem() {
        var p = ProductionPlanner.plan(vanilla(), have(Map.of(m("coal"), 216, m("oak_log"), 16), Map.of(),
                List.of(new Tool("pickaxe", 2, false, 131)), 0, true, Map.of()), m("stone_bricks"), 1_728);
        assertTrue(p.possible(), p.problems().toString());
        var bricks = only(p, Action.CRAFT, m("stone_bricks"));
        assertEquals(432, bricks.batches(), "4 stone for 4 bricks, 432 times");
        var smelt = only(p, Action.SMELT, m("stone"));
        assertEquals(1_728, smelt.count());
        assertEquals(m("cobblestone"), smelt.input());
        assertEquals(m("coal"), smelt.fuel(), "the coal it carries");
        assertEquals(216, smelt.fuelCount(), "8 smelts a coal");
        assertTrue(smelt.furnaces() >= 8 && smelt.furnaces() <= 16, "furnaces worth building: " + smelt.furnaces());
        int mined = p.mined().get(m("stone"));
        int furnaceStone = 8 * p.furnacesBuilt();
        int pickaxes = p.toolsMade().getOrDefault(m("stone_pickaxe"), 0);
        assertEquals(1_728 + furnaceStone + 3 * pickaxes, mined, "bricks, furnaces and the pickaxes it wears out");
        assertTrue(pickaxes >= 13 && pickaxes <= 16, "stone pickaxes, 131 blocks each: " + pickaxes);
        assertEquals(0, p.toolsMade().getOrDefault(m("wooden_pickaxe"), 0).intValue(), "it has a pickaxe to start with");
        assertTrue(p.ticks() > 0);
        assertTrue(p.summary().startsWith("1,728 stone bricks: "), p.summary());
        assertTrue(p.summary().contains("smelt 1,728 cobblestone into stone"), p.summary());
    }

    @Test void withSilkTouchItMinesStoneAndSmeltsNothing() {
        var p = ProductionPlanner.plan(vanilla(), have(Map.of(), Map.of(), List.of(new Tool("pickaxe", 4, true, 4_000)), 0, true,
                Map.of()), m("stone_bricks"), 1_728);
        assertTrue(p.possible(), p.problems().toString());
        assertTrue(p.steps().stream().noneMatch(s -> s.action() == Action.SMELT), "no furnace work: " + p.steps());
        assertEquals(1_728, p.mined().get(m("stone")).intValue());
        assertEquals(0, p.furnacesBuilt());
    }

    @Test void aSilkTouchPickaxeThatWearsOutLeavesTheRestToTheFurnaces() {
        var p = ProductionPlanner.plan(vanilla(), have(Map.of(m("coal"), 100), Map.of(),
                List.of(new Tool("pickaxe", 4, true, 1_000), new Tool("pickaxe", 2, false, 131)), 0, true, Map.of()),
                m("stone_bricks"), 1_728);
        assertTrue(p.possible(), p.problems().toString());
        assertEquals(728, only(p, Action.SMELT, m("stone")).count(), "1,000 mined as stone, the rest smelted");
    }

    @Test void whatIsInItsChestsComesFirst() {
        var p = ProductionPlanner.plan(vanilla(), have(Map.of(m("coal"), 100), Map.of(m("stone_bricks"), 1_000), List.of(new Tool("pickaxe", 2, false, 131)), 0, true,
                Map.of()), m("stone_bricks"), 1_728);
        assertEquals(1_000, only(p, Action.FETCH, m("stone_bricks")).count());
        assertEquals(728, only(p, Action.CRAFT, m("stone_bricks")).count());
    }

    @Test void fromNothingAWoodenPickaxeFirstThenStoneOnes() {
        var p = ProductionPlanner.plan(vanilla(), have(Map.of(), Map.of(), List.of(), 0, false, Map.of()), m("stone_bricks"), 256);
        assertTrue(p.possible(), p.problems().toString());
        assertEquals(1, p.toolsMade().getOrDefault(m("wooden_pickaxe"), 0).intValue(), "one to start: " + p.toolsMade());
        assertTrue(p.toolsMade().getOrDefault(m("stone_pickaxe"), 0) >= 1, "then stone, cheaper a block: " + p.toolsMade());
        assertTrue(p.steps().stream().anyMatch(s -> s.action() == Action.CRAFT && s.item().equals(m("crafting_table"))), "a table");
        var smelt = only(p, Action.SMELT, m("stone"));
        assertNotEquals(m("coal"), smelt.fuel(), "no coal seen: wood it can get");
        // Order: the pickaxe is made before the stone is mined.
        int pick = indexOf(p, Action.CRAFT, m("wooden_pickaxe"));
        int mine = indexOf(p, Action.MINE, m("cobblestone"));
        assertTrue(pick >= 0 && mine > pick, p.steps().toString());
    }

    @Test void torchesFromWhatItCarries() {
        var p = ProductionPlanner.plan(vanilla(), have(Map.of(m("coal"), 16, m("oak_planks"), 8), Map.of(), List.of(), 0, false, Map.of()),
                m("torch"), 64);
        assertTrue(p.possible(), p.problems().toString());
        assertEquals(16, only(p, Action.CRAFT, m("torch")).batches());
        assertEquals(16, only(p, Action.CRAFT, m("stick")).count());
        assertTrue(p.mined().isEmpty(), "nothing to mine: " + p.mined());
    }

    @Test void somethingItCannotGetSaysWhy() {
        var p = ProductionPlanner.plan(vanilla(), have(Map.of(), Map.of(), List.of(), 0, false, Map.of()), m("diamond"), 3);
        assertFalse(p.possible());
        assertTrue(p.summary().startsWith("I can't see a way to make 3 diamond"), p.summary());
    }

    @Test void whatItAlreadyCarriesIsEnough() {
        var p = ProductionPlanner.plan(vanilla(), have(Map.of(m("stone_bricks"), 64), Map.of(), List.of(), 0, false, Map.of()),
                m("stone_bricks"), 64);
        assertTrue(p.steps().isEmpty(), p.steps().toString());
        assertTrue(p.possible());
    }

    static int indexOf(Plan p, Action a, String item) {
        for (int i = 0; i < p.steps().size(); i++) {
            if (p.steps().get(i).action() == a && p.steps().get(i).item().equals(item)) return i;
        }
        return -1;
    }
}
