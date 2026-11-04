package com.mineagent.engine.gear;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Hoarding (SMH plan 70, its owner: "a fair base, filled to the brim with valuables ... a metric ton of golden carrots,
 * golden apples, en gold apples, shields, weapons, ores, food. Generally hoarder behavior"): what it keeps in its own
 * stockpile, in the order it goes after it: food first, then what keeps it going (torches, iron, fuel, arrows, shields,
 * golden apples), then the valuables in bulk, and fuel for the long haul. Enchanted golden apples can't be made: every one found is kept. Pure.
 */
public final class Hoard {
    private Hoard() {}

    /** One thing to have so many of. */
    public record Target(String item, int count) {}

    /** The stockpile, in order. */
    public static final List<Target> TARGETS = List.of(
            new Target("minecraft:bread", 64), new Target("minecraft:cooked_beef", 64), new Target("minecraft:torch", 128),
            new Target("minecraft:iron_ingot", 128), new Target("minecraft:coal", 128), new Target("minecraft:arrow", 256),
            new Target("minecraft:shield", 2), new Target("minecraft:golden_apple", 16), new Target("minecraft:golden_carrot", 128),
            new Target("minecraft:diamond", 32), new Target("minecraft:obsidian", 32), new Target("minecraft:emerald", 64),
            new Target("minecraft:diamond_sword", 2), new Target("minecraft:bow", 2), new Target("minecraft:slime_ball", 64),
            // Then the metric ton.
            new Target("minecraft:golden_carrot", 512), new Target("minecraft:golden_apple", 64), new Target("minecraft:iron_ingot", 512),
            new Target("minecraft:gold_ingot", 256), new Target("minecraft:diamond", 128), new Target("minecraft:emerald", 256),
            new Target("minecraft:cooked_beef", 256), new Target("minecraft:arrow", 1024), new Target("minecraft:slime_ball", 576),
            new Target("minecraft:coal", 512));

    /**
     * What stands for the same in its stockpile: coal and charcoal burn alike (eight smelts each), so either counts toward
     * the fuel it keeps, and charcoal is made from logs when there is no coal to be had.
     */
    static final Map<String, List<String>> SAME = Map.of("minecraft:coal", List.of("minecraft:coal", "minecraft:charcoal"));

    /** The most it goes after in one go, so its owner's orders never wait long. */
    public static final int BATCH = 64;

    /** What to go after next: the first target short ({@code has}: what it holds and keeps) that {@code possible} can make. */
    public static Target next(Map<String, Integer> has, Predicate<Target> possible) {
        for (Target t : TARGETS) {
            List<String> kinds = SAME.getOrDefault(t.item(), List.of(t.item()));
            int held = 0;
            for (String k : kinds) held += has.getOrDefault(k, 0);
            int short_ = t.count() - held;
            if (short_ <= 0) continue;
            for (String k : kinds) {
                Target batch = new Target(k, Math.min(BATCH, short_));
                if (possible.test(batch)) return batch;
            }
        }
        return null;
    }
}
