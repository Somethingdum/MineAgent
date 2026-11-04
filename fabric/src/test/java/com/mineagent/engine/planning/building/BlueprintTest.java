package com.mineagent.engine.planning.building;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Blueprints and their checks (SMH plan 70, MA9.1). */
final class BlueprintTest {
    @Test void theHutsMaterialsAndOrder() {
        var hut = Blueprint.hut("minecraft:oak_planks", "minecraft:oak_door");
        var m = hut.materials();
        assertEquals(55, m.get("minecraft:oak_planks").intValue(), "30 of wall and 25 of roof");
        assertEquals(1, m.get("minecraft:oak_door").intValue());
        assertEquals(1, m.get("minecraft:torch").intValue());
        assertTrue(hut.unsupported().isEmpty(), "every block has one to go against, in the order it is built: " + hut.unsupported());
        // Bottom up: no block above y 1 comes before one under it.
        int lastY = 0;
        for (var b : hut.blocks()) {
            if (b.id().contains("door") || b.id().contains("torch")) continue;
            assertTrue(b.y() >= lastY, "bottom up");
            lastY = b.y();
        }
        // The doorway is left open, and the door goes in last, from outside.
        assertTrue(hut.blocks().stream().noneMatch(b -> b.x() == 2 && b.z() == 0 && b.y() <= 2 && b.id().contains("planks")));
        assertTrue(hut.blocks().get(hut.blocks().size() - 1).fromOutside());
        // The roof starts at the walls: its first block is on the edge.
        var firstRoof = hut.blocks().stream().filter(b -> b.y() == 3).findFirst().orElseThrow();
        assertTrue(firstRoof.x() == 0 || firstRoof.x() == 4 || firstRoof.z() == 0 || firstRoof.z() == 4);
    }

    @Test void cornersBeforeTheBlocksBesideThem() {
        // A corner placed after both of its neighbours sits in a pocket whose faces can't be seen from inside: the
        // builder found it so in the game (the hut's 4,1,4, 4,2,4 and 4,3,4).
        var blocks = Blueprint.hut("minecraft:oak_planks", "minecraft:oak_door").blocks();
        java.util.List<String> order = new java.util.ArrayList<>();
        for (var b : blocks) order.add(b.x() + "," + b.y() + "," + b.z());
        for (int y = 1; y <= 3; y++) {
            for (int[] c : new int[][] {{0, 0}, {0, 4}, {4, 0}, {4, 4}}) {
                int at = order.indexOf(c[0] + "," + y + "," + c[1]);
                int nx = order.indexOf((c[0] == 0 ? 1 : 3) + "," + y + "," + c[1]);
                int nz = order.indexOf(c[0] + "," + y + "," + (c[1] == 0 ? 1 : 3));
                assertTrue(at >= 0 && at < Math.max(nx, nz), "corner " + c[0] + "," + y + "," + c[1] + " before both its neighbours");
            }
        }
    }

    @Test void aFloatingBlockIsFound() {
        var bad = new Blueprint("bad", 3, 3, 3, java.util.List.of(new Blueprint.Block(0, 1, 0, "minecraft:stone", false),
                new Blueprint.Block(2, 3, 2, "minecraft:stone", false)), 1, 1, 0, 0);
        assertEquals(1, bad.unsupported().size());
        assertEquals(3, bad.unsupported().get(0).y());
    }
}
