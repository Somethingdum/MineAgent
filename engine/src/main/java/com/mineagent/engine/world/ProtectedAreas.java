package com.mineagent.engine.world;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Places its owner said not to touch (SMH plan 70, MA10.2: "don't touch my garden"): nothing is broken or placed within
 * an area's radius by any companion, as at an AFK farm. Each companion's loop keeps its own from the places it remembers
 * (kind "protected", saved with the world's memory) and sets them here. Pure.
 */
public final class ProtectedAreas {
    /** The radius around the spot they meant. */
    public static final double RADIUS = 8.0;

    public record Area(String name, String dim, int x, int y, int z, double radius) {
        public boolean holds(String d, int bx, int by, int bz) {
            if (!dim.equals(d)) return false;
            double dx = bx - x, dy = by - y, dz = bz - z;
            return dx * dx + dy * dy + dz * dz <= radius * radius;
        }
    }

    private static final Map<UUID, List<Area>> AREAS = new ConcurrentHashMap<>();

    private ProtectedAreas() {}

    /** A companion's areas, all of them (empty or null clears). */
    public static void set(UUID companion, List<Area> areas) {
        if (companion == null) return;
        if (areas == null || areas.isEmpty()) AREAS.remove(companion);
        else AREAS.put(companion, List.copyOf(areas));
    }

    /** The area holding the block, or null. */
    public static Area at(String dim, int x, int y, int z) {
        if (dim == null || AREAS.isEmpty()) return null;
        for (List<Area> list : AREAS.values()) for (Area a : list) if (a.holds(dim, x, y, z)) return a;
        return null;
    }

    public static boolean protects(String dim, int x, int y, int z) {
        return at(dim, x, y, z) != null;
    }

    /** For the tests. */
    static void clearAll() {
        AREAS.clear();
    }
}
