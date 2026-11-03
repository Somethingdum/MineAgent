package com.mineagent.engine.pathing;

import com.mineagent.engine.pathing.util.CoarsePlanner;
import com.mineagent.engine.world.ChunkSummaries;
import com.mineagent.engine.world.ChunkSummaries.Kind;
import com.mineagent.engine.world.ChunkSummaries.Summary;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** SMH plan 70, MA5.10: long trips planned over the chunks it has seen. */
final class CoarsePlannerTest {
    private static final String OW = "minecraft:overworld";

    @Test void summariesKeepWhatWasSeenAndSaveAndLoad() throws Exception {
        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("chunks");
        var store = new com.mineagent.engine.memory.store.MemoryStore(dir);
        ChunkSummaries m = new ChunkSummaries(store);
        m.put(OW, 3, -7, new Summary(Kind.WATER, 62, false));
        m.put(OW, -400, 9000, new Summary(Kind.LAND, 140, true));
        m.put(OW, 1, 1, new Summary(Kind.UNKNOWN, 70, false)); // not kept
        assertEquals(new Summary(Kind.WATER, 62, false), m.get(OW, 3, -7));
        assertEquals(new Summary(Kind.LAND, 140, true), m.get(OW, -400, 9000));
        assertEquals(Kind.UNKNOWN, m.get(OW, 1, 1).kind());
        assertEquals(Kind.UNKNOWN, m.get("minecraft:the_nether", 3, -7).kind());
        store.close();
        var store2 = new com.mineagent.engine.memory.store.MemoryStore(dir);
        ChunkSummaries back = new ChunkSummaries(store2);
        assertEquals(2, back.size());
        assertEquals(new Summary(Kind.LAND, 140, true), back.get(OW, -400, 9000));
        assertEquals(new Summary(Kind.WATER, 62, false), back.get(OW, 3, -7));
        store2.close();
    }

    @Test void overUnseenGroundTheWayIsStraight() {
        List<int[]> way = CoarsePlanner.plan(new ChunkSummaries(), OW, 0, 64, 0, 1000, 0);
        assertFalse(way.isEmpty());
        int[] end = way.get(way.size() - 1);
        assertEquals(1000 >> 4, end[0] >> 4);
        for (int[] p : way) assertEquals(0, p[2] >> 4, "a straight trip east stays in the same row of chunks");
        assertTrue(way.size() <= 1000 / 16 / 4 + 2, "a straight stretch has a waypoint every few chunks, not every one");
    }

    @Test void itGoesRoundALakeItHasSeenWhenTheWayRoundIsShorter() {
        ChunkSummaries m = new ChunkSummaries();
        // A lake 5 chunks across north to south, right on the straight line east.
        for (int cx = 4; cx <= 8; cx++) for (int cz = -2; cz <= 2; cz++) m.put(OW, cx, cz, new Summary(Kind.WATER, 62, false));
        for (int cx = 0; cx <= 14; cx++) for (int cz = -6; cz <= 6; cz++) {
            if (m.get(OW, cx, cz).kind() == Kind.UNKNOWN) m.put(OW, cx, cz, new Summary(Kind.LAND, 64, false));
        }
        List<int[]> way = CoarsePlanner.plan(m, OW, 8, 64, 8, 14 * 16 + 8, 8);
        for (int[] p : way) assertNotEquals(Kind.WATER, m.get(OW, p[0] >> 4, p[2] >> 4).kind());
        assertTrue(way.stream().anyMatch(p -> Math.abs(p[2] >> 4) >= 3), "the way leaves the straight line");
        assertEquals(64, way.get(way.size() - 1)[1], "a seen chunk gives its surface height");
    }

    @Test void theSameChunkNeedsNoPlan() {
        assertTrue(CoarsePlanner.plan(new ChunkSummaries(), OW, 1, 64, 1, 15, 15).isEmpty());
    }
}
