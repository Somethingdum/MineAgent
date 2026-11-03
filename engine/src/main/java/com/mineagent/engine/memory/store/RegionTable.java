package com.mineagent.engine.memory.store;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A store table split by region (SMH plan 70, MA5.7): the regions in use stay in memory, at most {@code maxLoaded},
 * the one used longest ago going first (written out if it changed); others are read from disk when needed. Without a
 * store (in tests, or no world folder) it simply keeps everything in memory.
 */
public abstract class RegionTable<R> implements MemoryStore.Table {
    public static final int VERSION = 1;

    protected final MemoryStore store;
    private final String name;
    private final int maxLoaded;
    private final LinkedHashMap<RegionKey, R> loaded = new LinkedHashMap<>(16, 0.75f, true);
    private final Set<RegionKey> dirty = new HashSet<>();
    private final Map<RegionKey, String> evicted = new HashMap<>();

    protected RegionTable(MemoryStore store, String name, int maxLoaded) {
        this.store = store;
        this.name = name;
        this.maxLoaded = Math.max(1, maxLoaded);
    }

    @Override
    public String name() {
        return name;
    }

    protected abstract R empty();

    protected abstract String encode(R region);

    protected abstract R decode(String body);

    /** The region, from memory or disk (an empty one if never seen). */
    protected synchronized R region(RegionKey key) {
        R r = loaded.get(key);
        if (r != null) return r;
        String pending = evicted.remove(key);
        if (pending != null) {
            r = decode(pending);
            dirty.add(key);
        } else if (store != null) {
            r = store.read(path(key), name).map(this::decode).orElseGet(this::empty);
        } else {
            r = empty();
        }
        loaded.put(key, r);
        if (store != null) {
            while (loaded.size() > maxLoaded) {
                var eldest = loaded.entrySet().iterator().next();
                if (dirty.remove(eldest.getKey())) evicted.put(eldest.getKey(), encode(eldest.getValue()));
                loaded.remove(eldest.getKey());
            }
        }
        return r;
    }

    /** It changed: written at the next flush. */
    protected synchronized void touched(RegionKey key) {
        dirty.add(key);
    }

    protected synchronized int loadedCount() {
        return loaded.size();
    }

    protected String path(RegionKey key) {
        return name + "/" + key.folder() + "/r." + key.rx() + "." + key.rz() + ".gz";
    }

    /** Extra files the table keeps beside its regions (its index), when they changed. */
    protected Map<String, MemoryStore.Segment> dirtyExtras() {
        return Map.of();
    }

    @Override
    public synchronized Map<String, MemoryStore.Segment> takeDirty() {
        Map<String, MemoryStore.Segment> out = new LinkedHashMap<>();
        for (RegionKey k : dirty) {
            R r = loaded.get(k);
            if (r != null) out.put(path(k), new MemoryStore.Segment(name, VERSION, encode(r)));
        }
        for (var e : evicted.entrySet()) out.put(path(e.getKey()), new MemoryStore.Segment(name, VERSION, e.getValue()));
        dirty.clear();
        evicted.clear();
        out.putAll(dirtyExtras());
        return out;
    }
}
