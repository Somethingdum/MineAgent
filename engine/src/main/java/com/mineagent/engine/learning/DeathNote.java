package com.mineagent.engine.learning;

import java.util.Locale;
import java.util.Set;

/**
 * What a death leaves behind (SMH plan 69, MA3.9): a note on the goal it was working on, and whether to go back for
 * its things. Pure; the lifecycle handler gives it the damage type and the world's rules.
 */
public final class DeathNote {
    /** Damage that leaves nothing to pick up: the items burn or fall out of the world. */
    private static final Set<String> NOTHING_LEFT = Set.of("lava", "in_fire", "on_fire", "fell_out_of_world", "outside_border");
    /** Dropped items vanish this long after a death, in ticks (vanilla, 5 minutes). */
    public static final long ITEMS_LAST_TICKS = 6_000L;
    public static final String RECOVER_PREFIX = "Recover your gear at ";

    private DeathNote() {}

    /** Whether going back for its things makes sense. {@code damageType}: e.g. "minecraft:lava" or "lava". */
    public static boolean recoverable(String damageType, boolean keepInventory, boolean hardcore, boolean carriedAnything) {
        if (keepInventory || hardcore || !carriedAnything) return false;
        String t = damageType == null ? "" : damageType.toLowerCase(Locale.ROOT);
        if (t.contains(":")) t = t.substring(t.indexOf(':') + 1);
        return !NOTHING_LEFT.contains(t);
    }

    /** The goal that sends it back. */
    public static String recoveryGoal(int x, int y, int z, String dimension) {
        String dim = dimension == null ? "" : dimension.replace("minecraft:", "").replaceFirst("^the_", "");
        return RECOVER_PREFIX + x + " " + y + " " + z + (dim.isEmpty() || dim.equals("overworld") ? "" : " in the " + dim.replace('_', ' '))
                + ": go there carefully (whatever killed you may still be there) and pick up what you dropped; it vanishes 5 minutes after the death.";
    }

    /** Where a recovery goal sends it ({@code x y z}), or null for any other goal's words. */
    public static int[] recoveryAt(String goalText) {
        if (goalText == null || !goalText.startsWith(RECOVER_PREFIX)) return null;
        var m = java.util.regex.Pattern.compile("^(-?\\d+) (-?\\d+) (-?\\d+)").matcher(goalText.substring(RECOVER_PREFIX.length()));
        if (!m.find()) return null;
        return new int[] {Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3))};
    }

    /** The note on the goal it was on. */
    public static String note(String cause, int x, int y, int z) {
        String c = cause == null || cause.isBlank() ? "died" : cause.strip();
        if (c.length() > 160) c = c.substring(0, 160);
        return "Died at " + x + " " + y + " " + z + ": " + c;
    }
}
