package com.mineagent.engine.combat;

import java.util.Locale;
import java.util.Map;

/**
 * How to fight each mob: a table, not code (after the Andy Omega plan §12.3, and the danger lists of
 * altoclef's MobDefenseChain, MIT, see THIRD_PARTY_NOTICES.md). Its "never" rules are the floor: neither
 * the model's tactics nor learning can remove one (§12.5).
 *
 * @param danger            base danger, 0..10
 * @param preferRanged      fight it from range when a bow and arrows are at hand
 * @param keepDistance      blocks to keep between it and the companion when not striking (0: none)
 * @param shieldUseful      a raised shield blocks most of what it does
 * @param neverMeleeBare    never strike it in melee without a shield up (the creeper)
 * @param mustFlee          never engage it, whatever is said (the warden, the wither)
 * @param fleeUnlessGeared  engage only with iron gear or better and good health; else keep away
 * @param onlyIfAngry       leave it alone unless it is attacking (enderman, piglins, wolves)
 */
public record MobPolicy(String id, int danger, boolean preferRanged, double keepDistance, boolean shieldUseful,
                        boolean neverMeleeBare, boolean mustFlee, boolean fleeUnlessGeared, boolean onlyIfAngry) {

    private static MobPolicy p(String id, int danger, boolean ranged, double keep, boolean shield,
                               boolean neverBare, boolean flee, boolean geared, boolean angry) {
        return new MobPolicy(id, danger, ranged, keep, shield, neverBare, flee, geared, angry);
    }

    /** An unknown hostile: careful, not fearful. */
    public static final MobPolicy DEFAULT = p("hostile", 4, false, 0, true, false, false, false, false);

    private static final Map<String, MobPolicy> TABLE = Map.ofEntries(
            // Melee walkers: close in, full-charge hits, shield between them.
            Map.entry("zombie", p("zombie", 3, false, 0, true, false, false, false, false)),
            Map.entry("husk", p("husk", 3, false, 0, true, false, false, false, false)),
            Map.entry("zombie_villager", p("zombie_villager", 3, false, 0, true, false, false, false, false)),
            Map.entry("drowned", p("drowned", 4, false, 0, true, false, false, false, false)),
            Map.entry("spider", p("spider", 3, false, 0, true, false, false, false, false)),
            Map.entry("cave_spider", p("cave_spider", 4, false, 0, true, false, false, false, false)),
            Map.entry("silverfish", p("silverfish", 2, false, 0, false, false, false, false, false)),
            Map.entry("endermite", p("endermite", 2, false, 0, false, false, false, false, false)),
            Map.entry("slime", p("slime", 3, false, 0, true, false, false, false, false)),
            Map.entry("magma_cube", p("magma_cube", 5, false, 0, true, false, false, false, false)),
            // Archers: close the distance behind a shield, strafe.
            Map.entry("skeleton", p("skeleton", 4, true, 0, true, false, false, false, false)),
            Map.entry("stray", p("stray", 5, true, 0, true, false, false, false, false)),
            Map.entry("bogged", p("bogged", 5, true, 0, true, false, false, false, false)),
            Map.entry("pillager", p("pillager", 5, true, 0, true, false, false, false, false)),
            // The creeper: never melee it bare; hit and step back, or shoot it.
            Map.entry("creeper", p("creeper", 8, true, 4.5, true, true, false, false, false)),
            // Kill fast or leave: the witch heals and poisons.
            Map.entry("witch", p("witch", 6, true, 0, false, false, false, false, false)),
            Map.entry("blaze", p("blaze", 7, true, 0, true, false, false, false, false)),
            Map.entry("ghast", p("ghast", 6, true, 0, true, false, false, false, false)),
            Map.entry("breeze", p("breeze", 6, true, 0, true, false, false, false, false)),
            Map.entry("phantom", p("phantom", 4, false, 0, true, false, false, false, false)),
            Map.entry("vex", p("vex", 5, false, 0, true, false, false, false, false)),
            Map.entry("guardian", p("guardian", 6, true, 0, true, false, false, false, false)),
            Map.entry("shulker", p("shulker", 5, true, 0, true, false, false, false, false)),
            // Leave alone unless angry; fight with a shield up if it comes.
            Map.entry("enderman", p("enderman", 7, false, 0, false, false, false, false, true)),
            Map.entry("piglin", p("piglin", 5, false, 0, true, false, false, false, true)),
            Map.entry("zombified_piglin", p("zombified_piglin", 6, false, 0, true, false, false, false, true)),
            Map.entry("wolf", p("wolf", 4, false, 0, true, false, false, false, true)),
            // Dangerous enough to keep away from without good gear (altoclef's "universally dangerous").
            Map.entry("vindicator", p("vindicator", 8, false, 0, true, false, false, true, false)),
            Map.entry("evoker", p("evoker", 8, false, 0, true, false, false, true, false)),
            Map.entry("ravager", p("ravager", 9, false, 0, true, false, false, true, false)),
            Map.entry("piglin_brute", p("piglin_brute", 9, false, 0, true, false, false, true, false)),
            Map.entry("hoglin", p("hoglin", 8, false, 0, true, false, false, true, false)),
            Map.entry("zoglin", p("zoglin", 8, false, 0, true, false, false, true, false)),
            Map.entry("wither_skeleton", p("wither_skeleton", 8, false, 0, true, false, false, true, false)),
            Map.entry("elder_guardian", p("elder_guardian", 9, false, 0, true, false, false, true, false)),
            // Never engage.
            Map.entry("warden", p("warden", 10, false, 24, false, false, true, false, false)),
            Map.entry("wither", p("wither", 10, false, 24, false, false, true, false, false)),
            Map.entry("ender_dragon", p("ender_dragon", 10, false, 24, false, false, true, false, false)));

    /** The policy for an entity type id ("minecraft:zombie" or "zombie"). */
    public static MobPolicy of(String typeId) {
        if (typeId == null) return DEFAULT;
        String k = typeId.toLowerCase(Locale.ROOT);
        int colon = k.indexOf(':');
        if (colon >= 0) k = k.substring(colon + 1);
        return TABLE.getOrDefault(k, DEFAULT);
    }
}
