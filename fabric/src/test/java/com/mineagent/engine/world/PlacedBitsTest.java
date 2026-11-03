package com.mineagent.engine.world;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The record of blocks people placed (SMH plan 70, MA5.2): a bit per block, sections only where needed. */
final class PlacedBitsTest {
    @Test void placedBlocksAreRememberedAndBrokenOnesForgotten() {
        PlacedBits bits = new PlacedBits();
        bits.mark(10, 64, -5);
        bits.mark(-30_000_000 + 7, -64, 29_999_990);
        assertTrue(bits.isMarked(10, 64, -5));
        assertTrue(bits.isMarked(-30_000_000 + 7, -64, 29_999_990));
        assertFalse(bits.isMarked(11, 64, -5));
        assertFalse(bits.isMarked(10, 65, -5));
        assertFalse(bits.isMarked(10, 64 + 16, -5));
        assertEquals(2, bits.sectionCount());
        bits.clear(10, 64, -5);
        assertFalse(bits.isMarked(10, 64, -5));
        assertEquals(1, bits.sectionCount(), "an empty section is dropped");
    }

    @Test void neighboursInOneSectionShareIt() {
        PlacedBits bits = new PlacedBits();
        for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) bits.mark(x, y, 3);
        assertEquals(1, bits.sectionCount());
        assertTrue(bits.isMarked(15, 15, 3));
        assertFalse(bits.isMarked(15, 15, 4));
        bits.mark(16, 0, 3);
        assertEquals(2, bits.sectionCount());
    }

    @Test void whatIsSavedComesBack() {
        PlacedBits bits = new PlacedBits();
        bits.mark(1, 2, 3);
        bits.mark(-100, 50, 200);
        Map<Long, long[]> saved = bits.export();
        PlacedBits back = new PlacedBits();
        back.load(saved);
        assertTrue(back.isMarked(1, 2, 3));
        assertTrue(back.isMarked(-100, 50, 200));
        assertFalse(back.isMarked(1, 2, 4));
        back.load(Map.of(5L, new long[3]));
        assertEquals(0, back.sectionCount(), "a section of the wrong size is skipped");
    }
}
