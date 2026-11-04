package com.mineagent.engine.planning.mining;

import com.mineagent.engine.planning.mining.OrePlan.Feature;
import com.mineagent.engine.planning.mining.Tunnel.Kind;
import com.mineagent.engine.planning.mining.Tunnel.Step;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Where and how it mines on its own (SMH plan 70, MA7.8), on the game's 1.21.1 ore features. */
final class MiningPlanTest {
    @Test void theBestHeightFromTheGamesOreFeatures() {
        // Iron: the upper band peaks in the mountains, the middle at 16, the small ones even below 72.
        var iron = List.of(new Feature("trapezoid", 80, 384, 90, 9), new Feature("trapezoid", -24, 56, 10, 9),
                new Feature("uniform", -64, 72, 10, 4));
        assertEquals(16, OrePlan.bestY(iron, -64, 56), "iron near Y 16");
        // Diamonds: a trapezoid from -144 to 16 peaks at -64, the floor: just above bedrock.
        var diamond = List.of(new Feature("trapezoid", -144, 16, 7, 4), new Feature("uniform", -64, -4, 2, 8),
                new Feature("trapezoid", -144, 16, 4, 8));
        assertEquals(-59, OrePlan.bestY(diamond, -64, 56), "diamonds near Y -58");
        // Coal: both bands sit high; kept underground.
        var coal = List.of(new Feature("uniform", 136, 320, 30, 17), new Feature("trapezoid", 0, 192, 20, 17));
        int y = OrePlan.bestY(coal, -64, 56);
        assertTrue(y <= 56 && y >= 0, "coal underground: " + y);
        assertEquals(16, OrePlan.bestY(List.of(), -64, 56), "nothing known: Y 16");
    }

    @Test void aStaircaseDownNeverStraightDown() {
        var steps = Tunnel.plan(0, 64, 0, 1, 0, 60, 6, 4);
        var stairs = steps.stream().filter(s -> s.kind() == Kind.STAIR).toList();
        assertEquals(4, stairs.size(), "one down a step");
        int px = 0, py = 64;
        for (Step s : stairs) {
            assertEquals(px + 1, s.x(), "always a step forward");
            assertEquals(py - 1, s.y());
            assertEquals(3, s.clear().size(), "feet, head and the headroom to walk down into it");
            px = s.x();
            py = s.y();
        }
    }

    @Test void branchesEveryThirdBlockBothSidesTwoHigh() {
        var steps = Tunnel.plan(0, 16, 0, 1, 0, 16, 6, 4);
        var main = steps.stream().filter(s -> s.kind() == Kind.MAIN).toList();
        assertEquals(6, main.size());
        for (Step s : main) assertEquals(2, s.clear().size(), "two high");
        var branches = steps.stream().filter(s -> s.kind() == Kind.BRANCH).toList();
        assertEquals(2 * 2 * 4, branches.size(), "at 3 and 6, left and right, 4 long");
        // Heading east (+x), the first branch goes north (left, -z), then south.
        assertEquals(3, branches.get(0).x());
        assertEquals(-1, branches.get(0).z());
        assertEquals(1, branches.get(4).z());
        var returns = steps.stream().filter(s -> s.kind() == Kind.RETURN).toList();
        assertEquals(4, returns.size(), "each branch walked back");
        assertEquals(3, returns.get(0).x());
        assertEquals(0, returns.get(0).z());
        // Giving up a branch part way: its return step.
        int first = steps.indexOf(branches.get(1));
        int back = Tunnel.returnOf(steps, first);
        assertEquals(Kind.RETURN, steps.get(back).kind());
        assertEquals(branches.get(1).branch(), steps.get(back).branch());
        assertEquals(-1, Tunnel.returnOf(steps, steps.indexOf(main.get(0))), "the main tunnel has no return");
    }

    @Test void aSlimeRoomIsDugRowByRowBelowForty() {
        // From the surface at y 64 a little west of a slime chunk at (32, 48): stairs down to 36, then the room.
        var steps = Tunnel.room(20, 64, 50, Tunnel.SLIME_ROOM_Y, 32, 48, 16);
        var stairs = steps.stream().filter(s -> s.kind() == Tunnel.Kind.STAIR).toList();
        assertEquals(28, stairs.size(), "one block down for each one forward");
        assertEquals(Tunnel.SLIME_ROOM_Y, stairs.get(stairs.size() - 1).y());
        var room = steps.stream().filter(s -> s.kind() == Tunnel.Kind.BRANCH).toList();
        assertEquals(256, room.size(), "every cell of the chunk");
        for (var s : room) {
            assertTrue(s.x() >= 32 && s.x() < 48 && s.z() >= 48 && s.z() < 64, "inside the chunk");
            assertEquals(Tunnel.SLIME_ROOM_Y, s.y());
            assertEquals(3, s.clear().size(), "three high, for a big slime");
            assertTrue(s.clear().stream().allMatch(c -> c[1] < 40), "slimes spawn there only below 40");
        }
        assertEquals(16L, steps.stream().filter(s -> s.kind() == Tunnel.Kind.RETURN).count(), "each row walked back");
        // Each step is next to the one before (a row's first cell beside the last row's).
        Tunnel.Step prev = null;
        for (var s : steps) {
            if (prev != null && s.kind() != Tunnel.Kind.RETURN && prev.kind() != Tunnel.Kind.RETURN) {
                int d = Math.abs(s.x() - prev.x()) + Math.abs(s.z() - prev.z());
                assertTrue(d <= 1, "a step at most one block on from the last: " + prev + " -> " + s);
            }
            prev = s;
        }
        int row3 = steps.indexOf(room.get(16 * 3 + 5));
        assertEquals(Tunnel.Kind.RETURN, steps.get(Tunnel.returnOf(steps, row3)).kind(), "a row given up goes back to its start");
        assertEquals(48 + 3, steps.get(Tunnel.returnOf(steps, row3)).z());
    }

    @Test void theNearestChunkOutward() {
        int[] c = Tunnel.nearestChunk(0, 0, 5, (x, z) -> x == 2 && z == -1 || x == -3 && z == 3);
        assertArrayEquals(new int[] {2, -1}, c);
        assertNull(Tunnel.nearestChunk(0, 0, 1, (x, z) -> x == 2), "none within the radius");
        assertArrayEquals(new int[] {0, 0}, Tunnel.nearestChunk(0, 0, 3, (x, z) -> true), "its own chunk first");
    }
}

