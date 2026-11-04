package com.mineagent.engine.planning.building;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Two buildings from Mindcraft's blueprint library (SMH plan 70, MA9.1): {@code dirt_shelter} and
 * {@code small_wood_house}, their layouts as Mindcraft draws them (layers from the floor up, rows front to back, blocks
 * left to right), turned into this mod's blueprints: the floor layer is the ground it stands on, built above ground;
 * walls from the bottom up, corners first, the roof from the walls in, the torches, chest and door last. Beds are left
 * out (a bed is two blocks whose halves depend on the way it is put down).
 *
 * <p>The layouts are from Mindcraft (https://github.com/kolbytn/mindcraft, {@code src/agent/npc/construction}), under
 * its licence:
 *
 * <pre>
 * MIT License
 *
 * Copyright (c) 2024 Kolby Nottingham
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 * </pre>
 * Pure.
 */
public final class MindcraftBlueprints {
    private MindcraftBlueprints() {}

    /** Mindcraft's dirt_shelter, layers from the floor up ("" is anything, "air" must be clear). */
    static final String[][][] DIRT_SHELTER = {
            {{"", "", "", "", ""}, {"", "dirt", "dirt", "dirt", ""}, {"", "dirt", "dirt", "dirt", ""}, {"", "dirt", "dirt", "dirt", ""},
                    {"", "", "dirt", "", ""}, {"", "", "dirt", "", ""}},
            {{"dirt", "dirt", "dirt", "dirt", "dirt"}, {"dirt", "chest", "bed", "air", "dirt"}, {"dirt", "air", "bed", "air", "dirt"},
                    {"dirt", "air", "air", "air", "dirt"}, {"dirt", "dirt", "door", "dirt", "dirt"}, {"dirt", "dirt", "air", "dirt", "dirt"}},
            {{"dirt", "dirt", "dirt", "dirt", "dirt"}, {"dirt", "air", "air", "air", "dirt"}, {"dirt", "torch", "air", "air", "dirt"},
                    {"dirt", "air", "air", "air", "dirt"}, {"dirt", "dirt", "door", "dirt", "dirt"}, {"air", "air", "air", "air", "air"}},
            {{"air", "air", "air", "air", "air"}, {"dirt", "dirt", "dirt", "dirt", "dirt"}, {"dirt", "dirt", "dirt", "dirt", "dirt"},
                    {"dirt", "dirt", "dirt", "dirt", "dirt"}, {"air", "air", "air", "air", "air"}, {"air", "air", "air", "air", "air"}}};

    /** Mindcraft's small_wood_house. */
    static final String[][][] SMALL_WOOD_HOUSE = {
            {{"", "", "", "", ""}, {"", "planks", "planks", "planks", ""}, {"", "planks", "planks", "planks", ""},
                    {"", "planks", "planks", "planks", ""}, {"", "planks", "planks", "planks", ""}, {"", "", "planks", "", ""}, {"", "", "", "", ""}},
            {{"log", "planks", "planks", "planks", "log"}, {"planks", "chest", "bed", "air", "planks"}, {"planks", "air", "bed", "air", "planks"},
                    {"planks", "air", "air", "air", "planks"}, {"planks", "air", "air", "air", "planks"}, {"log", "planks", "door", "planks", "log"},
                    {"", "air", "air", "air", ""}},
            {{"log", "planks", "planks", "planks", "log"}, {"planks", "torch", "air", "torch", "planks"}, {"planks", "air", "air", "air", "planks"},
                    {"planks", "air", "air", "air", "planks"}, {"planks", "torch", "air", "torch", "planks"}, {"log", "planks", "door", "planks", "log"},
                    {"", "air", "air", "air", ""}},
            {{"air", "air", "air", "air", "air"}, {"air", "planks", "planks", "planks", "air"}, {"planks", "planks", "planks", "planks", "planks"},
                    {"planks", "planks", "planks", "planks", "planks"}, {"air", "planks", "planks", "planks", "air"}, {"air", "air", "air", "air", "air"},
                    {"", "air", "air", "air", ""}}};

    public static Blueprint dirtShelter() {
        return convert("dirt_shelter", DIRT_SHELTER, "minecraft:dirt", "minecraft:oak_log", "minecraft:oak_door");
    }

    /** In the wood it has most of: {@code planks}, its log and its door. */
    public static Blueprint smallWoodHouse(String planks, String door) {
        String log = planks.endsWith("_planks") ? planks.replace("_planks", "_log") : "minecraft:oak_log";
        if (planks.contains("crimson") || planks.contains("warped")) log = planks.replace("_planks", "_stem");
        if (planks.contains("bamboo")) log = "minecraft:bamboo_block";
        return convert("small_wood_house", SMALL_WOOD_HOUSE, planks, log, door);
    }

    /**
     * Mindcraft's layers as this mod's blueprint. The layout's rows are turned so its front row (the door's) is z 0, as
     * the hut's is: Mindcraft draws the door at the far row.
     */
    static Blueprint convert(String name, String[][][] layers, String planks, String log, String door) {
        int depth = layers[0].length, width = layers[0][0].length;
        // The row the door is in: the front.
        int doorRow = -1, doorCol = -1;
        for (int y = 1; y < layers.length && doorRow < 0; y++) {
            for (int r = 0; r < depth; r++) for (int c = 0; c < width; c++) {
                if ("door".equals(layers[y][r][c]) && doorRow < 0) {
                    doorRow = r;
                    doorCol = c;
                }
            }
        }
        boolean flip = doorRow > depth / 2;
        List<Blueprint.Block> walls = new ArrayList<>(), roof = new ArrayList<>(), last = new ArrayList<>();
        int top = layers.length - 1;
        for (int y = 1; y < layers.length; y++) {
            for (int r = 0; r < depth; r++) {
                for (int c = 0; c < width; c++) {
                    String w = layers[y][r][c];
                    int z = flip ? depth - 1 - r : r;
                    String id = switch (w) {
                        case "dirt" -> "minecraft:dirt";
                        case "planks" -> planks;
                        case "log" -> log;
                        case "chest" -> "minecraft:chest";
                        default -> null;
                    };
                    if (id != null) {
                        (y == top ? roof : walls).add(new Blueprint.Block(c, y, z, id, false));
                    } else if ("torch".equals(w)) {
                        // On the floor beneath, when that is clear: a torch needs ground to stand on.
                        if ("air".equals(layers[1][r][c]) && y > 1) last.add(new Blueprint.Block(c, 1, z, "minecraft:torch", false));
                        else if (y == 1) last.add(new Blueprint.Block(c, 1, z, "minecraft:torch", false));
                    }
                }
            }
        }
        int dz = flip ? depth - 1 - doorRow : doorRow;
        walls.sort(Comparator.comparingInt(Blueprint.Block::y).thenComparingInt((Blueprint.Block b) -> corner(b, width, depth) ? 0 : 1)
                .thenComparingInt(b -> b.x() * depth + b.z()));
        roof.sort(Comparator.comparingInt((Blueprint.Block b) -> Math.min(Math.min(b.x(), width - 1 - b.x()), Math.min(b.z(), depth - 1 - b.z())))
                .thenComparingInt(b -> corner(b, width, depth) ? 0 : 1).thenComparingInt(b -> b.x() * depth + b.z()));
        List<Blueprint.Block> all = new ArrayList<>(walls);
        all.addAll(supported(roof, walls));
        // A chest is furniture: in last, with the torches, before the door.
        List<Blueprint.Block> chests = new ArrayList<>();
        all.removeIf(b -> {
            if (b.id().equals("minecraft:chest")) {
                chests.add(b);
                return true;
            }
            return false;
        });
        all.addAll(last);
        all.addAll(chests);
        if (doorRow >= 0) all.add(new Blueprint.Block(doorCol, 1, dz, door, true));
        // Inside: a clear cell on the floor, away from the door.
        int ix = width / 2, iz = depth / 2;
        for (int r = 0; r < depth; r++) {
            for (int c = 0; c < width; c++) {
                int z = flip ? depth - 1 - r : r;
                if ("air".equals(layers[1][r][c]) && "air".equals(layers[2][r][c]) && c > 0 && c < width - 1 && z > 0 && z < depth - 1
                        && Math.abs(z - dz) > 1) {
                    ix = c;
                    iz = z;
                }
            }
        }
        return new Blueprint(name, width, depth, top, List.copyOf(all), ix, iz, doorCol, dz);
    }

    /**
     * The roof in an order where each block has one placed under or beside it to go against: the ring order, but a block
     * that has none yet waits for the next that does.
     */
    static List<Blueprint.Block> supported(List<Blueprint.Block> roof, List<Blueprint.Block> under) {
        java.util.Set<String> placed = new java.util.HashSet<>();
        for (var b : under) placed.add(b.x() + "," + b.y() + "," + b.z());
        List<Blueprint.Block> left = new ArrayList<>(roof), out = new ArrayList<>();
        while (!left.isEmpty()) {
            Blueprint.Block next = null;
            for (var b : left) {
                if (placed.contains(b.x() + "," + (b.y() - 1) + "," + b.z()) || placed.contains((b.x() + 1) + "," + b.y() + "," + b.z())
                        || placed.contains((b.x() - 1) + "," + b.y() + "," + b.z()) || placed.contains(b.x() + "," + b.y() + "," + (b.z() + 1))
                        || placed.contains(b.x() + "," + b.y() + "," + (b.z() - 1))) {
                    next = b;
                    break;
                }
            }
            if (next == null) next = left.get(0);
            left.remove(next);
            out.add(next);
            placed.add(next.x() + "," + next.y() + "," + next.z());
        }
        return out;
    }

    private static boolean corner(Blueprint.Block b, int width, int depth) {
        return (b.x() == 0 || b.x() == width - 1) && (b.z() == 0 || b.z() == depth - 1);
    }
}
