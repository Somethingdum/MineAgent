// Ported from Mindcraft (github.com/mindcraft-bots/mindcraft, MIT, Copyright (c) 2024 Kolby Nottingham) at e1c1fc3:
// src/agent/npc/utils.js (getTypeOfGeneric, blockSatisfied, itemSatisfied, rotateXZ) with the lists from
// src/utils/mcdata.js (WOOD_TYPES, MATCHING_WOOD_BLOCKS, WOOL_COLORS), on the server's own blocks and items.
package com.mineagent.engine.agent.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class NpcUtils {

    private NpcUtils() {}

    public static final List<String> WOOD_TYPES = List.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry");
    public static final List<String> MATCHING_WOOD_BLOCKS = List.of("log", "planks", "sign", "boat", "fence_gate", "door", "fence",
            "slab", "stairs", "button", "pressure_plate", "trapdoor");
    public static final List<String> WOOL_COLORS = List.of("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");

    /** What it carries, by item name (world.getInventoryCounts). */
    public static Map<String, Integer> inventoryCounts(ServerPlayer player) {
        Map<String, Integer> out = new HashMap<>();
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            out.merge(BuiltInRegistries.ITEM.getKey(s.getItem()).getPath(), s.getCount(), Integer::sum);
        }
        return out;
    }

    /** A generic name made a real one: a wooden block of the wood it carries most, else of the nearest log, else oak; a bed of the wool it carries most, else white. */
    public static String typeOfGeneric(ServerPlayer player, String blockName) {
        if (MATCHING_WOOD_BLOCKS.contains(blockName)) {
            Map<String, Integer> typeCount = new HashMap<>();
            int maxCount = 0;
            String maxType = null;
            for (var e : inventoryCounts(player).entrySet()) {
                for (String wood : WOOD_TYPES) {
                    if (e.getKey().contains(wood)) {
                        int c = typeCount.merge(wood, e.getValue(), Integer::sum);
                        if (c > maxCount) {
                            maxCount = c;
                            maxType = wood;
                        }
                    }
                }
            }
            if (maxType != null) return maxType + "_" + blockName;
            String nearest = nearestLogWood(player, 16);
            if (nearest != null) return nearest + "_" + blockName;
            return "oak_" + blockName;
        }
        if (blockName.equals("bed")) {
            int maxCount = 0;
            String maxType = null;
            Map<String, Integer> inv = inventoryCounts(player);
            for (String color : WOOL_COLORS) {
                int c = inv.getOrDefault(color + "_wool", 0);
                if (c > maxCount) {
                    maxCount = c;
                    maxType = color;
                }
            }
            return (maxType != null ? maxType : "white") + "_" + blockName;
        }
        return blockName;
    }

    private static String nearestLogWood(ServerPlayer player, int range) {
        var level = player.serverLevel();
        BlockPos at = player.blockPosition();
        String best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-range, -range, -range), at.offset(range, range, range))) {
            if (level.getChunkSource().getChunkNow(p.getX() >> 4, p.getZ() >> 4) == null) continue;
            String id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(p).getBlock()).getPath();
            for (String wood : WOOD_TYPES) {
                if (id.equals(wood + "_log")) {
                    double d = p.distSqr(at);
                    if (d < bestD) {
                        bestD = d;
                        best = wood;
                    }
                }
            }
        }
        return best;
    }

    /** Whether the block there is what the blueprint asks for (dirt takes grass; a wooden block, any wood; any bed; any torch). */
    public static boolean blockSatisfied(String targetName, BlockState state) {
        String name = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        if (targetName.equals("dirt")) return name.equals("dirt") || name.equals("grass_block");
        if (MATCHING_WOOD_BLOCKS.contains(targetName)) return name.endsWith(targetName);
        if (targetName.equals("bed")) return name.endsWith("bed");
        if (targetName.equals("torch")) return name.contains("torch");
        return name.equals(targetName);
    }

    /** Whether it carries enough of an item; a tool of a better tier counts for a lesser one. */
    public static boolean itemSatisfied(ServerPlayer player, String item, int quantity) {
        List<String> qualifying = new java.util.ArrayList<>(List.of(item));
        if (item.contains("pickaxe") || item.contains("axe") || item.contains("shovel") || item.contains("hoe") || item.contains("sword")) {
            int cut = item.indexOf('_');
            String material = cut < 0 ? item : item.substring(0, cut);
            String type = cut < 0 ? "" : item.substring(cut + 1);
            List<String> better = switch (material) {
                case "wooden" -> List.of("stone", "iron", "gold", "diamond");
                case "stone" -> List.of("iron", "gold", "diamond");
                case "iron" -> List.of("gold", "diamond");
                case "gold" -> List.of("diamond");
                default -> List.of();
            };
            for (String m : better) qualifying.add(m + "_" + type);
        }
        Map<String, Integer> inv = inventoryCounts(player);
        for (String q : qualifying) if (inv.getOrDefault(q, 0) >= quantity) return true;
        return false;
    }

    /** A blueprint's x and z turned to one of four orientations. */
    public static int[] rotateXZ(int x, int z, int orientation, int sizeX, int sizeZ) {
        return switch (orientation) {
            case 1 -> new int[]{z, sizeX - x - 1};
            case 2 -> new int[]{sizeX - x - 1, sizeZ - z - 1};
            case 3 -> new int[]{sizeZ - z - 1, x};
            default -> new int[]{x, z};
        };
    }
}
