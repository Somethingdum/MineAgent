package com.mineagent.engine.combat;

import java.util.ArrayList;
import java.util.List;

/**
 * The 20 Hz fighting mind, with no model on its path (after the Andy Omega plan §12: threat assessment, a
 * tactical state machine, a per-mob table; and altoclef's MobDefenseChain and KillAura, MIT, see
 * THIRD_PARTY_NOTICES.md: hit only at full charge and hold the shield between hits, raise it for arrows and for a
 * skeleton drawing its bow, keep away from a fusing creeper). Pure: the survival chain gathers what the game
 * shows into {@link Hostile} and {@link Self}, asks {@link #assess} and {@link #decide}, and does what it says.
 */
public final class CombatBrain {
    public enum Posture { SAFE, ALERT, ENGAGED, OVERWHELMED, FLEEING }

    public enum Move { HOLD, APPROACH, BACK_OFF, FLEE, RETURN }

    /**
     * What the companion is doing, which decides what it fights (the user, 2026-11-03: "I need to be sure that
     * the Agent isn't just going to run off to go fight a distant mob that poses minimal threat. It should be goal
     * oriented. If I tell it to guard something, it should guard THAT thing. Within a perimeter. If it's mining, it
     * shouldn't chase every mob in the cave."). The anchor is the guard post, the owner (escort), the work spot
     * (working) or where the fight began (idle).
     */
    public enum Duty { IDLE, WORKING, ESCORT, GUARD }

    /**
     * {@code perimeter}: the guarded radius (guard), or how far it may leave the anchor to fight (the leash).
     * {@code selfFromAnchor}: how far the companion is from the anchor now.
     */
    public record Context(Duty duty, double perimeter, double selfFromAnchor) {
        public static Context idle() {
            return new Context(Duty.IDLE, 10.0, 0.0);
        }

        /** How far from the anchor it may go after a mob: the perimeter itself when guarding. */
        public double leash() {
            return switch (duty) {
                case GUARD -> perimeter + 2.0;
                case WORKING -> Math.min(perimeter, 6.0);
                case ESCORT -> Math.max(perimeter, 12.0);
                case IDLE -> Math.max(perimeter, 10.0);
            };
        }
    }

    /**
     * A hostile as the game shows it. {@code bearing}: its direction from the companion, in degrees.
     * {@code angry}: it is attacking someone (for mobs left alone otherwise).
     */
    public record Hostile(String type, double distance, double closingSpeed, double health, boolean targetsSelf,
                          boolean targetsOwner, boolean fusing, boolean drawingBow, boolean lineOfSight,
                          double bearing, boolean angry, double fromAnchor, boolean targetsWard) {
        /** Without an anchor or a ward: as close to the anchor as to the companion. */
        public Hostile(String type, double distance, double closingSpeed, double health, boolean targetsSelf,
                       boolean targetsOwner, boolean fusing, boolean drawingBow, boolean lineOfSight,
                       double bearing, boolean angry) {
            this(type, distance, closingSpeed, health, targetsSelf, targetsOwner, fusing, drawingBow, lineOfSight,
                    bearing, angry, distance, false);
        }

        boolean attacking() {
            return targetsSelf || targetsOwner || targetsWard;
        }
    }

    /** The companion. {@code weaponTier}: 0 hand, 1 wood or gold, 2 stone, 3 iron, 4 diamond, 5 netherite. */
    public record Self(double health, double maxHealth, int armor, boolean hasShield, int weaponTier,
                       boolean hasBow, int arrows, int food, boolean witherOrPoison) {}

    public record Assessment(double total, double strength, Posture posture, int primary, boolean ownerAtRisk,
                             String reason) {}

    /** What to do this tick. {@code strafe}: -1 left .. 1 right. */
    public record Action(Move move, double strafe, boolean sprint, boolean jump, boolean shield, boolean attack) {
        static Action hold(boolean shield) {
            return new Action(Move.HOLD, 0, false, false, shield, false);
        }
    }

    /** Melee reach, blocks. */
    public static final double REACH = 3.0;
    /** Full charge, for altoclef's "SMART" strike (Minecraft's attack strength scale). */
    public static final double FULL_CHARGE = 0.95;
    /** Total threat over strength times this is too much: retreat. */
    public static final double OVERWHELM = 1.6;

    private CombatBrain() {}

    /** One hostile's threat, 0 for one to leave alone. */
    public static double threat(Hostile h) {
        MobPolicy p = MobPolicy.of(h.type());
        if (p.onlyIfAngry() && !h.angry() && !h.targetsSelf() && !h.targetsOwner()) return 0.0;
        double proximity = Math.max(0.1, Math.min(1.5, 1.5 - h.distance() / 12.0));
        double engaged = (h.targetsSelf() ? 1.5 : 1.0) * (h.targetsOwner() ? 1.6 : 1.0);
        double score = p.danger() * 10.0 * proximity * engaged;
        if (h.fusing() && h.distance() < 6.0) score += 60.0;
        if (h.drawingBow() && h.lineOfSight()) score += 15.0;
        return score + Math.max(0.0, h.closingSpeed()) * 10.0;
    }

    /** How strong the companion is now, on the same scale. */
    public static double strength(Self s) {
        double ratio = s.maxHealth() > 0 ? s.health() / s.maxHealth() : 1.0;
        return 40.0 * ratio + 4.0 * s.armor() + 12.0 * s.weaponTier() + (s.hasShield() ? 20.0 : 0.0)
                + (s.food() >= 6 ? 5.0 : 0.0) - (s.witherOrPoison() ? 20.0 : 0.0);
    }

    /** An archer attacking with a clear shot: it keeps hitting from range, so it is dealt with, not waited out. */
    static boolean shooting(Hostile h) {
        MobPolicy p = MobPolicy.of(h.type());
        return h.attacking() && h.lineOfSight() && (p.preferRanged() || h.drawingBow()) && h.distance() <= 16.0;
    }

    /** It is going for the owner or a trusted player within 24 blocks (plan 69, MA3.5): the companion steps in. */
    static boolean protecting(Hostile h) {
        return (h.targetsOwner() || h.targetsWard()) && h.distance() <= 24.0;
    }

    /**
     * How far from the anchor it may go after this hostile: the duty's leash; 16 blocks for an archer shooting at it
     * or its charge; 24 for one going for the owner or a trusted player (then back to the work). A guard keeps to its
     * perimeter, and goes 8 past it only for an archer shooting in.
     */
    public static double leashFor(Hostile h, Context c) {
        double leash = c.leash();
        if (h == null) return leash;
        if (c.duty() == Duty.GUARD) return shooting(h) ? c.perimeter() + 8.0 : leash;
        if (protecting(h)) leash = Math.max(leash, 24.0);
        if (shooting(h)) leash = Math.max(leash, 16.0);
        return leash;
    }

    /**
     * Whether a hostile is the companion's business given its duty: never a mob far off that threatens no one.
     * Always: one in its face (4 blocks) or a creeper close. Guarding: anything inside the perimeter, or shooting
     * into it from just outside; nothing farther. Working: only what attacks it close by, an archer shooting at it, or
     * what goes for the owner or a trusted player within 24 blocks (it does not chase every mob in the cave). Escort:
     * what threatens the owner. Idle: what attacks it or them.
     */
    public static boolean relevant(Hostile h, Context c) {
        MobPolicy p = MobPolicy.of(h.type());
        if (p.onlyIfAngry() && !h.angry() && !h.attacking()) return false;
        boolean inFace = h.distance() <= 4.0;
        boolean creeperNear = p.neverMeleeBare() && h.distance() < 6.0;
        if (inFace || creeperNear) return true;
        double leash = leashFor(h, c);
        return switch (c.duty()) {
            case GUARD -> h.fromAnchor() <= c.perimeter() || (shooting(h) && h.fromAnchor() <= leash);
            case WORKING -> h.attacking() && (h.distance() <= 8.0 || shooting(h) || protecting(h)) && h.fromAnchor() <= leash + 4.0;
            case ESCORT -> h.attacking() && h.fromAnchor() <= leash + 4.0;
            case IDLE -> h.attacking() && (h.distance() <= 16.0 || protecting(h));
        };
    }

    /**
     * With a bow and arrows, what is coming for someone in plain sight is its business out to the bow's reach (SMH plan
     * 70): it is shot from where it stands ({@code RangedChoice}), not walked to. Not on escort (it keeps to its ward), and
     * not what arrows can't land on or what is fled.
     */
    public static boolean inBowReach(Hostile h, Self self, Context c) {
        if (!self.hasBow() || self.arrows() <= 0 || c.duty() == Duty.ESCORT || !h.attacking() || !h.lineOfSight()) return false;
        MobPolicy p = MobPolicy.of(h.type());
        return !p.mustFlee() && !RangedChoice.NO_ARROWS.contains(p.id()) && h.distance() <= RangedChoice.MAX;
    }

    /** Andy Omega §12.1, with no duty: as if idle where it stands. */
    public static Assessment assess(List<Hostile> hostiles, Self self, Tactics tactics) {
        return assess(hostiles, self, tactics, Context.idle());
    }

    /** Andy Omega §12.1: total threat from what is its business, the posture it calls for, whom to strike first. */
    public static Assessment assess(List<Hostile> hostiles, Self self, Tactics tactics, Context context) {
        double strength = strength(self);
        double total = 0.0;
        int primary = -1;
        double best = -1.0;
        boolean ownerAtRisk = false;
        List<Double> close = new ArrayList<>();
        for (int i = 0; i < hostiles.size(); i++) {
            Hostile h = hostiles.get(i);
            MobPolicy p = MobPolicy.of(h.type());
            if (p.mustFlee() && h.distance() < p.keepDistance()) {
                return new Assessment(threat(h), strength, Posture.FLEEING, i, h.targetsOwner(), "never fight a " + p.id());
            }
            boolean undergeared = self.weaponTier() < 3 || self.armor() < 10 || self.health() < self.maxHealth() * 0.6;
            if (p.fleeUnlessGeared() && undergeared && h.distance() < 8.0 && (h.angry() || h.targetsSelf())) {
                return new Assessment(threat(h), strength, Posture.FLEEING, i, h.targetsOwner(), "too strong for this gear: " + p.id());
            }
            if (!relevant(h, context) && !inBowReach(h, self, context)) continue;
            double t = threat(h);
            if (t <= 0.0) continue;
            total += t;
            ownerAtRisk |= h.targetsOwner();
            if (h.distance() < 6.0) close.add(h.bearing());
            double rank = t + orderBonus(tactics, h.type());
            if (rank > best) {
                best = rank;
                primary = i;
            }
        }
        if (primary < 0) return new Assessment(0, strength, Posture.SAFE, -1, false, "nothing to fight");
        total *= geometry(close);
        if (self.health() <= tactics.retreatHealth() || "retreat".equals(tactics.stance())) {
            return new Assessment(total, strength, Posture.FLEEING, primary, ownerAtRisk, "health low, breaking off");
        }
        Hostile top = hostiles.get(primary);
        // A guard clears its perimeter; elsewhere a mob that bothers no one is only watched.
        boolean guardsIt = context.duty() == Duty.GUARD && top.fromAnchor() <= context.perimeter();
        if (!guardsIt && !top.targetsSelf() && !top.targetsOwner() && top.distance() > 6.0 && !top.angry()) {
            return new Assessment(total, strength, Posture.ALERT, primary, ownerAtRisk, "watching " + top.type());
        }
        if (total > strength * OVERWHELM) {
            return new Assessment(total, strength, Posture.OVERWHELMED, primary, ownerAtRisk, "outnumbered");
        }
        return new Assessment(total, strength, Posture.ENGAGED, primary, ownerAtRisk, "fighting " + top.type());
    }

    /** Several mobs close is worse than one; mobs all round (a wide arc) are worse than in a line (§12.1). */
    static double geometry(List<Double> bearings) {
        int n = bearings.size();
        if (n <= 1) return 1.0;
        double spread = 0.0;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                double d = Math.abs(bearings.get(i) - bearings.get(j)) % 360.0;
                spread = Math.max(spread, d > 180.0 ? 360.0 - d : d);
            }
        }
        return 1.0 + 0.15 * (n - 1) + (spread >= 120.0 ? 0.3 : spread >= 60.0 ? 0.15 : 0.0);
    }

    private static double orderBonus(Tactics t, String type) {
        if (t == null || t.targetOrder().isEmpty() || type == null) return 0.0;
        String k = type.contains(":") ? type.substring(type.indexOf(':') + 1) : type;
        int at = t.targetOrder().indexOf(k);
        return at < 0 ? 0.0 : 200.0 - at * 20.0;
    }

    /**
     * One tick's action against the primary target. {@code charge}: attack strength 0..1. {@code incomingArrow}:
     * the arrow predictor says one will pass within blocking distance. {@code skeletonDrawing}: one in sight
     * within 10 blocks has drawn for 15 ticks or more (it takes the shield 5 ticks to come up).
     */
    public static Action decide(Assessment a, Hostile target, Self self, double charge, boolean onGround,
                                boolean falling, boolean inWater, boolean incomingArrow, boolean skeletonDrawing,
                                Tactics t, long tick) {
        return decide(a, target, self, charge, onGround, falling, inWater, incomingArrow, skeletonDrawing, t, tick, Context.idle());
    }

    /** One tick's action, kept within the duty's leash: it never chases a mob out of its perimeter or off its work. */
    public static Action decide(Assessment a, Hostile target, Self self, double charge, boolean onGround,
                                boolean falling, boolean inWater, boolean incomingArrow, boolean skeletonDrawing,
                                Tactics t, long tick, Context context) {
        boolean canShield = self.hasShield() && t.useShield();
        boolean threatIn = incomingArrow || skeletonDrawing;
        if (a.posture() != Posture.FLEEING && a.posture() != Posture.OVERWHELMED) {
            boolean inFace = target != null && target.distance() <= REACH;
            double leash = leashFor(target, context);
            // Off its post or its work: back, shield up if something is shooting, unless a mob is in its face.
            if (context.selfFromAnchor() > leash && !inFace) {
                return new Action(Move.RETURN, 0, !threatIn, false, canShield && threatIn, false);
            }
            // The target has left the perimeter or the leash: let it go.
            if (target != null && target.fromAnchor() > leash && !inFace) {
                return new Action(context.selfFromAnchor() > 1.5 ? Move.RETURN : Move.HOLD, 0, false, false, canShield && threatIn, false);
            }
        }
        if (a.posture() == Posture.FLEEING || a.posture() == Posture.OVERWHELMED) {
            // Running: the shield slows; up only when an arrow is about to land.
            return new Action(Move.FLEE, 0, !(canShield && threatIn), false, canShield && threatIn, false);
        }
        if (target == null || a.posture() == Posture.SAFE) return Action.hold(canShield && threatIn);
        MobPolicy p = MobPolicy.of(target.type());
        double dist = target.distance();
        boolean ready = charge >= FULL_CHARGE;
        String stance = t.stance();
        double zigzag = ((tick / 15) % 2 == 0) ? 0.6 : -0.6;
        double circle = ((tick / 20) % 2 == 0) ? 0.8 : -0.8;

        // The creeper: never struck bare; while it fuses, a raised shield facing it takes the blast (vanilla blocks
        // explosions from the front), and without one it runs; step back after every hit.
        if (p.neverMeleeBare()) {
            if (target.fusing() && dist < p.keepDistance()) {
                return canShield ? new Action(Move.BACK_OFF, 0, false, false, true, false)
                        : new Action(Move.FLEE, 0, true, false, false, false);
            }
            if (dist <= REACH && ready && (canShield || dist >= 2.5)) {
                return new Action(Move.HOLD, 0, false, false, false, true);
            }
            if (dist < 2.5) return new Action(Move.BACK_OFF, circle, false, false, canShield, false);
            return new Action(Move.APPROACH, zigzag, false, false, canShield, false);
        }

        if (dist > REACH) {
            // Closing in: archers are met behind the shield, zig-zagging.
            boolean archer = p.preferRanged() || target.drawingBow();
            boolean shieldUp = canShield && (threatIn || (archer && target.lineOfSight()));
            return new Action(Move.APPROACH, archer ? zigzag : 0, dist > 6.0 && !shieldUp, false, shieldUp, false);
        }

        // In reach. Strike only at full charge (altoclef's SMART aura); a crit is a strike while falling.
        boolean crits = t.crits() && !"defensive".equals(stance) && !inWater;
        if (ready) {
            if (crits && onGround && !"kite".equals(stance)) return new Action(Move.HOLD, 0, false, true, false, false);
            if (!crits || falling || !onGround) {
                Move after = "kite".equals(stance) ? Move.BACK_OFF : Move.HOLD;
                return new Action(after, 0, false, false, false, true);
            }
        }
        // Waiting for the charge: shield up, or circle so it does not land its hits.
        if ("defensive".equals(stance) && dist < 2.5) return new Action(Move.BACK_OFF, circle, false, false, canShield, false);
        if ("kite".equals(stance) && dist < 3.5) return new Action(Move.BACK_OFF, circle, false, false, canShield, false);
        if (canShield) return Action.hold(true);
        return new Action(Move.HOLD, circle, false, false, false, false);
    }

    /** The stances learning chooses among for a fight (the scorecard's keys are fight:<mob>:<stance>). */
    public static List<String> stances() {
        return List.of("aggressive", "balanced", "defensive", "kite");
    }
}
