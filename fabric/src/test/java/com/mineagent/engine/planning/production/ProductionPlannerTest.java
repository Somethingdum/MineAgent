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
        add.accept(new Recipe(m("cooked_beef"), Kind.SMELT, m("cooked_beef"), 1, List.of(new Need(List.of(m("beef")), 1)), false, 200));
        add.accept(new Recipe(m("cooked_beef_from_smoking"), Kind.SMOKE, m("cooked_beef"), 1, List.of(new Need(List.of(m("beef")), 1)), false, 100));
        add.accept(new Recipe(m("cooked_beef_from_campfire_cooking"), Kind.CAMPFIRE, m("cooked_beef"), 1, List.of(new Need(List.of(m("beef")), 1)), false, 600));
        add.accept(new Recipe(m("iron_ingot_from_smelting_raw_iron"), Kind.SMELT, m("iron_ingot"), 1, List.of(new Need(List.of(m("raw_iron")), 1)), false, 200));
        add.accept(new Recipe(m("iron_ingot_from_blasting_raw_iron"), Kind.BLAST, m("iron_ingot"), 1, List.of(new Need(List.of(m("raw_iron")), 1)), false, 100));
        add.accept(new Recipe(m("shears"), Kind.CRAFT, m("shears"), 1, List.of(new Need(List.of(m("iron_ingot")), 2)), false, 0));
        add.accept(new Recipe(m("white_bed"), Kind.CRAFT, m("white_bed"), 1,
                List.of(new Need(List.of(m("white_wool")), 3), new Need(PLANKS, 3)), true, 0));
        add.accept(new Recipe(m("flint_and_steel"), Kind.CRAFT, m("flint_and_steel"), 1,
                List.of(new Need(List.of(m("iron_ingot")), 1), new Need(List.of(m("flint")), 1)), false, 0));
        add.accept(new Recipe(m("iron_pickaxe"), Kind.CRAFT, m("iron_pickaxe"), 1,
                List.of(new Need(List.of(m("iron_ingot")), 3), new Need(List.of(m("stick")), 2)), true, 0));
        Map<String, List<Source>> s = new HashMap<>();
        java.util.function.Consumer<Source> src = x -> s.computeIfAbsent(x.item(), k -> new ArrayList<>()).add(x);
        src.accept(new Source(m("cobblestone"), m("stone"), "pickaxe", 1, false, 1.0, 12));
        src.accept(new Source(m("stone"), m("stone"), "pickaxe", 1, true, 1.0, 12));
        src.accept(new Source(m("oak_log"), m("oak_log"), "", 0, false, 1.0, 60)); // by hand, as the game's table says
        src.accept(new Source(m("coal"), m("coal_ore"), "pickaxe", 1, false, 1.0, 23));
        src.accept(new Source(m("raw_iron"), m("iron_ore"), "pickaxe", 2, false, 1.0, 23));
        src.accept(new Source(m("diamond"), m("diamond_ore"), "pickaxe", 3, false, 1.0, 23));
        // A cow, from its loot table: 1-3 beef (2 on average), 0-2 leather; a kill priced by its health.
        src.accept(new Source(m("beef"), "entity:minecraft:cow", "", 0, false, 2.0, 120));
        src.accept(new Source(m("leather"), "entity:minecraft:cow", "", 0, false, 1.0, 120));
        // Shearing a white sheep: 1 to 3 wool; it lives. Killing one: 1 wool and mutton.
        src.accept(new Source(m("white_wool"), "shear:minecraft:sheep:white", "shears", 0, false, 2.0, 30));
        src.accept(new Source(m("white_wool"), "entity:minecraft:sheep", "", 0, false, 1.0, 100));
        // Gravel: flint one time in ten, gravel otherwise (measured over 64 breaks).
        src.accept(new Source(m("flint"), m("gravel"), "", 0, false, 0.1, 18));
        src.accept(new Source(m("gravel"), m("gravel"), "", 0, false, 0.9, 18));
        Map<String, Integer> fuel = Map.of(m("coal"), 1600, m("charcoal"), 1600, m("oak_log"), 300, m("oak_planks"), 300,
                m("spruce_planks"), 300, m("stick"), 100);
        Map<String, Integer> uses = Map.of(m("wooden_pickaxe"), 59, m("stone_pickaxe"), 131, m("iron_pickaxe"), 250,
                m("diamond_pickaxe"), 1561, m("shears"), 238);
        return new Book(r, s, fuel, uses);
    }

    static Have have(Map<String, Integer> carried, Map<String, Integer> stored, List<Tool> tools, int furnaces, boolean table,
                     Map<String, Where> where) {
        Map<String, Where> w = new HashMap<>(Map.of(m("stone"), Where.COMMON, m("oak_log"), Where.COMMON));
        w.putAll(where);
        return new Have(carried, stored, tools, furnaces, table, w, Map.of());
    }

    static Have have(Map<String, Integer> carried, List<Tool> tools, int furnaces, int smokers, int blasts, Map<String, Where> where,
                     Map<String, Integer> distance) {
        Map<String, Where> w = new HashMap<>(Map.of(m("stone"), Where.COMMON, m("oak_log"), Where.COMMON));
        w.putAll(where);
        return new Have(carried, Map.of(), tools, furnaces, true, w, distance, smokers, blasts);
    }

    static final String COW = "entity:minecraft:cow";

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

    @Test void withNoTreesItCooksWithTheCoalItCarries() {
        // Two planks in hand price no more planks: with the trees ruled out (none near), the coal does it.
        Map<String, Where> w = new HashMap<>(Map.of(m("stone"), Where.COMMON, m("oak_log"), Where.COMMON));
        var have = new Have(Map.of(m("coal"), 8, m("oak_planks"), 2, m("beef"), 8), Map.of(), List.of(), 3, true, w, Map.of(), 0, 0);
        var p = ProductionPlanner.plan(vanilla(), have, m("cooked_beef"), 8, java.util.Set.of(m("oak_log"), Production.UNSEEN));
        assertTrue(p.possible(), p.summary());
        assertEquals(m("coal"), only(p, Action.SMELT, m("cooked_beef")).fuel(), p.summary());
    }

    @Test void noTreeSeenItBurnsTheCoalItHasRatherThanGoLookingForOne() {
        // A treeless field: planks for three furnaces would be quicker cooking, but only after a search for a tree.
        Map<String, Where> w = new HashMap<>(Map.of(m("stone"), Where.COMMON));
        var have = new Have(Map.of(m("coal"), 8, m("oak_planks"), 2, m("beef"), 8), Map.of(), List.of(), 3, true, w, Map.of(), 0, 0);
        var p = ProductionPlanner.plan(vanilla(), have, m("cooked_beef"), 8);
        assertTrue(p.possible(), p.summary());
        assertEquals(m("coal"), only(p, Action.SMELT, m("cooked_beef")).fuel(), p.summary());
        assertTrue(p.mined().isEmpty(), "nothing to go and find: " + p.mined());
        // With trees in sight, the planks' speed may win: that's a choice, not a search.
    }

    @Test void somethingItCannotGetSaysWhy() {
        var p = ProductionPlanner.plan(vanilla(), have(Map.of(), Map.of(), List.of(), 0, false, Map.of()), m("netherite_ingot"), 3);
        assertFalse(p.possible());
        assertTrue(p.summary().startsWith("I can't see a way to make 3 netherite ingots: I know no way to get netherite ingot"), p.summary());
        // A level down: what the recipe lacks, when it has searched and seen none.
        var torches = ProductionPlanner.plan(vanilla(), have(Map.of(), Map.of(), List.of(), 0, false, Map.of()), m("torch"), 16,
                java.util.Set.of(Production.UNSEEN, m("oak_log")));
        assertFalse(torches.possible());
        assertTrue(torches.summary().contains("torches needs coal or charcoal"), torches.summary());
    }

    @Test void whatItAlreadyCarriesIsEnough() {
        var p = ProductionPlanner.plan(vanilla(), have(Map.of(m("stone_bricks"), 64), Map.of(), List.of(), 0, false, Map.of()),
                m("stone_bricks"), 64);
        assertTrue(p.steps().isEmpty(), p.steps().toString());
        assertTrue(p.possible());
    }

    // ── Hunting and cooking (MA7.1, MA7.5) ──────────────────────────

    @Test void foodFromTheCowsNearCookedInFurnacesItBuilds() {
        var p = ProductionPlanner.plan(vanilla(), have(Map.of(m("coal"), 4), List.of(new Tool("pickaxe", 2, false, 131)), 0, 0, 0,
                Map.of(COW, Where.KNOWN), Map.of(COW, 20)), m("cooked_beef"), 16);
        assertTrue(p.possible(), p.problems().toString());
        var hunt = only(p, Action.HUNT, m("beef"));
        assertEquals(COW, hunt.block());
        assertEquals(8, hunt.blocks(), "2 beef a cow on average");
        var cook = only(p, Action.SMELT, m("cooked_beef"));
        assertEquals(m("cooked_beef"), cook.recipe(), "no smoker: a furnace");
        assertEquals(m("coal"), cook.fuel());
        assertEquals(2, cook.fuelCount(), "a coal cooks 8");
        assertTrue(p.summary().contains("hunt 8 cows"), p.summary());
    }

    @Test void aSmokerItHasCooksASmallBatch() {
        var p = ProductionPlanner.plan(vanilla(), have(Map.of(m("coal"), 4, m("beef"), 16), List.of(), 0, 1, 0, Map.of(), Map.of()),
                m("cooked_beef"), 16);
        assertTrue(p.possible(), p.problems().toString());
        var cook = only(p, Action.SMELT, m("cooked_beef"));
        assertEquals(m("cooked_beef_from_smoking"), cook.recipe(), "its smoker, twice as quick, and no furnace to build");
        assertEquals(1, cook.furnaces());
        assertEquals(2, cook.fuelCount(), "a smoker burns a coal in 8 too");
        assertEquals(0, p.furnacesBuilt());
        assertTrue(p.summary().contains("cook 16 beef into cooked beef in 1 smoker"), p.summary());
    }

    @Test void aBigBatchIsWorthFurnacesBesideTheSmoker() {
        var p = ProductionPlanner.plan(vanilla(), have(Map.of(m("coal"), 64, m("beef"), 512), List.of(new Tool("pickaxe", 2, false, 131)),
                0, 1, 0, Map.of(), Map.of()), m("cooked_beef"), 512);
        assertTrue(p.possible(), p.problems().toString());
        var cook = only(p, Action.SMELT, m("cooked_beef"));
        assertEquals(m("cooked_beef"), cook.recipe(), "one smoker would take 51,200 ticks: furnaces are quicker");
        assertTrue(cook.furnaces() > 1 && p.furnacesBuilt() > 0, p.summary());
    }

    @Test void aBlastFurnaceItHasSmeltsTheOre() {
        var p = ProductionPlanner.plan(vanilla(), have(Map.of(m("coal"), 1, m("raw_iron"), 8), List.of(), 0, 0, 1, Map.of(), Map.of()),
                m("iron_ingot"), 8);
        assertTrue(p.possible(), p.problems().toString());
        var smelt = only(p, Action.SMELT, m("iron_ingot"));
        assertEquals(m("iron_ingot_from_blasting_raw_iron"), smelt.recipe());
        assertEquals(1, smelt.fuelCount(), "8 raw iron, one coal");
    }

    @Test void noCowsLeftNearItCooksWhatItCarries() {
        var book = vanilla();
        var h = have(Map.of(m("coal"), 4, m("beef"), 6), List.of(), 1, 0, 0, Map.of(COW, Where.KNOWN), Map.of(COW, 10));
        var without = java.util.Set.of(COW);
        assertFalse(ProductionPlanner.plan(book, h, m("cooked_beef"), 16, without).possible(), "no cows: no more beef");
        assertEquals(6, ProductionPlanner.most(book, h, m("cooked_beef"), 16, without), "the 6 beef it has");
        assertEquals(16, ProductionPlanner.most(book, h, m("cooked_beef"), 16, java.util.Set.of()), "with the cows, all of it");
        assertEquals(0, ProductionPlanner.most(book, have(Map.of(), List.of(), 1, 0, 0, Map.of(), Map.of()), m("cooked_beef"), 16, without));
        // Cows it has never seen, after a search round it found none of anything: not near either.
        var none = have(Map.of(m("coal"), 4), List.of(), 1, 0, 0, Map.of(), Map.of());
        assertTrue(ProductionPlanner.plan(book, none, m("cooked_beef"), 4).possible(), "unseen cows, priced as a search");
        assertFalse(ProductionPlanner.plan(book, none, m("cooked_beef"), 4, java.util.Set.of(Production.UNSEEN)).possible());
    }

    // ── The catalogue (MA7.3) ───────────────────────────────────────

    static final String WHITE_SHEEP = "shear:minecraft:sheep:white";

    @Test void aBedFromShornWoolTheSheepLeftAlive() {
        var p = ProductionPlanner.plan(vanilla(), have(Map.of(m("oak_planks"), 3), List.of(new Tool("shears", 0, false, 200)), 0, 0, 0,
                Map.of(WHITE_SHEEP, Where.KNOWN, "entity:minecraft:sheep", Where.KNOWN), Map.of(WHITE_SHEEP, 12, "entity:minecraft:sheep", 12)),
                m("white_bed"), 1);
        assertTrue(p.possible(), p.problems().toString());
        var shear = only(p, Action.SHEAR, m("white_wool"));
        assertEquals(2, shear.blocks(), "2 wool a sheep on average: 2 sheep for 3");
        assertTrue(p.steps().stream().noneMatch(s -> s.action() == Action.HUNT), "shorn, not killed: " + p.steps());
        assertTrue(p.summary().contains("shear 2 white sheep"), p.summary());
    }

    @Test void itShearsNoMoreSheepThanAreReadyAndKillsNoMoreThanTheHerdSpares() {
        // A bed's 3 wool: one sheep ready to shear (2 wool), and a herd of 3 sheep of which it may take one (the two left).
        Map<String, Where> w = new HashMap<>(Map.of(m("stone"), Where.COMMON, m("oak_log"), Where.COMMON,
                WHITE_SHEEP, Where.KNOWN, "entity:minecraft:sheep", Where.KNOWN));
        var have = new Have(Map.of(m("oak_planks"), 3), Map.of(), List.of(new Tool("shears", 0, false, 200)), 0, true, w,
                Map.of(WHITE_SHEEP, 12, "entity:minecraft:sheep", 12), 0, 0, Map.of(WHITE_SHEEP, 1, "entity:minecraft:sheep", 1));
        var p = ProductionPlanner.plan(vanilla(), have, m("white_bed"), 1);
        assertTrue(p.possible(), p.problems().toString());
        assertEquals(1, only(p, Action.SHEAR, m("white_wool")).blocks(), "the one sheep ready: " + p.summary());
        assertEquals(1, only(p, Action.HUNT, m("white_wool")).blocks(), "the rest from the one it may take: " + p.summary());
    }

    @Test void moreThanTheHerdCanGiveIsNotPlannedAsIfItCould() {
        // 8 beef, 2 a cow, and only one cow it may take: not "hunt 4 cows".
        Map<String, Where> w = new HashMap<>(Map.of(m("stone"), Where.COMMON, m("oak_log"), Where.COMMON, COW, Where.KNOWN));
        var have = new Have(Map.of(), Map.of(), List.of(), 0, true, w, Map.of(COW, 10), 0, 0, Map.of(COW, 1));
        var p = ProductionPlanner.plan(vanilla(), have, m("beef"), 8);
        assertTrue(p.steps().stream().filter(s -> s.action() == Action.HUNT).allMatch(s -> s.blocks() <= 1), p.steps().toString());
        assertFalse(p.possible(), p.summary());
        assertTrue(p.problems().toString().contains("enough cows near for 6 more beef"), p.problems().toString());
    }

    // ── Lava for fuel (MA7.9) ───────────────────────────────────────

    static final String LAVA = "fluid:minecraft:lava";

    static Book withLava() {
        Book b = vanilla();
        Map<String, List<Source>> s = new HashMap<>(b.sourcesByItem());
        s.put(m("lava_bucket"), List.of(new Source(m("lava_bucket"), LAVA, "bucket", 0, false, 1.0, 20)));
        Map<String, Integer> fuel = new HashMap<>(b.fuelTicks());
        fuel.put(m("lava_bucket"), 20_000);
        Map<String, Integer> uses = new HashMap<>(b.durability());
        uses.put(m("bucket"), 10_000);
        return new Book(b.recipesByOutput(), s, fuel, uses);
    }

    static Have lavaHave(int pools, boolean seen, Map<String, Integer> carried) {
        Map<String, Where> w = new HashMap<>(Map.of(m("stone"), Where.COMMON, m("oak_log"), Where.COMMON));
        if (seen) w.put(LAVA, Where.KNOWN);
        return new Have(carried, Map.of(), List.of(new Tool("pickaxe", 1, false, 131), new Tool("bucket", 0, false, 10_000)), 2, true,
                w, Map.of(LAVA, 6), 0, 0, seen ? Map.of(LAVA, pools) : Map.of());
    }

    @Test void aBigSmeltBurnsLavaFromThePoolInSight() {
        var p = ProductionPlanner.plan(withLava(), lavaHave(16, true, Map.of(m("cobblestone"), 200)), m("stone"), 200);
        assertTrue(p.possible(), p.problems().toString());
        var smelt = only(p, Action.SMELT, m("stone"));
        assertEquals(m("lava_bucket"), smelt.fuel(), p.summary());
        assertEquals(smelt.furnaces(), smelt.fuelCount(), "a bucket a furnace: " + p.summary());
        assertTrue(smelt.furnaces() > 2, "lava doesn't hold it to two furnaces for 200: " + p.summary());
        assertEquals(smelt.fuelCount(), only(p, Action.MINE, m("lava_bucket")).blocks(), p.summary());
        assertTrue(p.summary().contains("with lava"), p.summary());
    }

    @Test void noMoreBucketsOfLavaThanThePoolsInSight() {
        var p = ProductionPlanner.plan(withLava(), lavaHave(3, true, Map.of(m("cobblestone"), 200)), m("stone"), 200);
        assertTrue(p.possible(), p.problems().toString());
        var smelt = only(p, Action.SMELT, m("stone"));
        if (smelt.fuel().equals(m("lava_bucket"))) assertTrue(smelt.fuelCount() <= 3, p.summary());
    }

    @Test void noLavaInSightIsNeverGoneLookingFor() {
        var p = ProductionPlanner.plan(withLava(), lavaHave(0, false, Map.of(m("cobblestone"), 200)), m("stone"), 200);
        assertTrue(p.steps().stream().noneMatch(s -> s.action() == Action.MINE && s.item().equals(m("lava_bucket"))), p.summary());
    }

    @Test void coalItCarriesBeatsLava() {
        var p = ProductionPlanner.plan(withLava(), lavaHave(4, true, Map.of(m("cobblestone"), 16, m("coal"), 4)), m("stone"), 16);
        assertTrue(p.possible(), p.problems().toString());
        assertEquals(m("coal"), only(p, Action.SMELT, m("stone")).fuel(), p.summary());
    }

    @Test void noShearsItMakesThemFirst() {
        var p = ProductionPlanner.plan(vanilla(), have(Map.of(m("oak_planks"), 3, m("iron_ingot"), 2), List.of(), 0, 0, 0,
                Map.of(WHITE_SHEEP, Where.KNOWN), Map.of(WHITE_SHEEP, 12)), m("white_bed"), 1);
        assertTrue(p.possible(), p.problems().toString());
        assertEquals(1, p.toolsMade().getOrDefault(m("shears"), 0).intValue(), p.toolsMade().toString());
        int shears = indexOf(p, Action.CRAFT, m("shears"));
        int shear = indexOf(p, Action.SHEAR, m("white_wool"));
        assertTrue(shears >= 0 && shears < shear, "the shears before the shearing: " + p.steps());
    }

    @Test void flintFromGravelOneInTen() {
        var p = ProductionPlanner.plan(vanilla(), have(Map.of(m("iron_ingot"), 1), List.of(), 0, 0, 0, Map.of(m("gravel"), Where.KNOWN),
                Map.of(m("gravel"), 8)), m("flint_and_steel"), 1);
        assertTrue(p.possible(), p.problems().toString());
        assertEquals(10, p.mined().get(m("gravel")).intValue(), "10 gravel for a flint, on average");
    }

    // ── The planner's own checks (MA7.2) ────────────────────────────

    @Test void threeDiamondsFromAStonePickaxe() {
        var p = ProductionPlanner.plan(vanilla(), have(Map.of(m("coal"), 8, m("oak_planks"), 8), List.of(new Tool("pickaxe", 2, false, 131)),
                1, 0, 0, Map.of(m("iron_ore"), Where.KNOWN, m("diamond_ore"), Where.KNOWN), Map.of(m("iron_ore"), 30, m("diamond_ore"), 60)),
                m("diamond"), 3);
        assertTrue(p.possible(), p.problems().toString());
        assertEquals(1, p.toolsMade().getOrDefault(m("iron_pickaxe"), 0).intValue(), "an iron pickaxe first: " + p.toolsMade());
        assertEquals(3, p.mined().get(m("iron_ore")).intValue(), "3 iron for it");
        assertEquals(3, only(p, Action.SMELT, m("iron_ingot")).count());
        assertEquals(3, p.mined().get(m("diamond_ore")).intValue());
        int iron = indexOf(p, Action.MINE, m("raw_iron"));
        int pick = indexOf(p, Action.CRAFT, m("iron_pickaxe"));
        int dia = indexOf(p, Action.MINE, m("diamond"));
        assertTrue(iron >= 0 && iron < pick && pick < dia, "iron, then the pickaxe, then the diamonds: " + p.steps());
    }

    @Test void seventyThreeIronAndTheFuelForThem() {
        var p = ProductionPlanner.plan(vanilla(), have(Map.of(m("coal"), 20), List.of(new Tool("pickaxe", 2, false, 131)), 0, 0, 0,
                Map.of(m("iron_ore"), Where.KNOWN), Map.of(m("iron_ore"), 16)), m("iron_ingot"), 73);
        assertTrue(p.possible(), p.problems().toString());
        var smelt = only(p, Action.SMELT, m("iron_ingot"));
        assertEquals(73, smelt.count());
        assertEquals(73, p.mined().get(m("iron_ore")).intValue());
        // Whole coals per furnace: each burns ceil(its share / 8), never less than 73 / 8 all told.
        var split = FurnaceSplit.split(73, smelt.furnaces(), 200, 1600);
        assertEquals(split.fuelTotal(), smelt.fuelCount(), p.summary());
        assertTrue(smelt.fuelCount() >= 10 && smelt.fuelCount() <= 10 + smelt.furnaces(), p.summary());
    }

    static int indexOf(Plan p, Action a, String item) {
        for (int i = 0; i < p.steps().size(); i++) {
            if (p.steps().get(i).action() == a && p.steps().get(i).item().equals(item)) return i;
        }
        return -1;
    }
}
