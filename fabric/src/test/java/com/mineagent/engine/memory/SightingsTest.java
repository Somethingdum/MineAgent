package com.mineagent.engine.memory;

import com.mineagent.engine.memory.Sightings.Kind;
import com.mineagent.engine.memory.store.MemoryStore;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** SMH plan 70, MA5.7: things it has seen, kept by region, found nearest first, forgotten by worth. */
final class SightingsTest {
    private static final String OW = "minecraft:overworld";

    @Test void namesAreKeptPlainAndWordsFindThem() {
        assertEquals("iron", Sightings.base("minecraft:deepslate_iron_ore"));
        assertEquals("quartz", Sightings.base("minecraft:nether_quartz_ore"));
        assertEquals("cow", Sightings.base("minecraft:cow"));
        assertEquals("diamond", Sightings.fromWords("diamonds"));
        assertEquals("iron", Sightings.fromWords("the iron ore"));
        assertEquals("cow", Sightings.fromWords("cows"));
        assertEquals("lapis", Sightings.fromWords("lapis"));
        assertEquals("ancient_debris", Sightings.fromWords("ancient debris"));
    }

    @Test void theNearestIsFoundAndCloseSightingsMerge() {
        Sightings s = new Sightings(null, 1000);
        s.see(Kind.ORE, "minecraft:iron_ore", OW, 100, 20, 100, 1, 10);
        s.see(Kind.ORE, "minecraft:deepslate_iron_ore", OW, 101, 21, 101, 3, 20); // same 4-block bucket
        s.see(Kind.ORE, "minecraft:iron_ore", OW, 2000, 20, 2000, 1, 30); // another region
        s.see(Kind.ORE, "minecraft:diamond_ore", OW, 110, -50, 100, 2, 40);
        assertEquals(3, s.count());
        var found = s.nearest("iron", OW, 0, 64, 0, 5, 10_000);
        assertEquals(2, found.size());
        assertEquals(3, found.get(0).sighting().count());
        assertEquals(100, found.get(0).sighting().x());
        assertEquals(1, s.nearest("ORE", OW, 105, 0, 100, 1, 10_000).size());
        assertTrue(s.nearest("iron", "minecraft:the_nether", 0, 64, 0, 5, 10_000).isEmpty());
        assertTrue(s.known("diamond"));
        assertFalse(s.known("emerald"));
    }

    @Test void whatIsMinedOutIsStruckOff() {
        Sightings s = new Sightings(null, 1000);
        s.see(Kind.ORE, "minecraft:coal_ore", OW, 5, 60, 5, 1, 1);
        s.gone(Kind.ORE, "minecraft:coal_ore", OW, 5, 60, 5);
        assertEquals(0, s.count());
        assertTrue(s.nearest("coal", OW, 5, 60, 5, 5, 100).isEmpty());
        assertFalse(s.known("coal"));
    }

    @Test void whenFullTheLeastWorthGoesFirst() {
        Sightings s = new Sightings(null, 100);
        s.see(Kind.ORE, "minecraft:diamond_ore", OW, 0, 10, 0, 1, 0);
        for (int i = 0; i < 150; i++) s.see(Kind.MOB, "minecraft:zombie", OW, i * 16, 64, 0, 1, 1000 + i);
        assertTrue(s.count() <= 100);
        assertEquals(1, s.nearest("diamond", OW, 0, 10, 0, 1, 100).size(), "a diamond outlasts a crowd of zombies");
    }

    @Test void itKeepsThroughARestartAndAfterACrash() throws Exception {
        Path dir = Files.createTempDirectory("sightings");
        MemoryStore st = new MemoryStore(dir);
        Sightings a = new Sightings(st, 1000);
        a.see(Kind.ORE, "minecraft:gold_ore", OW, -700, 12, 300, 4, 5);
        a.see(Kind.BLOCK, "minecraft:spawner", OW, 40, 30, 40, 1, 6);
        st.flushBlocking();
        a.see(Kind.ORE, "minecraft:emerald_ore", OW, 1500, 40, -1500, 1, 7); // journal only: then a crash
        MemoryStore st2 = new MemoryStore(dir);
        Sightings b = new Sightings(st2, 1000);
        st2.replayJournals();
        assertEquals(3, b.count());
        assertEquals(4, b.nearest("gold", OW, 0, 0, 0, 1, 5000).get(0).sighting().count());
        assertEquals(1, b.nearest("emerald", OW, 0, 0, 0, 1, 5000).size());
        assertEquals(1, b.nearest("spawner", OW, 0, 0, 0, 1, 5000).size());
        var report = st2.report();
        assertEquals(Long.valueOf(3L), report.counts().get("sightings"));
        st2.close();
    }
}
