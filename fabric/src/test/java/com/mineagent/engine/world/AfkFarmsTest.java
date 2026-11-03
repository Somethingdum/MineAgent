package com.mineagent.engine.world;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** No block changes at a farm whose player is AFK (SMH plan 70, MA5.4). */
final class AfkFarmsTest {
    @Test void blocksNearAnAfkFarmAreLeftAlone() {
        AfkFarms.clearAll();
        UUID alex = UUID.randomUUID();
        String ow = "minecraft:overworld";
        assertFalse(AfkFarms.protects(ow, 0, 64, 0), "no one AFK: nothing protected");
        AfkFarms.set(alex, new AfkFarms.Area(ow, 0.5, 64.0, 0.5, 16.0));
        assertTrue(AfkFarms.protects(ow, 0, 64, 0));
        assertTrue(AfkFarms.protects(ow, 10, 60, -10), "14 blocks away");
        assertFalse(AfkFarms.protects(ow, 17, 64, 0), "past 16");
        assertFalse(AfkFarms.protects("minecraft:the_nether", 0, 64, 0), "another dimension");
        AfkFarms.clear(alex);
        assertFalse(AfkFarms.protects(ow, 0, 64, 0), "back at the keys: the farm is theirs to tend again");
    }

    @Test void eachPlayersFarmIsTheirOwn() {
        AfkFarms.clearAll();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        String ow = "minecraft:overworld";
        AfkFarms.set(a, new AfkFarms.Area(ow, 0, 64, 0, 16));
        AfkFarms.set(b, new AfkFarms.Area(ow, 100, 64, 0, 16));
        assertTrue(AfkFarms.protects(ow, 100, 64, 0));
        AfkFarms.set(a, null);
        assertFalse(AfkFarms.protects(ow, 0, 64, 0));
        assertTrue(AfkFarms.protects(ow, 100, 64, 0), "the other player's farm stays protected");
        AfkFarms.clearAll();
    }
}
