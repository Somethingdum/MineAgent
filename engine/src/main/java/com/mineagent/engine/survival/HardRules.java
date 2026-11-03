package com.mineagent.engine.survival;

import java.util.Set;

/**
 * The game's own rules on Hard, read from the world (SMH plan 70, MA8.7), as the companion lives by them. Pure.
 *
 * <ul>
 *   <li>Hunger kills on Hard (on Normal it stops at half a heart): its trip food doubles, and it never sets out on a
 *       trip with none.</li>
 *   <li>Drinking an ominous bottle and walking into a village starts a raid: it never drinks one.</li>
 *   <li>A sculk sensor hears footsteps within 8 blocks and a shrieker calls the warden: paths keep clear of them.</li>
 * </ul>
 */
public final class HardRules {
    private HardRules() {}

    /** Items it never uses, whatever it is told: drinking an ominous bottle starts a raid at the next village. */
    static final Set<String> NEVER_USE = Set.of("minecraft:ominous_bottle");
    /** Blocks whose hearing its paths keep out of (the deep dark). */
    static final Set<String> LISTENING = Set.of("minecraft:sculk_sensor", "minecraft:calibrated_sculk_sensor", "minecraft:sculk_shrieker");
    /** How far a sculk sensor hears a step. */
    public static final double HEARS = 9.0;

    /** The food it takes on a trip: twice as much on Hard. */
    public static int tripFood(int base, boolean hard) {
        return hard ? Math.min(64, base * 2) : base;
    }

    /** Whether it may set out on a trip carrying this much food: on Hard, never with none. */
    public static boolean maySetOut(int food, boolean hard) {
        return !hard || food > 0;
    }

    public static boolean neverUse(String itemId) {
        return itemId != null && NEVER_USE.contains(itemId);
    }

    public static boolean listening(String blockId) {
        return blockId != null && LISTENING.contains(blockId);
    }
}
