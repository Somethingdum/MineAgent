package com.mineagent.engine.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What it has seen of each chunk (SMH plan 70, MA5.10, and the start of MA5.7's explored map): land, water or
 * something to avoid (lava, fire), its surface height, and whether it is steep. One number per chunk, per dimension.
 * Long trips are planned over this first. Pure.
 */
public final class ChunkSummaries {
    public enum Kind { UNKNOWN, LAND, WATER, HAZARD }

    public record Summary(Kind kind, int height, boolean steep) {}

    /** One dimension's chunks, for saving. */
    public record Dim(String dim, long[] keys, int[] values) {}

    public record State(List<Dim> dims) {}

    /** Chunks kept per dimension (about 100 MB of world each way at 100,000). */
    public static final int MAX_PER_DIM = 100_000;

    private final Map<String, LinkedHashMap<Long, Integer>> byDim = new HashMap<>();

    public static long key(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    static int pack(Summary s) {
        int height = Math.max(0, Math.min(1023, s.height() + 512));
        return s.kind().ordinal() | (height << 2) | (s.steep() ? 1 << 12 : 0);
    }

    static Summary unpack(int v) {
        return new Summary(Kind.values()[v & 3], ((v >>> 2) & 1023) - 512, (v & (1 << 12)) != 0);
    }

    public synchronized void put(String dim, int cx, int cz, Summary s) {
        if (dim == null || s == null || s.kind() == Kind.UNKNOWN) return;
        var chunks = byDim.computeIfAbsent(dim, d -> new LinkedHashMap<>(256, 0.75f, true));
        chunks.put(key(cx, cz), pack(s));
        if (chunks.size() > MAX_PER_DIM) {
            // The one looked at longest ago goes first.
            var oldest = chunks.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
    }

    /** What it knows of a chunk; UNKNOWN when it hasn't seen it. */
    public synchronized Summary get(String dim, int cx, int cz) {
        var chunks = byDim.get(dim);
        Integer v = chunks == null ? null : chunks.get(key(cx, cz));
        return v == null ? new Summary(Kind.UNKNOWN, 0, false) : unpack(v);
    }

    public synchronized int size() {
        int n = 0;
        for (var chunks : byDim.values()) n += chunks.size();
        return n;
    }

    public synchronized State exportState() {
        List<Dim> dims = new ArrayList<>();
        for (var e : byDim.entrySet()) {
            long[] keys = new long[e.getValue().size()];
            int[] values = new int[keys.length];
            int i = 0;
            for (var c : e.getValue().entrySet()) {
                keys[i] = c.getKey();
                values[i++] = c.getValue();
            }
            dims.add(new Dim(e.getKey(), keys, values));
        }
        return new State(dims);
    }

    public synchronized void importState(State s) {
        byDim.clear();
        if (s == null || s.dims() == null) return;
        for (Dim d : s.dims()) {
            if (d == null || d.dim() == null || d.keys() == null || d.values() == null
                    || d.keys().length != d.values().length) continue;
            var chunks = byDim.computeIfAbsent(d.dim(), x -> new LinkedHashMap<>(256, 0.75f, true));
            for (int i = 0; i < d.keys().length && chunks.size() < MAX_PER_DIM; i++) {
                if ((d.values()[i] & 3) != 0) chunks.put(d.keys()[i], d.values()[i]);
            }
        }
    }
}
