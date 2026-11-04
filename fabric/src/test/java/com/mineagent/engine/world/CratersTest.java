package com.mineagent.engine.world;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CratersTest {
    @Test void groundIsEarthSandGravelAndRockNotWhatIsBuilt() {
        assertTrue(Craters.ground("minecraft:grass_block"));
        assertTrue(Craters.ground("minecraft:sand"));
        assertTrue(Craters.ground("minecraft:deepslate"));
        assertTrue(Craters.ground("minecraft:orange_terracotta"));
        assertFalse(Craters.ground("minecraft:oak_planks"));
        assertFalse(Craters.ground("minecraft:stone_bricks"));
        assertFalse(Craters.ground("minecraft:white_glazed_terracotta"));
        assertFalse(Craters.ground("minecraft:chest"));
    }

    @Test void theCommonestTopBlockFillsAndGrassGivesDirt() {
        assertEquals("minecraft:grass_block", Craters.surface(Map.of("minecraft:grass_block", 40, "minecraft:sand", 12, "minecraft:oak_log", 60)));
        assertEquals("minecraft:sand", Craters.surface(Map.of("minecraft:sand", 30, "minecraft:sandstone", 4)));
        assertNull(Craters.surface(Map.of("minecraft:oak_planks", 9)));
        assertEquals("minecraft:dirt", Holes.filler("minecraft:grass_block"));
        assertEquals("minecraft:sand", Holes.filler("minecraft:sand"));
    }

    @Test void filledFromTheBottomUp() {
        var order = Craters.bottomUp(List.of(new int[] {0, 64, 0}, new int[] {1, 62, 0}, new int[] {0, 63, 1}, new int[] {3, 62, 3}), 0, 0);
        assertEquals(62, order.get(0)[1]);
        assertEquals(1, order.get(0)[0], "the nearer of the two at the bottom first");
        assertEquals(62, order.get(1)[1]);
        assertEquals(64, order.get(3)[1]);
    }
}
