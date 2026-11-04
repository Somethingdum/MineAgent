package com.mineagent.engine.world;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** SMH plan 70, MA9.2: chests with a purpose. */
class ChestPurposesTest {
    @Test void theWordsForAPurpose() {
        assertEquals("tools", ChestPurposes.purpose("for tools"));
        assertEquals("food", ChestPurposes.purpose("the food chest"));
        assertEquals("ores", ChestPurposes.purpose("ores"));
        assertEquals("overflow", ChestPurposes.purpose("anything else"));
        assertEquals("spruce logs", ChestPurposes.purpose("spruce logs"));
    }

    @Test void whatFitsWhere() {
        assertTrue(ChestPurposes.fits("tools", "minecraft:iron_pickaxe", Set.of("tools")));
        assertFalse(ChestPurposes.fits("tools", "minecraft:bread", Set.of("food")));
        assertTrue(ChestPurposes.fits("spruce logs", "minecraft:spruce_log", Set.of("wood")));
        assertFalse(ChestPurposes.fits("spruce logs", "minecraft:oak_log", Set.of("wood")));
        assertTrue(ChestPurposes.fits("overflow", "minecraft:dirt", Set.of("blocks")));
        assertTrue(ChestPurposes.fits("cobblestone", "minecraft:cobblestone", Set.of("stone")) || ChestPurposes.fits("stone", "minecraft:cobblestone", Set.of("stone")));
    }

    @Test void keptWithTheWorld() {
        var p = new ChestPurposes();
        p.set("minecraft:overworld", 1, 64, 2, "for tools");
        var json = new com.google.gson.Gson().toJson(p.exportState());
        var again = new ChestPurposes();
        again.importState(new com.google.gson.Gson().fromJson(json, ChestPurposes.State.class));
        assertEquals("tools", again.of("minecraft:overworld", 1, 64, 2).orElseThrow());
        assertTrue(again.clear("minecraft:overworld", 1, 64, 2));
        assertTrue(again.of("minecraft:overworld", 1, 64, 2).isEmpty());
    }
}
