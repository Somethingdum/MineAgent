package com.mineagent.engine.gear;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Levels (SMH plan 70, its owner: "It should want XP at some point, you need it for enchantments and anvil shit. So at
 * some point, once its doing well in terms of materials, armor and weapons. It should start seeking beef with mobs. Of
 * any kind."): how many levels it wants, whether it is doing well enough to go after them, and which mob to go for. Pure.
 */
public final class XpSeeking {
    private XpSeeking() {}

    /** The best roll an enchanting table offers costs 30 levels. */
    public static final int TABLE = 30;
    /** The most it goes after for a run of anvil steps it couldn't pay for. */
    public static final int MOST = 50;

    /** The levels it wants: 30, or what a run of anvil steps it couldn't pay for took all told (up to 50). */
    public static int target(int neededForSteps) {
        return Math.max(TABLE, Math.min(MOST, neededForSteps));
    }

    /**
     * Doing well for armour and weapons: every piece it wears iron or better, an iron weapon or better, and a shield
     * (tiers as {@link GodGear#tier}: 3 is iron).
     */
    public static boolean geared(int helmet, int chestplate, int leggings, int boots, int weapon, boolean shield) {
        return Math.min(Math.min(helmet, chestplate), Math.min(leggings, boots)) >= 3 && weapon >= 3 && shield;
    }

    /** Doing well for materials: at least this much of each, in its pack and its own chests together. */
    public static final int FOOD = 32, IRON = 32, COAL = 32, TORCHES = 64;

    /** Foods that count toward its stock: the cooked and the ready to eat. */
    static final Set<String> FOODS = Set.of("minecraft:bread", "minecraft:cooked_beef", "minecraft:cooked_porkchop", "minecraft:cooked_mutton",
            "minecraft:cooked_chicken", "minecraft:cooked_rabbit", "minecraft:cooked_cod", "minecraft:cooked_salmon", "minecraft:baked_potato",
            "minecraft:golden_carrot", "minecraft:carrot", "minecraft:apple", "minecraft:pumpkin_pie", "minecraft:mushroom_stew",
            "minecraft:rabbit_stew", "minecraft:beetroot_soup");

    /** What its stock is short of, as words ("32 iron"); empty: it is doing well for materials. */
    public static List<String> short_(Map<String, Integer> has) {
        List<String> out = new ArrayList<>();
        int food = 0;
        for (String f : FOODS) food += has.getOrDefault(f, 0);
        int coal = has.getOrDefault("minecraft:coal", 0) + has.getOrDefault("minecraft:charcoal", 0);
        if (food < FOOD) out.add((FOOD - food) + " more food");
        if (has.getOrDefault("minecraft:iron_ingot", 0) < IRON) out.add((IRON - has.getOrDefault("minecraft:iron_ingot", 0)) + " more iron");
        if (coal < COAL) out.add((COAL - coal) + " more coal");
        if (has.getOrDefault("minecraft:torch", 0) < TORCHES) out.add((TORCHES - has.getOrDefault("minecraft:torch", 0)) + " more torches");
        return out;
    }

    /** Fit to start: three quarters of its health, fed (14 of 20), and 8 food or more on it. */
    public static boolean fit(double health, double maxHealth, int foodLevel, int foodCarried) {
        return maxHealth > 0 && health / maxHealth >= 0.75 && foodLevel >= 14 && foodCarried >= 8;
    }

    /** Fit to go on: half its health, and food on it. */
    public static boolean goOn(double health, double maxHealth, int foodCarried) {
        return maxHealth > 0 && health / maxHealth >= 0.5 && foodCarried >= 2;
    }

    /** Night, when the surface has its monsters out: from dusk (13,000) to dawn (23,000). */
    public static boolean night(long dayTime) {
        long t = Math.floorMod(dayTime, 24_000L);
        return t >= 13_000L && t < 23_000L;
    }

    public enum Kind { HOSTILE, ANIMAL, NEVER }

    /**
     * Hostiles it doesn't go after even for levels: a piglin's or a zombified piglin's kin all turn on it (and piglins
     * barter); the warden, the wither and the dragon; what fights underwater; raid captains and their summons; what
     * flies out of reach (a phantom or a ghast is met when it comes); and the creeper, shot or met as it comes, never
     * walked up to. Each is still fought by the usual rules when it comes for it.
     */
    static final Set<String> NOT_FOR_XP = Set.of("piglin", "zombified_piglin", "piglin_brute", "warden", "wither", "ender_dragon",
            "elder_guardian", "guardian", "evoker", "vindicator", "ravager", "vex", "shulker", "phantom", "ghast", "creeper");

    /** Animals worth a kill for their experience (and their meat), the herd always kept. */
    static final Set<String> ANIMALS = Set.of("cow", "mooshroom", "pig", "sheep", "chicken", "rabbit");

    /** Two of a kind are left round about, so they breed again. */
    public static final int HERD_KEPT = 2;

    /** What a mob is to it when it is after levels; {@code enemy}: the game counts it a monster. */
    public static Kind kind(String type, boolean enemy) {
        String t = type == null ? "" : type.substring(type.indexOf(':') + 1);
        if (NOT_FOR_XP.contains(t)) return Kind.NEVER;
        if (enemy) return Kind.HOSTILE;
        if (ANIMALS.contains(t)) return Kind.ANIMAL;
        return Kind.NEVER;
    }

    /** About what one gives: a monster 5, a blaze 10, a slime or magma cube by its size (2 here), an animal 1 to 3. */
    public static int xp(String type, boolean enemy) {
        String t = type == null ? "" : type.substring(type.indexOf(':') + 1);
        if (t.equals("blaze")) return 10;
        if (t.equals("slime") || t.equals("magma_cube")) return 2;
        return enemy ? 5 : 2;
    }

    /**
     * A mob it might go for. {@code kept}: one it never kills for levels (a name, a lead, a pen or a farm of its owner's);
     * {@code herd}: how many grown ones of its kind are round about, itself counted.
     */
    public record Prey(String type, boolean enemy, double distance, boolean sight, boolean inWater, boolean adult, boolean kept, int herd) {}

    /** How far it goes after one it can't see. */
    public static final double UNSEEN = 12.0;

    /**
     * The one to go for, or -1: a monster before any animal (more levels, and one less round its home), the most
     * levels for the walk; an animal only grown, wild, and never one of the last two of its kind about. Never one in
     * water, one of those it never goes after, or one out of sight beyond 12 blocks.
     */
    public static int pick(List<Prey> prey) {
        int best = -1;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < prey.size(); i++) {
            Prey p = prey.get(i);
            Kind k = kind(p.type(), p.enemy());
            if (k == Kind.NEVER || p.inWater() || p.kept()) continue;
            if (!p.sight() && p.distance() > UNSEEN) continue;
            if (k == Kind.ANIMAL && (!p.adult() || p.herd() <= HERD_KEPT)) continue;
            double score = (k == Kind.HOSTILE ? 1_000.0 : 0.0) + xp(p.type(), p.enemy()) * 10.0 - p.distance();
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }
}
