package com.mineagent.engine.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** SMH plan 70, MA9.4: the holes it dug near home. */
class HolesTest {
    @Test void nearestFirstAndFilledOnce() {
        var h = new Holes();
        h.dug("o", 10, 64, 10, "minecraft:grass_block", 1);
        h.dug("o", 2, 64, 2, "minecraft:stone", 2);
        h.dug("n", 1, 64, 1, "minecraft:netherrack", 3);
        var near = h.near("o", 0, 64, 0, 32, 8);
        assertEquals(2, near.size(), "this world's only");
        assertEquals(2, near.get(0).x(), "nearest first");
        h.filled("o", 2, 64, 2);
        assertEquals(1, h.near("o", 0, 64, 0, 32, 8).size());
    }

    @Test void plainFillersAndKeptWithTheWorld() {
        assertEquals("minecraft:dirt", Holes.filler("minecraft:grass_block"));
        assertEquals("minecraft:cobblestone", Holes.filler("minecraft:stone"));
        assertEquals("minecraft:sand", Holes.filler("minecraft:sand"));
        var h = new Holes();
        h.dug("o", 1, 2, 3, "minecraft:dirt", 9);
        var json = new com.google.gson.Gson().toJson(h.exportState());
        var again = new Holes();
        again.importState(new com.google.gson.Gson().fromJson(json, Holes.State.class));
        assertEquals(1, again.size());
    }
}
