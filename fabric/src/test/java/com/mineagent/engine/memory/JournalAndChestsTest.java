package com.mineagent.engine.memory;

import com.mineagent.engine.memory.store.MemoryStore;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** SMH plan 70, MA5.9 and MA5.7: the journal of what happened, and chests with what is in them. */
final class JournalAndChestsTest {
    private static Clock at(String isoDay) {
        return Clock.fixed(Instant.parse(isoDay + "T12:00:00Z"), ZoneId.of("UTC"));
    }

    @Test void aDayIsKeptAndSummed() throws Exception {
        Path dir = Files.createTempDirectory("journal");
        MemoryStore store = new MemoryStore(dir);
        EventJournal j = new EventJournal(at("2026-11-02"));
        j.attach(store, 90);
        j.record("order", "go home", 10);
        j.record("place", "named the mine", 20, "minecraft:overworld", 1, 2, 3);
        j.record("fight", "fought off a zombie", 30);
        j.record("stored", "stored 32 iron_ingot in the chest at 5, 64, 9", 40);
        store.flushBlocking();
        j.close();
        String said = j.describe(LocalDate.parse("2026-11-02"), "Yesterday");
        assertTrue(said.startsWith("Yesterday: 1 order, 1 place named, 1 fight, 1 thing stored"), said);
        assertTrue(said.contains("named the mine"), said);

        // The next day: yesterday is compressed, still readable and searchable; the summary stays.
        MemoryStore store2 = new MemoryStore(dir);
        EventJournal j2 = new EventJournal(at("2026-11-03"));
        j2.attach(store2, 90);
        assertTrue(Files.exists(dir.resolve("events/2026-11-02.log.gz")));
        assertFalse(Files.exists(dir.resolve("events/2026-11-02.log")));
        assertEquals(4, j2.day(LocalDate.parse("2026-11-02")).size());
        assertEquals(1, j2.search("iron", "stored", 5).size());
        assertNotNull(j2.summary(LocalDate.parse("2026-11-02")));
        assertEquals(4, j2.count());
        store2.close();
        j2.close();

        // Ninety-one days on, the day itself is gone; its summary is kept for good.
        MemoryStore store3 = new MemoryStore(dir);
        EventJournal j3 = new EventJournal(at("2027-02-01"));
        j3.attach(store3, 90);
        assertTrue(j3.day(LocalDate.parse("2026-11-02")).isEmpty());
        assertNotNull(j3.summary(LocalDate.parse("2026-11-02")));
        store3.close();
        j3.close();
    }

    @Test void withoutAStoreItRemembersInMemory() {
        EventJournal j = new EventJournal(at("2026-11-03"));
        j.record("find", "found a village", 5);
        assertEquals(1, j.recent(10).size());
        assertEquals(1, j.day(LocalDate.parse("2026-11-03")).size());
        assertTrue(j.describe(LocalDate.parse("2026-11-01"), "Monday").contains("nothing"));
    }

    @Test void chestsAnswerWhereThingsArePutAndKeep() throws Exception {
        Path dir = Files.createTempDirectory("chests");
        MemoryStore store = new MemoryStore(dir);
        ChestMemory c = new ChestMemory(store, 100);
        c.looked("minecraft:overworld", 10, 64, 10, Map.of("minecraft:iron_ingot", 32, "minecraft:cobblestone", 64), 1);
        c.looked("minecraft:overworld", 500, 64, 10, Map.of("minecraft:iron_ingot", 5), 2);
        c.looked("minecraft:overworld", 20, 64, 10, Map.of(), 3);
        var held = c.holding("iron", "minecraft:overworld", 0, 64, 0, 5);
        assertEquals(2, held.size());
        assertEquals(32, held.get(0).count());
        assertEquals(2, c.holding("iron ingots", "minecraft:overworld", 0, 64, 0, 5).size());
        assertTrue(c.holding("diamond", "minecraft:overworld", 0, 64, 0, 5).isEmpty());
        store.close();
        ChestMemory back = new ChestMemory(new MemoryStore(dir), 100);
        assertEquals(3, back.count());
        assertEquals(64, back.holding("cobblestone", "minecraft:overworld", 0, 64, 0, 5).get(0).count());
    }

    @Test void chestsPastTheCapGoOldestLookedInFirst() {
        ChestMemory c = new ChestMemory(null, 64);
        for (int i = 0; i < 70; i++) c.looked("minecraft:overworld", i, 64, 0, Map.of("minecraft:dirt", 1), i);
        assertEquals(64, c.count());
        assertTrue(c.holding("dirt", "minecraft:overworld", 0, 64, 0, 100).stream().noneMatch(h -> h.chest().x() < 6));
    }
    @Test void theChestsNearAPlaceNearestFirst() {
        ChestMemory c = new ChestMemory(null, 64);
        String ow = "minecraft:overworld";
        c.looked(ow, 20, 64, 0, java.util.Map.of(), 1);
        c.looked(ow, 3, 64, 4, java.util.Map.of("minecraft:wheat", 3), 2);
        c.looked(ow, 40, 64, 0, java.util.Map.of(), 3);
        c.looked("minecraft:the_nether", 1, 64, 1, java.util.Map.of(), 4);
        var near = c.near(ow, 0, 64, 0, 24.0);
        assertEquals(2, near.size(), "within 24 blocks, in this dimension");
        assertEquals(3, near.get(0).x(), "nearest first");
        assertEquals(20, near.get(1).x());
        assertTrue(c.near(null, 0, 64, 0, 24.0).isEmpty());
    }
    @Test void chestsSeenAfterTheWorldsLastSaveAreLookedInAgain() {
        ChestMemory c = new ChestMemory(null, 64);
        String ow = "minecraft:overworld";
        c.looked(ow, 1, 64, 1, java.util.Map.of("minecraft:iron_ingot", 5), 1_000);
        c.looked(ow, 2, 64, 2, java.util.Map.of("minecraft:wheat", 9), 6_100);
        c.looked(ow, 3, 64, 3, java.util.Map.of(), 7_000);
        assertEquals(2, c.forgetAfter(6_000), "the world was saved at tick 6,000");
        assertEquals(1, c.count());
        assertEquals(1, c.near(ow, 0, 64, 0, 24.0).get(0).x(), "what it saw before the save is kept");
        assertEquals(0, c.forgetAfter(6_000), "nothing left to forget");
    }
}
