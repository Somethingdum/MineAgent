package com.mineagent.engine.combat;

import com.mineagent.engine.combat.CombatBrain.Hostile;
import com.mineagent.engine.combat.CombatBrain.Self;

import java.util.Set;

/**
 * Whether a fight is worth it (SMH plan 70, its owner: "a weighing system that prevents it from wasting its time and
 * resources on pointless fights"). A mob is fought when there is a reason (a job needs its drops, it wants the
 * experience, it keeps a place clear) and it can afford the fight (health, food, its weapon's wear, the hits it would
 * take); or when the danger is immediate and unavoidable (at arm's length, shut in with it, a mob it can't outrun). On
 * the way somewhere or busy, the rest is left behind: an archer's arrows while it runs home, a creeper by day that isn't
 * coming. For its owner it steps in only when they could be seriously hurt, never once told not to. Pure.
 */
public final class FightWeighing {
    private FightWeighing() {}

    public enum Call { FIGHT, KEEP_GOING, LET_BE }

    /**
     * What it is doing: {@code busy} on a job or an errand, {@code travelling} on its way somewhere, {@code confined} in a
     * tunnel or a small room, {@code daylight}, {@code guarding} a place or a person.
     */
    public record Doing(boolean busy, boolean travelling, boolean confined, boolean daylight, boolean guarding) {
        public static Doing idle() {
            return new Doing(false, false, false, true, false);
        }
    }

    /** Its own reasons to fight: mobs whose drops a job needs ("spider"), and whether it wants experience. */
    public record Reasons(Set<String> wantedDrops, boolean wantsXp) {
        public static Reasons none() {
            return new Reasons(Set.of(), false);
        }
    }

    /**
     * Its owner (or the player it guards), as it sees them: health, armour, whether it may protect them at all, the danger
     * on them (each attacker's blow, summed), how many are on them, and a creeper close to them.
     */
    public record Charge(double health, double maxHealth, int armor, boolean protect, double dangerOn, int attackers, boolean creeperClose) {}

    /** Mobs it can't outrun: they are dealt with when they come. */
    static final Set<String> FAST = Set.of("spider", "cave_spider", "wolf", "vex", "phantom", "enderman", "hoglin", "zoglin", "ravager",
            "piglin_brute", "silverfish", "endermite", "bee", "polar_bear", "breeze", "slime", "magma_cube");

    public static Call weigh(Hostile h, Self me, double weaponLeft, Doing d, Reasons r, Charge owner) {
        MobPolicy p = MobPolicy.of(h.type());
        if (p.mustFlee()) return Call.KEEP_GOING;
        boolean creeper = "creeper".equals(p.id());
        // For someone else: only when they could be seriously hurt, and they haven't said not to.
        if ((h.targetsOwner() || h.targetsWard()) && !h.targetsSelf()) {
            if (d.guarding() && h.targetsWard()) return Call.FIGHT;
            return owner != null && owner.protect() && seriously(owner) ? Call.FIGHT : Call.LET_BE;
        }
        // A reason of its own, and the means.
        boolean reason = r.wantedDrops().contains(p.id()) || r.wantsXp() && !creeper || d.guarding();
        if (reason && affordable(h, me, weaponLeft)) return Call.FIGHT;
        if (!h.attacking() && !h.angry() && !h.fusing()) return Call.LET_BE; // minding its own business
        // Coming for it: how soon, and how surely.
        boolean onTheWay = d.busy() || d.travelling();
        if (creeper) {
            if (h.distance() > 8.0 && !h.fusing()) return onTheWay ? Call.KEEP_GOING : Call.LET_BE;
            // Close and coming: shot from where it stands, or met hit-and-back; on the move, out of its way.
            if (me.hasBow() && me.arrows() > 0 && h.distance() >= 6.0) return Call.FIGHT;
            return d.travelling() && !d.confined() ? Call.KEEP_GOING : Call.FIGHT;
        }
        if (d.confined() && h.distance() <= 8.0) return Call.FIGHT; // nowhere to go: it is killed
        boolean fast = FAST.contains(p.id());
        boolean ranged = p.preferRanged() || h.drawingBow();
        if (h.distance() <= 2.5) {
            // At arm's length: on the move, a slow one is outrun (it sprints with food in it); else it is fought.
            return d.travelling() && !fast && me.food() > 6 ? Call.KEEP_GOING : Call.FIGHT;
        }
        if (fast && h.distance() <= 10.0) return Call.FIGHT; // it would catch it
        // Staying put, what comes for it will get here: with a bow, it is shot as it comes (arrows, not health).
        if (!onTheWay && me.hasBow() && me.arrows() > 0 && h.lineOfSight() && affordable(h, me, weaponLeft)) return Call.FIGHT;
        if (ranged) {
            // An archer: on the way somewhere, it keeps going (out of its sight soon); standing still, idle or at work, it
            // is pinned there and isn't let go on shooting.
            if (d.travelling()) return Call.KEEP_GOING;
            return h.lineOfSight() && h.distance() <= 24.0 && affordable(h, me, weaponLeft) ? Call.FIGHT
                    : onTheWay ? Call.KEEP_GOING : Call.LET_BE;
        }
        // Slow and coming: left behind on the way; idle, met when it gets here.
        return onTheWay ? Call.KEEP_GOING : Call.LET_BE;
    }

    /**
     * Whether its owner could be seriously hurt, not just hurt: low already, three or more on them, a creeper by them, or
     * the next few blows (less what their armour takes) half of what they have left.
     */
    public static boolean seriously(Charge o) {
        if (o.creeperClose() || o.health() <= 8.0 || o.attackers() >= 3) return true;
        double through = 1.0 - Math.min(20, o.armor()) / 25.0;
        return o.dangerOn() * through * 2.0 >= o.health() * 0.5;
    }

    /**
     * Whether it can afford the fight: half its health or more, fed, no wither or poison on it, gear enough for a mob that
     * needs it, its weapon not about to break (unless the bow will do), and what it would take leaving it 8 health.
     */
    public static boolean affordable(Hostile h, Self me, double weaponLeft) {
        if (me.maxHealth() <= 0 || me.health() / me.maxHealth() < 0.5 || me.food() < 6 || me.witherOrPoison()) return false;
        MobPolicy p = MobPolicy.of(h.type());
        if (p.fleeUnlessGeared() && (me.weaponTier() < 3 || me.armor() < 10)) return false;
        double[] perHit = {1, 4, 5, 6, 7, 8};
        double hits = Math.max(1.0, h.health() / perHit[Math.max(0, Math.min(5, me.weaponTier()))]);
        boolean bowWillDo = me.hasBow() && me.arrows() >= hits / 1.5;
        if (me.weaponTier() > 0 && weaponLeft < 0.1 && !bowWillDo) return false;
        double taken = p.danger() * hits * 0.4 * (1.0 - Math.min(20, me.armor()) / 25.0);
        return me.health() - taken >= 8.0;
    }
}
