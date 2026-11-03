package com.mineagent.engine.memory.store;

/** A 512-by-512-block region of one dimension, as the game stores chunks (SMH plan 70, MA5.7). */
public record RegionKey(String dim, int rx, int rz) {
    public static RegionKey ofBlock(String dim, int x, int z) {
        return new RegionKey(dim, x >> 9, z >> 9);
    }

    public static RegionKey ofChunk(String dim, int cx, int cz) {
        return new RegionKey(dim, cx >> 5, cz >> 5);
    }

    /** The dimension as a folder name. */
    public String folder() {
        return (dim == null ? "unknown" : dim).replace(':', '_').replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    /** Distance in blocks from x, z to the nearest point of this region (0 inside it). */
    public double distanceFrom(int x, int z) {
        int minX = rx << 9, minZ = rz << 9, maxX = minX + 511, maxZ = minZ + 511;
        double dx = x < minX ? minX - x : x > maxX ? x - maxX : 0;
        double dz = z < minZ ? minZ - z : z > maxZ ? z - maxZ : 0;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public static RegionKey parse(String s) {
        int a = s.lastIndexOf('|'), b = s.lastIndexOf('|', a - 1);
        return new RegionKey(s.substring(0, b), Integer.parseInt(s.substring(b + 1, a)), Integer.parseInt(s.substring(a + 1)));
    }

    public String id() {
        return dim + "|" + rx + "|" + rz;
    }
}
