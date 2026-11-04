package com.mineagent.engine.gear;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What in its pack is junk (SMH plan 70: "manage its inventory intelligently, dumping junk items it doesn't need in lava,
 * or the ocean"): the rock and dirt a dig fills a pack with, past what it keeps for bridging and pillaring, and the mob
 * drops nobody needs past what trades or brews use. Nothing enchanted or named is ever junk (the caller leaves those
 * out), nor anything its stockpile keeps. Pure.
 */
public final class Junk {
    private Junk() {}

    /** One thing to throw away, and how many. */
    public record Excess(String item, int count) {}

    /** How many of each it keeps; past that it is junk. Not here: never junk. */
    public static final Map<String, Integer> KEEP = Map.ofEntries(
            // A stack and more of plain rock for bridges, pillars and furnaces; the rest of a dig goes.
            Map.entry("minecraft:cobblestone", 128), Map.entry("minecraft:cobbled_deepslate", 64), Map.entry("minecraft:netherrack", 64),
            Map.entry("minecraft:dirt", 16), Map.entry("minecraft:coarse_dirt", 0), Map.entry("minecraft:gravel", 16),
            Map.entry("minecraft:diorite", 0), Map.entry("minecraft:andesite", 0), Map.entry("minecraft:granite", 0),
            Map.entry("minecraft:tuff", 0), Map.entry("minecraft:calcite", 0), Map.entry("minecraft:dripstone_block", 0),
            Map.entry("minecraft:pointed_dripstone", 0),
            // Mob drops: rotten flesh sells to clerics, spider eyes make weakness; a poisonous potato is nothing.
            Map.entry("minecraft:rotten_flesh", 32), Map.entry("minecraft:spider_eye", 16), Map.entry("minecraft:poisonous_potato", 0));

    /** Less junk than this all told isn't worth the walk to the lava. */
    public static final int WORTH_A_TRIP = 64;

    /**
     * The junk in a pack ({@code has}: item id to count, what may be thrown, nothing enchanted or named in it), less
     * anything {@code keep} says to keep (its stockpile's targets, an order's needs).
     */
    public static List<Excess> excess(Map<String, Integer> has, Set<String> keep) {
        List<Excess> out = new ArrayList<>();
        for (var e : has.entrySet()) {
            Integer limit = KEEP.get(e.getKey());
            if (limit == null || keep.contains(e.getKey())) continue;
            int over = e.getValue() - limit;
            if (over > 0) out.add(new Excess(e.getKey(), over));
        }
        out.sort((a, b) -> Integer.compare(b.count(), a.count()));
        return out;
    }

    /** How much junk all told. */
    public static int total(List<Excess> junk) {
        int n = 0;
        for (Excess e : junk) n += e.count();
        return n;
    }
}
