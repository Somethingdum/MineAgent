package com.mineagent.engine.world;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class ChestSignsTest {
    /** Names of things, as the game's items would answer (the game side asks the item list). */
    static final Set<String> ITEMS = Set.of("iron ingot", "gold ingot", "spruce log", "spruce logs", "wool", "sword", "bow", "arrow",
            "redstone", "string", "iron", "gold", "glass", "stair");
    static final Predicate<String> THING = w -> ITEMS.contains(w) || ITEMS.contains(ChestPurposes.singular(w));

    @Test void oneLineOnePurpose() {
        assertEquals(List.of("food"), ChestSigns.purposes(List.of("[Food]", "", "", ""), THING));
        assertEquals(List.of("tools"), ChestSigns.purposes(List.of("Tools", "chest"), THING));
    }

    @Test void severalOnALineOrOnLines() {
        assertEquals(List.of("iron", "gold"), ChestSigns.purposes(List.of("Iron, Gold"), THING));
        assertEquals(List.of("swords", "bows"), ChestSigns.purposes(List.of("Swords & Bows"), THING));
        assertEquals(List.of("redstone", "string", "arrows"), ChestSigns.purposes(List.of("Redstone", "String / Arrows"), THING));
    }

    @Test void twoLinesThatMakeOneThing() {
        assertEquals(List.of("spruce logs"), ChestSigns.purposes(List.of("Spruce", "Logs"), THING));
    }

    @Test void wordsThatSayNothingAreLeftOut() {
        assertEquals(List.of(), ChestSigns.purposes(List.of("Bob's", "Storage"), THING));
        assertEquals(List.of("glass"), ChestSigns.purposes(List.of("-- Glass --", "Keep out"), THING));
        assertEquals(List.of("overflow"), ChestSigns.purposes(List.of("Misc"), THING));
    }

    @Test void aSortOfThingTakesEveryKindOfIt() {
        assertEquals(1, ChestPurposes.rank("wool", "minecraft:white_wool", Set.of()));
        assertEquals(1, ChestPurposes.rank("swords", "minecraft:diamond_sword", Set.of("tools")));
        assertEquals(1, ChestPurposes.rank("stairs", "minecraft:oak_stairs", Set.of()));
        assertEquals(0, ChestPurposes.rank("glass", "minecraft:glass", Set.of()));
        assertEquals(0, ChestPurposes.rank("spruce logs", "minecraft:spruce_log", Set.of("wood")));
        assertEquals(2, ChestPurposes.rank("tools", "minecraft:diamond_sword", Set.of("tools")));
        assertEquals(Integer.MAX_VALUE, ChestPurposes.rank("iron", "minecraft:iron_sword", Set.of("tools")),
                "iron is a thing by name: the iron chest isn't for swords");
        assertEquals(1, ChestPurposes.rank("iron", "minecraft:iron_ingot", Set.of("ores")));
        assertEquals(1, ChestPurposes.rank("iron", "minecraft:raw_iron", Set.of("ores")));
        assertEquals(1, ChestPurposes.rank("iron", "minecraft:deepslate_iron_ore", Set.of("ores")));
        assertEquals(1, ChestPurposes.rank("gold", "minecraft:gold_nugget", Set.of("ores")));
        assertEquals(Integer.MAX_VALUE, ChestPurposes.rank("gold", "minecraft:golden_apple", Set.of("food")));
    }

    @Test void theOwnersOwnWords() {
        Predicate<String> thing = w -> Set.of("ingot", "seed", "sapling", "iron", "gold").contains(ChestPurposes.singular(w));
        var ores = ChestSigns.purposes(List.of("Ores, ingots and shit"), thing);
        assertTrue(ores.contains("ores") && !ores.contains("overflow"), "the ores chest, not an overflow chest too: " + ores);
        for (String coal : List.of("minecraft:coal", "minecraft:raw_iron", "minecraft:diamond", "minecraft:lapis_lazuli", "minecraft:gold_ingot")) {
            int best = Integer.MAX_VALUE;
            for (String p : ores) best = Math.min(best, ChestPurposes.rank(p, coal, Set.of("ores")));
            assertTrue(best < Integer.MAX_VALUE, coal + " goes in " + ores);
        }
        assertEquals(List.of("food"), ChestSigns.purposes(List.of("Food"), thing));
        assertEquals(List.of("armor"), ChestSigns.purposes(List.of("Armor"), thing));
        assertEquals(List.of("weapons"), ChestSigns.purposes(List.of("Weapons"), thing));
        assertEquals(List.of("overflow"), ChestSigns.purposes(List.of("Spare bullshit"), thing));
        assertEquals(List.of("seeds", "saplings"), ChestSigns.purposes(List.of("Seeds and saplings"), thing));
        assertEquals(1, ChestPurposes.rank("seeds", "minecraft:wheat_seeds", Set.of("crops")));
        assertEquals(1, ChestPurposes.rank("saplings", "minecraft:oak_sapling", Set.of("blocks")));
    }

    @Test void armourWeaponsAndToolsEachHaveTheirChestAndAToolsChestTakesTheRest() {
        assertEquals(2, ChestPurposes.rank("weapons", "minecraft:iron_sword", Set.of("weapons", "gear")));
        assertEquals(2, ChestPurposes.rank("armor", "minecraft:iron_chestplate", Set.of("armor", "gear")));
        assertEquals(Integer.MAX_VALUE, ChestPurposes.rank("armor", "minecraft:iron_sword", Set.of("weapons", "gear")));
        assertEquals(3, ChestPurposes.rank("tools", "minecraft:iron_sword", Set.of("weapons", "gear")), "with no weapons chest, the tools one");
        assertEquals(2, ChestPurposes.rank("gear", "minecraft:iron_sword", Set.of("weapons", "gear")));
        assertEquals("weapons", ChestPurposes.purpose("this chest is for weapons".replaceFirst("^this chest is ", "")));
        assertEquals("armor", ChestPurposes.purpose("the armour chest"));
    }
}
