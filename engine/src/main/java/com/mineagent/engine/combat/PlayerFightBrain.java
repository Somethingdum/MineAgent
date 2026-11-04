package com.mineagent.engine.combat;

/**
 * Fighting a player by the game's rules (SMH plan 70, MA10.6; the timing idea from mineflayer-pvp, MIT; strafing, the
 * axe against a shield and leading bow shots as ideas from mineflayer-custom-pvp). Pure: the chain
 * ({@code survival/PlayerFightChain}) reads the game into a {@link Foe} and a {@link Me}, asks {@link #decide}, and does
 * the step through a player's inputs.
 * <ul>
 *   <li>Whom ({@link #mayFight}): a player its owner (or a trusted player) named, or one who hit it or its owner first;
 *   never its owner, a trusted player or a companion of the same owner, nor anyone the game won't let be hurt.</li>
 *   <li>Hits only at full charge; a crit jump when it can (a full charge while falling, not sprinting); a sprint into
 *   the first hit for knockback; strafing while it recharges, the shield up against their swing.</li>
 *   <li>An axe against a raised shield (the shield is down for 5 seconds); the bow at range.</li>
 *   <li>Low on health it backs off, eats (a golden apple first) and comes back; a totem goes in the off hand.</li>
 *   <li>A mace smash when it has a mace and wind charges: launched up, the mace in hand on the way down.</li>
 * </ul>
 */
public final class PlayerFightBrain {
    private PlayerFightBrain() {}

    /** A player's reach to another player, eyes to hitbox. */
    public static final double REACH = 3.0;
    /** A full charge, as the game measures it (the attack cooldown). */
    public static final double FULL = 0.95;
    /** Below this health it backs off; above {@link #BACK_AT} it comes back. */
    public static final double BACK_OFF_AT = 7.0;
    public static final double BACK_AT = 14.0;
    /** Farther than this, with a bow and arrows and a clear line: it shoots. */
    public static final double BOW_RANGE = 8.0;
    /** Charged for this many ticks on the ground with no crit jump landed: it hits anyway. */
    public static final int CRIT_WAIT = 12;

    /** Whether it may fight this player at all. */
    public static boolean mayFight(boolean isOwner, boolean trusted, boolean sameOwnerCompanion, boolean named,
                                   boolean attackedFirst, boolean canBeHurt) {
        if (isOwner || trusted || sameOwnerCompanion || !canBeHurt) return false;
        return named || attackedFirst;
    }

    /**
     * The other player, as it sees them. {@code friendClose}: someone it must not hurt stands beside them, where a
     * sword's sweep would reach.
     */
    public record Foe(double distance, boolean shieldUp, boolean sight, boolean swinging, boolean friendClose) {
        public Foe(double distance, boolean shieldUp, boolean sight, boolean swinging) {
            this(distance, shieldUp, sight, swinging, false);
        }
    }

    /** Itself: health, the attack charge, where its body is, and what it carries. */
    public record Me(double health, double charge, boolean onGround, boolean falling, boolean sprinting,
                     int chargedTicks, boolean hasAxe, boolean hasBow, int arrows, boolean hasGoldenApple,
                     boolean hasFood, int hunger, boolean hasShield, boolean hasTotem, boolean totemInHand,
                     boolean hasMace, boolean hasWindCharge, boolean backingOff, boolean hasPearl) {
        public Me(double health, double charge, boolean onGround, boolean falling, boolean sprinting,
                  int chargedTicks, boolean hasAxe, boolean hasBow, int arrows, boolean hasGoldenApple,
                  boolean hasFood, int hunger, boolean hasShield, boolean hasTotem, boolean totemInHand,
                  boolean hasMace, boolean hasWindCharge, boolean backingOff) {
            this(health, charge, onGround, falling, sprinting, chargedTicks, hasAxe, hasBow, arrows, hasGoldenApple, hasFood, hunger,
                    hasShield, hasTotem, totemInHand, hasMace, hasWindCharge, backingOff, false);
        }
    }

    public enum Weapon { MELEE, AXE, BOW, MACE, NONE }

    public enum Step {
        /** Closer, sprinting into the first hit when charged. */
        APPROACH,
        /** A hit now. */
        STRIKE,
        /** Up, to come down with a crit. */
        CRIT_JUMP,
        /** Sideways while it recharges (the direction in {@link Plan#strafe}). */
        STRAFE,
        /** The shield up while it recharges and they swing. */
        SHIELD,
        /** Draw and loose, leading them. */
        SHOOT,
        /** Away from them to heal. */
        BACK_OFF,
        /** Eat (a golden apple first). */
        HEAL,
        /** The totem into the off hand. */
        TOTEM,
        /** A wind charge at its feet, for a mace smash. */
        LAUNCH,
        /** An ender pearl thrown away from them: out of a fight it is losing (the landing costs 5 health). */
        PEARL
    }

    /** A pearl's landing hurts this much: it is thrown only with more health than that left over. */
    public static final double PEARL_HURT = 5.0;

    /** What to do this tick, with what in hand. */
    public record Plan(Step step, Weapon weapon, boolean sprint, int strafe) {}

    /** The step for this tick. {@code tick} picks the strafing side (it changes every second or so). */
    public static Plan decide(Foe foe, Me me, long tick) {
        int side = ((tick / 23) % 2 == 0) ? 1 : -1;
        Weapon melee = foe.shieldUp() && me.hasAxe() ? Weapon.AXE : Weapon.MELEE;
        boolean low = me.health() <= BACK_OFF_AT || me.backingOff() && me.health() < BACK_AT;
        if (me.hasTotem() && !me.totemInHand() && me.health() <= 10.0) return new Plan(Step.TOTEM, melee, false, 0);
        if (low) {
            // Losing, and they are on it: a pearl away, when the landing leaves it standing (MA10.6).
            if (foe.distance() < 4.0 && me.hasPearl() && me.health() > PEARL_HURT + 1.0) return new Plan(Step.PEARL, Weapon.NONE, false, 0);
            if (foe.distance() < 6.0) return new Plan(Step.BACK_OFF, Weapon.NONE, true, 0);
            if (me.hasGoldenApple() || me.hasFood() && me.hunger() < 18) return new Plan(Step.HEAL, Weapon.NONE, false, 0);
            return new Plan(Step.BACK_OFF, Weapon.NONE, true, 0);
        }
        if (foe.distance() > BOW_RANGE && foe.sight() && me.hasBow() && me.arrows() > 0) return new Plan(Step.SHOOT, Weapon.BOW, false, 0);
        if (foe.distance() > REACH) {
            // A sprint into the first hit knocks them back; only worth it with the charge ready.
            return new Plan(Step.APPROACH, melee, me.charge() >= FULL, 0);
        }
        // In reach.
        if (me.hasMace() && me.falling() && me.charge() >= FULL) return new Plan(Step.STRIKE, Weapon.MACE, false, 0);
        if (me.hasMace() && me.hasWindCharge() && me.onGround() && me.charge() >= FULL && !foe.shieldUp()) {
            return new Plan(Step.LAUNCH, Weapon.MACE, false, 0);
        }
        // Someone it mustn't hurt beside them: an axe (no sweep), else only a crit (a falling hit doesn't sweep).
        if (foe.friendClose() && me.hasAxe()) melee = Weapon.AXE;
        if (me.charge() >= FULL) {
            if (melee == Weapon.AXE) return new Plan(Step.STRIKE, Weapon.AXE, false, 0); // the shield comes down
            if (me.falling() && !me.sprinting()) return new Plan(Step.STRIKE, melee, false, 0); // a crit
            if (foe.friendClose()) return me.onGround() ? new Plan(Step.CRIT_JUMP, melee, false, 0) : new Plan(Step.STRAFE, melee, false, side);
            if (me.onGround() && me.chargedTicks() < CRIT_WAIT) return new Plan(Step.CRIT_JUMP, melee, false, 0);
            if (!me.onGround() && !me.falling() && me.chargedTicks() < CRIT_WAIT) return new Plan(Step.STRAFE, melee, false, side);
            return new Plan(Step.STRIKE, melee, false, 0);
        }
        if (me.hasShield() && foe.swinging() && me.charge() < 0.6) return new Plan(Step.SHIELD, melee, false, 0);
        return new Plan(Step.STRAFE, melee, false, side);
    }

    /**
     * Where to aim a bow so the arrow meets a target moving at {@code (vx, vz)} blocks a tick, {@code distance} away: the
     * target's place after the arrow's flight (a full-charge arrow covers about 3 blocks a tick).
     */
    public static double[] lead(double x, double y, double z, double vx, double vz, double distance) {
        double flight = Math.max(1.0, distance / 3.0);
        return new double[] {x + vx * flight, y, z + vz * flight};
    }
}
