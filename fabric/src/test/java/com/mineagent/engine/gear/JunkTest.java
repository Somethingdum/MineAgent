package com.mineagent.engine.gear;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class JunkTest {
    @Test void aDigsRockPastWhatItKeepsIsJunk() {
        var junk = Junk.excess(Map.of("minecraft:cobblestone", 300, "minecraft:diorite", 40, "minecraft:dirt", 20,
                "minecraft:diamond", 12, "minecraft:torch", 200, "minecraft:rotten_flesh", 40), Set.of());
        assertEquals(new Junk.Excess("minecraft:cobblestone", 172), junk.get(0), "128 cobblestone kept for bridges and pillars");
        assertTrue(junk.contains(new Junk.Excess("minecraft:diorite", 40)), "diorite is no use to it");
        assertTrue(junk.contains(new Junk.Excess("minecraft:dirt", 4)), "16 dirt kept");
        assertTrue(junk.contains(new Junk.Excess("minecraft:rotten_flesh", 8)), "32 rotten flesh kept for a cleric's emeralds");
        assertTrue(junk.stream().noneMatch(e -> e.item().equals("minecraft:diamond") || e.item().equals("minecraft:torch")), "never what it values");
        assertEquals(224, Junk.total(junk));
    }

    @Test void whatItsStockpileOrAnOrderKeepsIsNotJunk() {
        var junk = Junk.excess(Map.of("minecraft:cobblestone", 300, "minecraft:dirt", 100), Set.of("minecraft:dirt"));
        assertEquals(1, junk.size(), "the dirt an order needs stays");
        assertTrue(Junk.excess(Map.of("minecraft:cobblestone", 100), Set.of()).isEmpty(), "under what it keeps: nothing");
    }
}
