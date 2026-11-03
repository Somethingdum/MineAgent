package com.mineagent.engine.pathing;

import com.mineagent.engine.pathing.util.ExplorePlanner;
import com.mineagent.engine.survival.ReturnReserve;
import com.mineagent.engine.world.ChunkSummaries;
import com.mineagent.engine.world.ChunkSummaries.Kind;
import com.mineagent.engine.world.ChunkSummaries.Summary;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** SMH plan 70, MA5.6: looking where it hasn't been, and turning back in time. */
final class ExploreAndReserveTest {
    private static final String OW = "minecraft:overworld";

    @Test void theNextPlaceToLookIsTheNearestGroundNeverSeen() {
        ChunkSummaries m = new ChunkSummaries();
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) m.put(OW, x, z, new Summary(Kind.LAND, 64, false));
        m.put(OW, 3, 0, new Summary(Kind.LAND, 64, false));
        int[] next = ExplorePlanner.nextUnseen(m, OW, 0, 0, 10, Set.of());
        assertNotNull(next);
        assertEquals(3, Math.max(Math.abs(next[0]), Math.abs(next[1])), "the first ring not yet seen");
        assertFalse(next[0] == 3 && next[1] == 0, "not one it has seen");
        Set<Long> skip = new HashSet<>();
        skip.add(ChunkSummaries.key(next[0], next[1]));
        int[] other = ExplorePlanner.nextUnseen(m, OW, 0, 0, 10, skip);
        assertFalse(other[0] == next[0] && other[1] == next[1], "one it couldn't reach is passed over");
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) m.put(OW, x, z, new Summary(Kind.LAND, 64, false));
        assertNull(ExplorePlanner.nextUnseen(m, OW, 0, 0, 4, Set.of()), "all seen within reach");
    }

    @Test void itTurnsBackWithFoodToSpareForTheWayHome() {
        assertEquals(10.0, ReturnReserve.needed(400), 1e-9);
        // 400 blocks home: 10 food points sprinting, 14 with the margin, plus 6 kept.
        assertTrue(ReturnReserve.enough(20, 0, 0, 400));
        assertFalse(ReturnReserve.enough(14, 5, 0, 400));
        assertTrue(ReturnReserve.enough(10, 0, 10, 400), "food it carries counts");
        assertTrue(ReturnReserve.enough(6, 0, 0, 0), "at home nothing more is needed");
    }
}
