package com.mineagent.engine.learning;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Places it keeps dying (SMH plan 70, MA8.6): three deaths within 16 blocks of each other in 15 minutes mark the
 * place dangerous for three days; its paths go round it (the pathfinder prices it as it prices a mob) and it says so.
 * Pure, one record per companion.
 */
public final class DangerZones {
    public record Death(String dim, int x, int y, int z, long tick) {}

    public record Zone(String dim, int x, int y, int z, long since) {}

    static final int DEATHS = 3;
    static final long WINDOW = 15 * 1_200L;
    static final double NEAR = 16.0;
    static final long KEEP = 3 * 24_000L;
    /** How far round a zone its paths keep off, and how hard (blocks of walking at its middle). */
    public static final double RADIUS = 12.0, WEIGHT = 60.0;

    private static final Map<UUID, DangerZones> BY_BODY = new ConcurrentHashMap<>();

    private final List<Death> deaths = new ArrayList<>();
    private final List<Zone> zones = new ArrayList<>();

    public static DangerZones of(UUID body) {
        return BY_BODY.computeIfAbsent(body, k -> new DangerZones());
    }

    /** A death; the zone it makes when it is the third close by within 15 minutes (and none is there yet). */
    public synchronized Optional<Zone> died(String dim, int x, int y, int z, long tick) {
        deaths.removeIf(d -> tick - d.tick() > WINDOW);
        deaths.add(new Death(dim, x, y, z, tick));
        int near = 0;
        long sx = 0, sy = 0, sz = 0;
        for (Death d : deaths) {
            if (!d.dim().equals(dim) || dist(d.x(), d.y(), d.z(), x, y, z) > NEAR) continue;
            near++;
            sx += d.x();
            sy += d.y();
            sz += d.z();
        }
        if (near < DEATHS || dangerous(dim, x, y, z, tick)) return Optional.empty();
        Zone zone = new Zone(dim, (int) (sx / near), (int) (sy / near), (int) (sz / near), tick);
        zones.add(zone);
        return Optional.of(zone);
    }

    /** The zones still in force in a dimension. */
    public synchronized List<Zone> zones(String dim, long tick) {
        zones.removeIf(z -> tick - z.since() > KEEP);
        List<Zone> out = new ArrayList<>();
        for (Zone z : zones) if (z.dim().equals(dim)) out.add(z);
        return out;
    }

    public synchronized boolean dangerous(String dim, int x, int y, int z, long tick) {
        for (Zone zone : zones(dim, tick)) if (dist(zone.x(), zone.y(), zone.z(), x, y, z) <= RADIUS) return true;
        return false;
    }

    private static double dist(int ax, int ay, int az, int bx, int by, int bz) {
        double dx = ax - bx, dy = ay - by, dz = az - bz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
