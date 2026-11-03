package com.mineagent.engine.memory;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mineagent.engine.memory.store.MemoryStore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Chests and what is in them (SMH plan 70, MA5.7): for each chest it has looked in, what it held and when, up to
 * 4,096 chests by default; the one looked in longest ago goes first when it is full. Fed only by a real look inside
 * (opening it, or a transfer it made), never by seeing through walls. Answers "where did you put the iron?". Kept in the
 * memory store as one file, written when it changed. Pure.
 */
public final class ChestMemory implements MemoryStore.Table {
    public record Chest(String dim, int x, int y, int z, Map<String, Integer> items, long seen) {}

    /** A chest holding what was asked: which item it is, how many, how far. */
    public record Holding(Chest chest, String item, int count, double distance) {}

    private static final Gson GSON = new Gson();
    private final LinkedHashMap<String, Chest> chests = new LinkedHashMap<>(256, 0.75f, true);
    private final int max;
    private boolean dirty;

    public ChestMemory(MemoryStore store, int max) {
        this.max = Math.max(64, max);
        if (store != null) {
            store.read("chests/chests.gz", "chests").ifPresent(body -> {
                List<Chest> list = GSON.fromJson(body, new TypeToken<List<Chest>>() {}.getType());
                if (list != null) for (Chest c : list) if (c != null && c.dim() != null && c.items() != null) chests.put(key(c), c);
            });
            store.register(this);
        }
    }

    private static String key(String dim, int x, int y, int z) {
        return dim + "|" + x + "|" + y + "|" + z;
    }

    private static String key(Chest c) {
        return key(c.dim(), c.x(), c.y(), c.z());
    }

    /**
     * It looked inside: this is what the chest holds now (nothing: it is empty). Returns what changed since it last
     * looked, item to how many more (or fewer); empty the first time it looks.
     */
    public synchronized Map<String, Integer> looked(String dim, int x, int y, int z, Map<String, Integer> items, long tick) {
        if (dim == null) return Map.of();
        Map<String, Integer> clean = new LinkedHashMap<>();
        if (items != null) {
            for (var e : items.entrySet()) {
                if (e.getKey() != null && e.getValue() != null && e.getValue() > 0) clean.merge(Sightings.base(e.getKey()), e.getValue(), Integer::sum);
            }
        }
        Chest before = chests.get(key(dim, x, y, z));
        Map<String, Integer> change = new LinkedHashMap<>();
        if (before != null) {
            for (var e : clean.entrySet()) {
                int d = e.getValue() - before.items().getOrDefault(e.getKey(), 0);
                if (d != 0) change.put(e.getKey(), d);
            }
            for (var e : before.items().entrySet()) if (!clean.containsKey(e.getKey())) change.put(e.getKey(), -e.getValue());
        }
        chests.put(key(dim, x, y, z), new Chest(dim, x, y, z, clean, tick));
        while (chests.size() > max) {
            var oldest = chests.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
        dirty = true;
        return change;
    }

    /** The chest is gone (broken). */
    public synchronized void gone(String dim, int x, int y, int z) {
        if (chests.remove(key(dim, x, y, z)) != null) dirty = true;
    }

    /** Chests it knows within {@code max} blocks of x, y, z, nearest first. */
    public synchronized List<Chest> near(String dim, int x, int y, int z, double max) {
        List<Chest> out = new ArrayList<>();
        if (dim == null) return out;
        for (Chest c : chests.values()) {
            if (!dim.equals(c.dim())) continue;
            double d = Math.sqrt(Math.pow(c.x() - x, 2) + Math.pow(c.y() - y, 2) + Math.pow(c.z() - z, 2));
            if (d <= max) out.add(c);
        }
        out.sort(Comparator.comparingDouble(c -> Math.pow(c.x() - x, 2) + Math.pow(c.y() - y, 2) + Math.pow(c.z() - z, 2)));
        return out;
    }

    /** Chests holding what was asked ("iron", "iron ingot", "diamonds"), nearest first. */
    public synchronized List<Holding> holding(String words, String dim, int x, int y, int z, int limit) {
        String w = Sightings.fromWords(words);
        if (w.isEmpty()) return List.of();
        List<Holding> out = new ArrayList<>();
        for (Chest c : new ArrayList<>(chests.values())) {
            int n = 0;
            String most = null;
            int mostCount = 0;
            for (var e : c.items().entrySet()) {
                String item = e.getKey().toLowerCase(Locale.ROOT);
                if (item.equals(w) || item.startsWith(w + "_") || item.endsWith("_" + w)) {
                    n += e.getValue();
                    if (e.getValue() > mostCount) {
                        mostCount = e.getValue();
                        most = item;
                    }
                }
            }
            if (n == 0) continue;
            double d = dim != null && dim.equals(c.dim())
                    ? Math.sqrt(Math.pow(c.x() - x, 2) + Math.pow(c.y() - y, 2) + Math.pow(c.z() - z, 2)) : Double.MAX_VALUE;
            out.add(new Holding(c, most, n, d));
        }
        out.sort(Comparator.comparingDouble(Holding::distance));
        return out.size() > limit ? out.subList(0, limit) : out;
    }

    @Override public String name() { return "chests"; }

    @Override public void replay(String op, String json) { }

    @Override
    public synchronized Map<String, MemoryStore.Segment> takeDirty() {
        if (!dirty) return Map.of();
        dirty = false;
        return Map.of("chests/chests.gz", new MemoryStore.Segment("chests", 1, GSON.toJson(new ArrayList<>(chests.values()))));
    }

    @Override public synchronized long count() { return chests.size(); }
}
