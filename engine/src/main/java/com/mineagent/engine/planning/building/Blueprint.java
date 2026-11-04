package com.mineagent.engine.planning.building;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A building as blocks to place (SMH plan 70, MA9.1): each at an offset from the corner, on ground at y 0, in the
 * order a player builds it (walls from the bottom up, the roof from the walls in, the door and the torch last). Pure:
 * the builder places and reads back each one; the checker counts the materials and finds what has nothing to stand on.
 */
public record Blueprint(String name, int width, int depth, int height, List<Block> blocks, int insideX, int insideZ,
                        int doorX, int doorZ) {
    /** One block: where, what, and whether it goes in from outside (a door, placed from in front). */
    public record Block(int x, int y, int z, String id, boolean fromOutside) {}

    /** The materials, item by item. */
    public Map<String, Integer> materials() {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Block b : blocks) out.merge(item(b.id()), 1, Integer::sum);
        return out;
    }

    /** What to carry for a block: a door is one item for two halves; wall torches are torches. */
    static String item(String id) {
        return id.replace("wall_torch", "torch");
    }

    /**
     * Blocks with nothing under or beside them to be placed against, in the build order: none for a sound blueprint.
     * The ground (y 0) holds the first layer.
     */
    public List<Block> unsupported() {
        java.util.Set<String> there = new java.util.HashSet<>();
        List<Block> out = new ArrayList<>();
        for (Block b : blocks) {
            boolean held = b.y() == 1
                    || there.contains(key(b.x(), b.y() - 1, b.z())) || there.contains(key(b.x() + 1, b.y(), b.z()))
                    || there.contains(key(b.x() - 1, b.y(), b.z())) || there.contains(key(b.x(), b.y(), b.z() + 1))
                    || there.contains(key(b.x(), b.y(), b.z() - 1)) || there.contains(key(b.x(), b.y() + 1, b.z()));
            if (!held) out.add(b);
            there.add(key(b.x(), b.y(), b.z()));
        }
        return out;
    }

    private static String key(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    /**
     * The hut: 5 by 5, walls two high of {@code wall}, a flat roof of it, a door in the middle of the front (z 0) and a
     * torch inside. 55 of the wall block, a door, a torch. Headroom two blocks, as a player needs.
     */
    public static Blueprint hut(String wall, String door) {
        List<Block> b = new ArrayList<>();
        // Each layer's corners first: a corner placed after both its neighbours is in a pocket no face of which can be
        // seen from inside (the ground's top face is hidden behind them).
        for (int y = 1; y <= 2; y++) {
            List<Block> layer = new ArrayList<>();
            for (int x = 0; x < 5; x++) {
                for (int z = 0; z < 5; z++) {
                    boolean edge = x == 0 || x == 4 || z == 0 || z == 4;
                    boolean doorway = x == 2 && z == 0;
                    if (edge && !doorway) layer.add(new Block(x, y, z, wall, false));
                }
            }
            layer.sort(Comparator.comparingInt((Block r) -> corner(r) ? 0 : 1).thenComparingInt(r -> r.x() * 5 + r.z()));
            b.addAll(layer);
        }
        // The roof: from the walls in, ring by ring (corners first), so each block has one beside it, or the wall under
        // it, to go against, with that face in sight from inside.
        List<Block> roof = new ArrayList<>();
        for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) roof.add(new Block(x, 3, z, wall, false));
        roof.sort(Comparator.comparingInt((Block r) -> Math.min(Math.min(r.x(), 4 - r.x()), Math.min(r.z(), 4 - r.z())))
                .thenComparingInt(r -> corner(r) ? 0 : 1)
                .thenComparingInt(r -> r.x() * 5 + r.z()));
        b.addAll(roof);
        b.add(new Block(1, 1, 1, "minecraft:torch", false));
        b.add(new Block(2, 1, 0, door, true));
        return new Blueprint("hut", 5, 5, 3, List.copyOf(b), 2, 2, 2, 0);
    }

    /**
     * The hut as a home (SMH plan 70: its base when it lives on its own): the same, with a crafting table and a furnace in
     * its two back corners inside, put down after the roof; the middle and the spots beside it, where it stands to build
     * and to sleep, left clear.
     */
    public static Blueprint homeHut(String wall, String door) {
        Blueprint hut = hut(wall, door);
        List<Block> b = new ArrayList<>(hut.blocks());
        int torch = b.indexOf(new Block(1, 1, 1, "minecraft:torch", false));
        b.add(torch, new Block(3, 1, 3, "minecraft:furnace", false));
        b.add(torch, new Block(1, 1, 3, "minecraft:crafting_table", false));
        return new Blueprint("hut_home", hut.width(), hut.depth(), hut.height(), List.copyOf(b), hut.insideX(), hut.insideZ(), hut.doorX(), hut.doorZ());
    }

    /** A corner of the 5 by 5 hut. */
    private static boolean corner(Block r) {
        return (r.x() == 0 || r.x() == 4) && (r.z() == 0 || r.z() == 4);
    }
}
