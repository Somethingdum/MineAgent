package com.mineagent.engine.combat;

import com.mineagent.engine.combat.PlayerFightBrain.Foe;
import com.mineagent.engine.combat.PlayerFightBrain.Me;
import com.mineagent.engine.combat.PlayerFightBrain.Step;
import com.mineagent.engine.combat.PlayerFightBrain.Weapon;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** SMH plan 70, MA10.6: whom it fights, and how, by the game's rules. */
class PlayerFightBrainTest {

    private static Me me(double health, double charge, boolean onGround, boolean falling) {
        return new Me(health, charge, onGround, falling, false, 0, false, false, 0, false, false, 20, false, false, false,
                false, false, false);
    }

    @Test
    void neverItsOwnerATrustedPlayerOrACompanionAndOnlyThoseNamedOrWhoStarted() {
        assertFalse(PlayerFightBrain.mayFight(true, false, false, true, true, true), "never its owner, even named");
        assertFalse(PlayerFightBrain.mayFight(false, true, false, true, true, true), "never a trusted player");
        assertFalse(PlayerFightBrain.mayFight(false, false, true, true, true, true), "never a companion of the same owner");
        assertFalse(PlayerFightBrain.mayFight(false, false, false, false, false, true), "nobody named them, they did nothing");
        assertFalse(PlayerFightBrain.mayFight(false, false, false, true, false, false), "the game lets no one hurt them (PvP off)");
        assertTrue(PlayerFightBrain.mayFight(false, false, false, true, false, true), "named");
        assertTrue(PlayerFightBrain.mayFight(false, false, false, false, true, true), "hit it or its owner first");
    }

    @Test
    void hitsOnlyAtFullCharge() {
        Foe close = new Foe(2.0, false, true, false);
        assertNotEquals(Step.STRIKE, PlayerFightBrain.decide(close, me(20, 0.5, true, false), 0).step());
        assertNotEquals(Step.STRIKE, PlayerFightBrain.decide(close, me(20, 0.9, false, true), 0).step());
        assertEquals(Step.STRIKE, PlayerFightBrain.decide(close, me(20, 1.0, false, true), 0).step(), "full charge, falling: a crit");
    }

    @Test
    void aCritJumpOnTheGroundThenAHitIfTheJumpNeverComes() {
        Foe close = new Foe(2.0, false, true, false);
        assertEquals(Step.CRIT_JUMP, PlayerFightBrain.decide(close, me(20, 1.0, true, false), 0).step());
        Me waited = new Me(20, 1.0, true, false, false, PlayerFightBrain.CRIT_WAIT, false, false, 0, false, false, 20, false,
                false, false, false, false, false);
        assertEquals(Step.STRIKE, PlayerFightBrain.decide(close, waited, 0).step());
    }

    @Test
    void anAxeAgainstARaisedShield() {
        Foe shielded = new Foe(2.0, true, true, false);
        Me axe = new Me(20, 1.0, true, false, false, 0, true, false, 0, false, false, 20, false, false, false, false, false, false);
        var plan = PlayerFightBrain.decide(shielded, axe, 0);
        assertEquals(Weapon.AXE, plan.weapon());
        assertEquals(Step.STRIKE, plan.step(), "no crit jump needed: the axe takes the shield down");
        Foe far = new Foe(6.0, true, true, false);
        assertEquals(Weapon.AXE, PlayerFightBrain.decide(far, axe, 0).weapon(), "the axe in hand on the way in");
        Me noAxe = me(20, 1.0, true, false);
        assertEquals(Weapon.MELEE, PlayerFightBrain.decide(shielded, noAxe, 0).weapon());
    }

    @Test
    void noSweepIntoSomeoneBesideThem() {
        Foe crowded = new Foe(2.0, false, true, false, true);
        Me waited = new Me(20, 1.0, true, false, false, PlayerFightBrain.CRIT_WAIT + 5, false, false, 0, false, false, 20,
                false, false, false, false, false, false);
        assertEquals(Step.CRIT_JUMP, PlayerFightBrain.decide(crowded, waited, 0).step(), "on the ground: never a sweeping hit");
        Me falling = new Me(20, 1.0, false, true, false, 0, false, false, 0, false, false, 20, false, false, false, false, false, false);
        assertEquals(Step.STRIKE, PlayerFightBrain.decide(crowded, falling, 0).step(), "a crit doesn't sweep");
        Me axe = new Me(20, 1.0, true, false, false, 0, true, false, 0, false, false, 20, false, false, false, false, false, false);
        var plan = PlayerFightBrain.decide(crowded, axe, 0);
        assertEquals(Weapon.AXE, plan.weapon(), "an axe doesn't sweep");
        assertEquals(Step.STRIKE, plan.step());
    }

    @Test
    void strafesAndShieldsWhileItRecharges() {
        Foe close = new Foe(2.0, false, true, true);
        Me shield = new Me(20, 0.3, true, false, false, 0, false, false, 0, false, false, 20, true, false, false, false, false, false);
        assertEquals(Step.SHIELD, PlayerFightBrain.decide(close, shield, 0).step());
        var a = PlayerFightBrain.decide(new Foe(2.0, false, true, false), me(20, 0.3, true, false), 0);
        var b = PlayerFightBrain.decide(new Foe(2.0, false, true, false), me(20, 0.3, true, false), 23);
        assertEquals(Step.STRAFE, a.step());
        assertEquals(-a.strafe(), b.strafe(), "the side changes");
    }

    @Test
    void sprintsInWithTheChargeReadyAndShootsAtRange() {
        var in = PlayerFightBrain.decide(new Foe(6.0, false, true, false), me(20, 1.0, true, false), 0);
        assertEquals(Step.APPROACH, in.step());
        assertTrue(in.sprint());
        Me bow = new Me(20, 1.0, true, false, false, 0, false, true, 16, false, false, 20, false, false, false, false, false, false);
        assertEquals(Step.SHOOT, PlayerFightBrain.decide(new Foe(14.0, false, true, false), bow, 0).step());
        assertEquals(Step.APPROACH, PlayerFightBrain.decide(new Foe(14.0, false, false, false), bow, 0).step(), "no line: closer");
    }

    @Test
    void backsOffToHealAndComesBack() {
        Me low = new Me(6, 1.0, true, false, false, 0, false, false, 0, true, true, 12, false, false, false, false, false, false);
        assertEquals(Step.BACK_OFF, PlayerFightBrain.decide(new Foe(3.0, false, true, false), low, 0).step());
        assertEquals(Step.HEAL, PlayerFightBrain.decide(new Foe(9.0, false, true, false), low, 0).step());
        Me mending = new Me(12, 1.0, true, false, false, 0, false, false, 0, false, false, 20, false, false, false, false, false, true);
        assertEquals(Step.BACK_OFF, PlayerFightBrain.decide(new Foe(9.0, false, true, false), mending, 0).step(), "not back until 14");
        Me well = new Me(15, 1.0, true, false, false, 0, false, false, 0, false, false, 20, false, false, false, false, false, true);
        assertEquals(Step.APPROACH, PlayerFightBrain.decide(new Foe(9.0, false, true, false), well, 0).step());
    }

    @Test
    void aPearlAwayWhenLosingAndTheyAreOnIt() {
        Me pearl = new Me(7, 1.0, true, false, false, 0, false, false, 0, false, false, 20, false, false, false, false, false, false, true);
        assertEquals(Step.PEARL, PlayerFightBrain.decide(new Foe(2.5, false, true, true), pearl, 0).step());
        assertEquals(Step.BACK_OFF, PlayerFightBrain.decide(new Foe(5.0, false, true, false), pearl, 0).step(), "not on it: just away");
        Me tooLow = new Me(5, 1.0, true, false, false, 0, false, false, 0, false, false, 20, false, false, false, false, false, false, true);
        assertNotEquals(Step.PEARL, PlayerFightBrain.decide(new Foe(2.5, false, true, true), tooLow, 0).step(), "the landing would finish it");
        Me well = new Me(18, 1.0, true, false, false, 0, false, false, 0, false, false, 20, false, false, false, false, false, false, true);
        assertNotEquals(Step.PEARL, PlayerFightBrain.decide(new Foe(2.5, false, true, true), well, 0).step(), "winning: it fights");
    }

    @Test
    void aTotemInTheOffHandWhenLow() {
        Me totem = new Me(9, 1.0, true, false, false, 0, false, false, 0, false, false, 20, true, true, false, false, false, false);
        assertEquals(Step.TOTEM, PlayerFightBrain.decide(new Foe(2.0, false, true, false), totem, 0).step());
    }

    @Test
    void aMaceSmash() {
        Me mace = new Me(20, 1.0, true, false, false, 0, false, false, 0, false, false, 20, false, false, false, true, true, false);
        assertEquals(Step.LAUNCH, PlayerFightBrain.decide(new Foe(2.0, false, true, false), mace, 0).step());
        Me down = new Me(20, 1.0, false, true, false, 0, false, false, 0, false, false, 20, false, false, false, true, false, false);
        var plan = PlayerFightBrain.decide(new Foe(2.0, false, true, false), down, 0);
        assertEquals(Step.STRIKE, plan.step());
        assertEquals(Weapon.MACE, plan.weapon());
    }

    @Test
    void leadsAMovingTarget() {
        double[] at = PlayerFightBrain.lead(0, 64, 0, 0.2, 0.0, 15.0);
        assertEquals(1.0, at[0], 1e-9, "15 blocks is 5 ticks of flight: a block ahead at 0.2 a tick");
        assertEquals(0.0, at[2], 1e-9);
    }
}
