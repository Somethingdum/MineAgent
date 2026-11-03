package com.mineagent.engine.world;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Which blocks people placed (SMH plan 70, MA5.2): one bit per block, kept only for the 16-block chunk sections where
 * someone placed something (4096 bits, 512 bytes, per section). Pure; {@link PlacedBlocks} keeps one per dimension in
 * the world's own save.
 */
public final class PlacedBits {
    private final Map<Long, long[]> sections = new HashMap<>();

    /** The section a block is in, as one number (the game's SectionPos packing: 22 bits x, 20 bits y, 22 bits z). */
    public static long sectionKey(int x, int y, int z) {
        long sx = x >> 4, sy = y >> 4, sz = z >> 4;
        return ((sx & 0x3FFFFFL) << 42) | ((sz & 0x3FFFFFL) << 20) | (sy & 0xFFFFFL);
    }

    private static int bit(int x, int y, int z) {
        return ((y & 15) << 8) | ((z & 15) << 4) | (x & 15);
    }

    public synchronized void mark(int x, int y, int z) {
        long[] words = sections.computeIfAbsent(sectionKey(x, y, z), k -> new long[64]);
        int b = bit(x, y, z);
        words[b >>> 6] |= 1L << (b & 63);
    }

    public synchronized void clear(int x, int y, int z) {
        long key = sectionKey(x, y, z);
        long[] words = sections.get(key);
        if (words == null) return;
        int b = bit(x, y, z);
        words[b >>> 6] &= ~(1L << (b & 63));
        for (long w : words) if (w != 0L) return;
        sections.remove(key);
    }

    public synchronized boolean isMarked(int x, int y, int z) {
        long[] words = sections.get(sectionKey(x, y, z));
        if (words == null) return false;
        int b = bit(x, y, z);
        return (words[b >>> 6] & (1L << (b & 63))) != 0L;
    }

    public synchronized int sectionCount() {
        return sections.size();
    }

    /** A copy of every section, for saving. */
    public synchronized Map<Long, long[]> export() {
        Map<Long, long[]> out = new LinkedHashMap<>();
        for (var e : sections.entrySet()) out.put(e.getKey(), e.getValue().clone());
        return out;
    }

    /** Sections read back from a save; any of the wrong size is skipped. */
    public synchronized void load(Map<Long, long[]> saved) {
        sections.clear();
        if (saved == null) return;
        for (var e : saved.entrySet()) {
            if (e.getKey() != null && e.getValue() != null && e.getValue().length == 64) {
                sections.put(e.getKey(), e.getValue().clone());
            }
        }
    }
}
