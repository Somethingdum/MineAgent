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
}
