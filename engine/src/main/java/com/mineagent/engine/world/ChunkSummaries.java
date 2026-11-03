package com.mineagent.engine.world;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.mineagent.engine.memory.store.MemoryStore;
import com.mineagent.engine.memory.store.RegionKey;
import com.mineagent.engine.memory.store.RegionTable;

import java.util.HashMap;
import java.util.Map;

/**
 * What it has seen of each chunk (SMH plan 70, MA5.10 and MA5.7's explored map): land, water or something to avoid
 * (lava, fire), its surface height, and whether it is steep; unknown where it has never been. One number per chunk,
 * every chunk it has seen, kept in the memory store by region (32 by 32 chunks a file) and loaded when needed. Long
 * trips are planned over it, and "find a village" searches where it is unknown. Without a store it keeps everything in
 * memory.
 */
public final class ChunkSummaries extends RegionTable<int[]> {
    public enum Kind { UNKNOWN, LAND, WATER, HAZARD }

    public record Summary(Kind kind, int height, boolean steep) {}

    private static final Gson GSON = new Gson();
    /** Region id to chunks known in it. */
    private final Map<String, Integer> known = new HashMap<>();
    private boolean knownDirty;

    /** In memory only. */
    public ChunkSummaries() {
        this(null);
    }

    public ChunkSummaries(MemoryStore store) {
        super(store, "chunks", 64);
        if (store != null) {
            store.register(this);
            store.read("chunks/index.gz", "chunks-index").ifPresent(body -> {
                Map<String, Integer> m = GSON.fromJson(body, new TypeToken<Map<String, Integer>>() {}.getType());
                if (m != null) known.putAll(m);
            });
        }
    }

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

    private static int index(int cx, int cz) {
        return ((cx & 31) << 5) | (cz & 31);
    }

    public void put(String dim, int cx, int cz, Summary s) {
        if (dim == null || s == null || s.kind() == Kind.UNKNOWN) return;
        int v = pack(s);
        RegionKey rk = RegionKey.ofChunk(dim, cx, cz);
        synchronized (this) {
            int[] r = region(rk);
            int i = index(cx, cz);
            if (r[i] == v) return; // the same as before: nothing to write
            if (r[i] == 0) {
                known.merge(rk.id(), 1, Integer::sum);
                knownDirty = true;
            }
            r[i] = v;
            touched(rk);
        }
        if (store != null) {
            JsonObject o = new JsonObject();
            o.addProperty("dim", dim);
            o.addProperty("cx", cx);
            o.addProperty("cz", cz);
            o.addProperty("v", v);
            store.log(name(), "put", o.toString());
        }
    }

    /** What it knows of a chunk; UNKNOWN when it hasn't seen it. */
    public Summary get(String dim, int cx, int cz) {
        if (dim == null) return new Summary(Kind.UNKNOWN, 0, false);
        RegionKey rk = RegionKey.ofChunk(dim, cx, cz);
        synchronized (this) {
            // A region it has never seen anything in isn't read from disk at all.
            if (store != null && !known.containsKey(rk.id())) return new Summary(Kind.UNKNOWN, 0, false);
            int v = region(rk)[index(cx, cz)];
            return v == 0 ? new Summary(Kind.UNKNOWN, 0, false) : unpack(v);
        }
    }

    public boolean seen(String dim, int cx, int cz) {
        return get(dim, cx, cz).kind() != Kind.UNKNOWN;
    }

    public synchronized int size() {
        return known.values().stream().mapToInt(Integer::intValue).sum();
    }

    @Override
    public long count() {
        return size();
    }

    @Override
    protected int[] empty() {
        return new int[1024];
    }

    @Override
    protected String encode(int[] region) {
        StringBuilder sb = new StringBuilder(region.length * 2);
        for (int i = 0; i < region.length; i++) {
            if (i > 0) sb.append(',');
            if (region[i] != 0) sb.append(region[i]);
        }
        return sb.toString();
    }

    @Override
    protected int[] decode(String body) {
        int[] r = new int[1024];
        String[] parts = body.split(",", -1);
        for (int i = 0; i < Math.min(parts.length, 1024); i++) {
            if (!parts[i].isEmpty()) {
                try {
                    r[i] = Integer.parseInt(parts[i]);
                } catch (NumberFormatException ignored) {
                    // Unknown.
                }
            }
        }
        return r;
    }

    @Override
    protected synchronized Map<String, MemoryStore.Segment> dirtyExtras() {
        if (!knownDirty) return Map.of();
        knownDirty = false;
        return Map.of("chunks/index.gz", new MemoryStore.Segment("chunks-index", VERSION, GSON.toJson(known)));
    }

    @Override
    public void replay(String op, String json) {
        if (!"put".equals(op)) return;
        JsonObject o = GSON.fromJson(json, JsonObject.class);
        String dim = o.get("dim").getAsString();
        int cx = o.get("cx").getAsInt(), cz = o.get("cz").getAsInt(), v = o.get("v").getAsInt();
        RegionKey rk = RegionKey.ofChunk(dim, cx, cz);
        synchronized (this) {
            int[] r = region(rk);
            int i = index(cx, cz);
            if (r[i] == 0 && v != 0) {
                known.merge(rk.id(), 1, Integer::sum);
                knownDirty = true;
            }
            r[i] = v;
            touched(rk);
        }
    }
}
