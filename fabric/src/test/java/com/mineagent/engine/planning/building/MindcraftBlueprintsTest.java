package com.mineagent.engine.planning.building;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** SMH plan 70, MA9.1: Mindcraft's two blueprints, as this mod builds them. */
class MindcraftBlueprintsTest {
    @Test void theDirtShelterIsSoundAndCountedRight() {
        Blueprint b = MindcraftBlueprints.dirtShelter();
        assertTrue(b.unsupported().isEmpty(), "every block has one to go against: " + b.unsupported());
        assertEquals(1, b.materials().get("minecraft:oak_door").intValue(), "one door for its two halves");
        assertEquals(1, b.materials().get("minecraft:chest").intValue());
        assertEquals(1, b.materials().get("minecraft:torch").intValue());
        assertEquals(1, b.doorZ(), "the door at the front, behind its porch (Mindcraft draws it at the far row)");
        assertEquals("minecraft:oak_door", b.blocks().get(b.blocks().size() - 1).id(), "the door last");
        assertTrue(b.materials().get("minecraft:dirt") > 30, b.materials().toString());
    }

    @Test void theSmallWoodHouseInTheWoodItHas() {
        Blueprint b = MindcraftBlueprints.smallWoodHouse("minecraft:spruce_planks", "minecraft:spruce_door");
        assertTrue(b.unsupported().isEmpty(), "every block has one to go against: " + b.unsupported());
        assertEquals(8, b.materials().get("minecraft:spruce_log").intValue(), "four corner posts, two high");
        assertEquals(1, b.materials().get("minecraft:spruce_door").intValue());
        assertEquals(3, b.materials().get("minecraft:torch").intValue(), "the fourth would stand on the chest");
        assertFalse(b.materials().containsKey("minecraft:white_bed"), "beds left out");
        for (var x : b.blocks()) assertTrue(x.y() >= 1 && x.y() <= b.height(), x.toString());
    }
}
