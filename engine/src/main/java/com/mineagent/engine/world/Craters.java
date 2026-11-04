package com.mineagent.engine.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Creeper holes near home (SMH plan 70): what a blast took that was ground (earth, sand, gravel, rock), to fill back
 * with what the ground there is mostly made of on top (grass gives dirt, as a player puts it back), from the bottom up.
 * Pure.
 */
public final class Craters {
    private Craters() {}

    static final Set<String> EARTH = Set.of("grass_block", "dirt", "coarse_dirt", "podzol", "rooted_dirt", "mycelium", "mud", "farmland",
            "dirt_path", "clay", "moss_block");
    static final Set<String> LOOSE = Set.of("sand", "red_sand", "gravel", "snow_block", "suspicious_sand", "suspicious_gravel");
    static final Set<String> ROCK = Set.of("stone", "cobblestone", "deepslate", "cobbled_deepslate", "andesite", "diorite", "granite",
            "tuff", "calcite", "sandstone", "red_sandstone", "terracotta", "netherrack", "end_stone", "blackstone", "basalt");

    /** Whether a block ("minecraft:dirt") is ground a blast leaves a hole in, not something built or growing. */
    public static boolean ground(String id) {
        if (id == null) return false;
        String p = id.substring(id.indexOf(':') + 1);
        return EARTH.contains(p) || LOOSE.contains(p) || ROCK.contains(p) || p.endsWith("_terracotta") && !p.contains("glazed");
    }

    /** The commonest ground block on top round the blast ({@code tops}: block id to columns), or null with none. */
    public static String surface(Map<String, Integer> tops) {
        return tops.entrySet().stream().filter(e -> ground(e.getKey()))
                .max(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue).thenComparing(Map.Entry::getKey, Comparator.reverseOrder()))
                .map(Map.Entry::getKey).orElse(null);
    }

    /** Holes in the order to fill them: the bottom first, then by distance from {@code (x, z)}. */
    public static List<int[]> bottomUp(List<int[]> holes, int x, int z) {
        List<int[]> out = new ArrayList<>(holes);
        out.sort(Comparator.<int[]>comparingInt(h -> h[1]).thenComparingDouble(h -> (h[0] - x) * (double) (h[0] - x) + (h[2] - z) * (double) (h[2] - z)));
        return out;
    }
}
