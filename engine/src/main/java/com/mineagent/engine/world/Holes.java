package com.mineagent.engine.world;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Leaving no trace (SMH plan 70, MA9.4): the holes it dug in the ground near home, the top block of the ground it broke
 * there, and what was there, so they are filled when it has nothing else to do. Kept with the world. Pure.
 */
public final class Holes {
    public static final int MAX = 256;

    public record Hole(String dim, int x, int y, int z, String block, long tick) {}

    public record State(List<Hole> holes) {}

    private final LinkedHashMap<String, Hole> holes = new LinkedHashMap<>();

    private static String key(String dim, int x, int y, int z) {
        return dim + "|" + x + "|" + y + "|" + z;
    }

    public synchronized void dug(String dim, int x, int y, int z, String block, long tick) {
        holes.put(key(dim, x, y, z), new Hole(dim, x, y, z, block, tick));
        while (holes.size() > MAX) holes.remove(holes.keySet().iterator().next());
    }

    /** Filled (by it, or by anyone: the game shows it is not air any more). */
    public synchronized void filled(String dim, int x, int y, int z) {
        holes.remove(key(dim, x, y, z));
    }

    /** The holes within {@code radius} of a point, nearest first, at most {@code limit}. */
    public synchronized List<Hole> near(String dim, int x, int y, int z, double radius, int limit) {
        List<Hole> out = new ArrayList<>();
        for (Hole h : holes.values()) {
            if (!h.dim().equals(dim)) continue;
            double dx = h.x() - x, dy = h.y() - y, dz = h.z() - z;
            if (dx * dx + dy * dy + dz * dz <= radius * radius) out.add(h);
        }
        out.sort((a, b) -> Double.compare(d2(a, x, y, z), d2(b, x, y, z)));
        return out.size() > limit ? out.subList(0, limit) : out;
    }

    private static double d2(Hole h, int x, int y, int z) {
        double dx = h.x() - x, dy = h.y() - y, dz = h.z() - z;
        return dx * dx + dy * dy + dz * dz;
    }

    public synchronized int size() {
        return holes.size();
    }

    public synchronized State exportState() {
        return new State(new ArrayList<>(holes.values()));
    }

    public synchronized void importState(State s) {
        holes.clear();
        if (s == null || s.holes() == null) return;
        for (Hole h : s.holes()) if (h != null && h.dim() != null && h.block() != null && holes.size() < MAX) holes.put(key(h.dim(), h.x(), h.y(), h.z()), h);
    }

    /** The plain block that fills a hole of this kind when it doesn't carry the block itself: dirt for earth, cobblestone for rock. */
    public static String filler(String block) {
        if (block == null) return "minecraft:dirt";
        String p = block.substring(block.indexOf(':') + 1);
        if (p.equals("grass_block") || p.equals("dirt") || p.equals("coarse_dirt") || p.equals("podzol") || p.equals("rooted_dirt")
                || p.equals("mycelium") || p.equals("mud") || p.equals("farmland") || p.equals("dirt_path")) return "minecraft:dirt";
        if (p.equals("sand") || p.equals("red_sand")) return block;
        if (p.equals("stone") || p.equals("deepslate") || p.equals("andesite") || p.equals("diorite") || p.equals("granite") || p.equals("tuff")) {
            return "minecraft:cobblestone";
        }
        return block;
    }
}
