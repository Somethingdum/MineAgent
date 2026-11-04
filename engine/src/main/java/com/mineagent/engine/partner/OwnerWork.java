package com.mineagent.engine.partner;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What its owner is doing, from what the server sees them do (SMH plan 70, MA10.4): the blocks they break and place in
 * the last half minute. Mining is stone and ore broken; building is blocks placed, the most of one kind. Pure: the
 * agent loop feeds it from the game's block events and acts on the guess only when it has nothing else to do.
 */
public final class OwnerWork {
    public enum Doing { NOTHING, MINING, BUILDING, FARMING }

    /** Crops: breaking one is a harvest, placing one is planting (not building). */
    static final Set<String> CROPS = Set.of("minecraft:wheat", "minecraft:carrots", "minecraft:potatoes", "minecraft:beetroots",
            "minecraft:melon", "minecraft:pumpkin", "minecraft:sugar_cane", "minecraft:nether_wart", "minecraft:sweet_berry_bush");

    /** How far back it looks, game ticks. */
    public static final long WINDOW = 600;
    /** Breaks (or places) in the window that make a guess. */
    public static final int ENOUGH = 4;

    private record Event(long tick, boolean placed, String block, int x, int y, int z) {}

    private final Deque<Event> events = new ArrayDeque<>();

    /** Underground blocks: what mining breaks. */
    static final Set<String> STONE = Set.of("minecraft:stone", "minecraft:deepslate", "minecraft:cobblestone", "minecraft:tuff",
            "minecraft:granite", "minecraft:diorite", "minecraft:andesite", "minecraft:netherrack", "minecraft:cobbled_deepslate",
            "minecraft:calcite", "minecraft:dripstone_block", "minecraft:blackstone", "minecraft:basalt", "minecraft:gravel");

    public synchronized void broke(String block, int x, int y, int z, long tick) {
        add(new Event(tick, false, block, x, y, z));
    }

    public synchronized void placed(String block, int x, int y, int z, long tick) {
        add(new Event(tick, true, block, x, y, z));
    }

    private void add(Event e) {
        events.addLast(e);
        while (events.size() > 256) events.removeFirst();
    }

    private void prune(long now) {
        while (!events.isEmpty() && now - events.peekFirst().tick() > WINDOW) events.removeFirst();
    }

    /** The guess now. */
    public synchronized Doing doing(long now) {
        prune(now);
        int mined = 0, placed = 0, farmed = 0;
        for (Event e : events) {
            if (CROPS.contains(e.block())) farmed++;
            else if (e.placed()) placed++;
            else if (STONE.contains(e.block()) || e.block().endsWith("_ore")) mined++;
        }
        if (farmed >= ENOUGH && farmed >= placed && farmed >= mined) return Doing.FARMING;
        if (placed >= ENOUGH && placed >= mined) return Doing.BUILDING;
        if (mined >= ENOUGH) return Doing.MINING;
        return Doing.NOTHING;
    }

    /** The block they have placed most in the window, or null. */
    public synchronized String placing(long now) {
        prune(now);
        Map<String, Integer> n = new HashMap<>();
        for (Event e : events) if (e.placed() && !CROPS.contains(e.block())) n.merge(e.block(), 1, Integer::sum);
        return n.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
    }

    /** The crop they have been working most in the window ("minecraft:wheat"), or null. */
    public synchronized String farming(long now) {
        prune(now);
        Map<String, Integer> n = new HashMap<>();
        for (Event e : events) if (CROPS.contains(e.block())) n.merge(e.block(), 1, Integer::sum);
        return n.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
    }

    /** Where they have broken blocks in the window, oldest first (the tunnel behind them). */
    public synchronized List<int[]> trail(long now) {
        prune(now);
        List<int[]> out = new ArrayList<>();
        for (Event e : events) if (!e.placed()) out.add(new int[] {e.x(), e.y(), e.z()});
        return out;
    }
}
