package com.mineagent.engine.world;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where players are AFK at a farm (SMH plan 70, MA5.4): no companion places or breaks a block within an area's
 * radius, so none lights a farm, walls it, or digs it up while its player is away. Each companion's loop sets its
 * owner's area while they're AFK there and clears it when they're back. Pure.
 */
public final class AfkFarms {
    public record Area(String dim, double x, double y, double z, double radius) {
        boolean holds(String d, int bx, int by, int bz) {
            if (!dim.equals(d)) return false;
            double dx = bx + 0.5 - x, dy = by + 0.5 - y, dz = bz + 0.5 - z;
            return dx * dx + dy * dy + dz * dz <= radius * radius;
        }
    }

    private static final Map<UUID, Area> AREAS = new ConcurrentHashMap<>();

    private AfkFarms() {}

    public static void set(UUID player, Area area) {
        if (player == null) return;
        if (area == null) AREAS.remove(player);
        else AREAS.put(player, area);
    }

    public static void clear(UUID player) {
        if (player != null) AREAS.remove(player);
    }

    /** Whether the block at x, y, z in {@code dim} is in a farm whose player is AFK. */
    public static boolean protects(String dim, int x, int y, int z) {
        if (dim == null || AREAS.isEmpty()) return false;
        for (Area a : AREAS.values()) if (a.holds(dim, x, y, z)) return true;
        return false;
    }

    /** For the tests. */
    static void clearAll() {
        AREAS.clear();
    }
}
