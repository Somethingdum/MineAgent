package com.mineagent.engine.combat;

import com.mineagent.engine.combat.CombatBrain.Action;
import com.mineagent.engine.combat.CombatBrain.Context;
import com.mineagent.engine.combat.CombatBrain.Duty;
import com.mineagent.engine.combat.CombatBrain.Hostile;
import com.mineagent.engine.combat.CombatBrain.Move;
import com.mineagent.engine.combat.CombatBrain.Posture;
import com.mineagent.engine.combat.CombatBrain.Self;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** SMH plan 69, block 3: the fighting mind, its "never" rules, and fighting only what is its business. */
final class CombatBrainTest {
    private static final Self STRONG = new Self(20, 20, 15, true, 4, true, 32, 18, false);
    private static final Self WEAK = new Self(12, 20, 0, false, 2, false, 0, 10, false);
    private static final Self BARE = new Self(20, 20, 15, false, 4, false, 0, 18, false);
    private static final Tactics BALANCED = Tactics.of("balanced", 6);
    private static final Context WORK = new Context(Duty.WORKING, 6, 0);
    private static final Context GUARD = new Context(Duty.GUARD, 10, 2);

    private static Hostile mob(String type, double distance, boolean atMe) {
        return new Hostile(type, distance, 0, 20, atMe, false, false, false, true, 0, atMe);
    }

    /** type, distance, at me, from the anchor. */
    private static Hostile mobAt(String type, double distance, boolean atMe, double fromAnchor) {
        return new Hostile(type, distance, 0, 20, atMe, false, false, false, true, 0, atMe, fromAnchor, false);
    }

    private static Action act(Hostile h, Self self, double charge, boolean onGround, boolean falling,
                              boolean skeletonDrawing, Context c) {
        return CombatBrain.decide(CombatBrain.assess(List.of(h), self, BALANCED, c), h, self, charge, onGround,
                falling, false, false, skeletonDrawing, BALANCED, 0, c);
    }

    @Test void theNeverRulesHoldWhateverTheGear() {
        assertEquals(Posture.FLEEING, CombatBrain.assess(List.of(mob("minecraft:warden", 20, false)), STRONG, BALANCED).posture());
        assertEquals(Posture.FLEEING, CombatBrain.assess(List.of(mob("vindicator", 6, true)), WEAK, BALANCED).posture());
        assertEquals(Posture.ENGAGED, CombatBrain.assess(List.of(mob("vindicator", 6, true)), STRONG, BALANCED).posture());
        Hostile calm = new Hostile("enderman", 4, 0, 40, false, false, false, false, true, 0, false);
        assertEquals(Posture.SAFE, CombatBrain.assess(List.of(calm), STRONG, BALANCED).posture());
        assertEquals(Posture.FLEEING, CombatBrain.assess(List.of(mob("zombie", 2, true)),
                new Self(5, 20, 15, true, 4, true, 0, 18, false), BALANCED).posture(), "health under the retreat line");
    }

    @Test void meleeStrikesOnlyAtFullChargeWithCritsAndTheShieldBetweenHits() {
        Hostile z = mob("zombie", 2, true);
        Context idle = Context.idle();
        Action jump = act(z, STRONG, 1.0, true, false, false, idle);
        assertTrue(jump.jump() && !jump.attack(), "crit jump " + jump);
        Action strike = act(z, STRONG, 1.0, false, true, false, idle);
        assertTrue(strike.attack() && !strike.shield(), "strike while falling " + strike);
        Action wait = act(z, STRONG, 0.4, true, false, false, idle);
        assertTrue(wait.shield() && !wait.attack(), "shield while recharging " + wait);
        Action circle = act(z, BARE, 0.4, true, false, false, idle);
        assertTrue(!circle.shield() && circle.strafe() != 0, "circle without a shield " + circle);
    }

    @Test void theCreeperIsNeverStruckBareAndAFuseMeansShieldOrRun() {
        Hostile fusing = new Hostile("creeper", 3, 0, 20, true, false, true, false, true, 0, true);
        Action withShield = act(fusing, STRONG, 1.0, true, false, false, Context.idle());
        assertTrue(withShield.move() == Move.BACK_OFF && withShield.shield(), "fusing, shield " + withShield);
        Action without = act(fusing, BARE, 1.0, true, false, false, Context.idle());
        assertTrue(without.move() == Move.FLEE && without.sprint(), "fusing, no shield " + without);
        Hostile near = new Hostile("creeper", 2.0, 0, 20, true, false, false, false, true, 0, true);
        Action bare = act(near, BARE, 1.0, true, false, false, Context.idle());
        assertTrue(!bare.attack() && bare.move() == Move.BACK_OFF, "never bare at 2 blocks " + bare);
        Hostile reach = new Hostile("creeper", 2.8, 0, 20, true, false, false, false, true, 0, true);
        assertTrue(act(reach, STRONG, 1.0, true, false, false, Context.idle()).attack(), "strike it with a shield");
    }

    @Test void archersAreMetBehindTheShieldZigZagging() {
        Hostile sk = new Hostile("skeleton", 10, 0, 20, true, false, false, true, true, 0, true);
        Action a = act(sk, STRONG, 1.0, true, false, true, Context.idle());
        assertTrue(a.move() == Move.APPROACH && a.shield() && a.strafe() != 0, a.toString());
    }

    @Test void beingSurroundedCountsAndGearCounts() {
        List<Hostile> ring = List.of(
                new Hostile("zombie", 3, 0, 20, true, false, false, false, true, 0, true),
                new Hostile("zombie", 3, 0, 20, true, false, false, false, true, 130, true),
                new Hostile("husk", 4, 0, 20, true, false, false, false, true, 250, true));
        assertEquals(Posture.OVERWHELMED, CombatBrain.assess(ring, WEAK, BALANCED).posture());
        assertEquals(Posture.ENGAGED, CombatBrain.assess(ring, STRONG, BALANCED).posture());
        var pick = CombatBrain.assess(List.of(mob("zombie", 2, true), mob("skeleton", 8, true)), STRONG,
                new Tactics("balanced", List.of("skeleton"), 6, true, true));
        assertEquals(1, pick.primary(), "the advised target order");
    }

    @Test void atWorkItAnswersOnlyWhatComesAtItCloseBy() {
        assertEquals(Posture.SAFE, CombatBrain.assess(List.of(mobAt("zombie", 10, false, 10)), STRONG, BALANCED, WORK).posture(),
                "a zombie idling 10 blocks off in the cave");
        assertEquals(Posture.ENGAGED, CombatBrain.assess(List.of(mobAt("zombie", 6, true, 6)), STRONG, BALANCED, WORK).posture(),
                "one coming at it");
        assertEquals(Posture.SAFE, CombatBrain.assess(List.of(mobAt("zombie", 12, true, 12)), STRONG, BALANCED, WORK).posture(),
                "an attacker 12 blocks out is met when it is close");
        assertEquals(Posture.SAFE, CombatBrain.assess(List.of(mobAt("zombie", 9, false, 9)), STRONG, BALANCED).posture(),
                "idle: a passive zombie is not its business");
        // A skeleton shooting at it from across the cave is dealt with (and then it goes back to work)...
        Hostile sniper = new Hostile("skeleton", 12, 0, 20, true, false, false, true, true, 0, true, 12, false);
        assertEquals(Posture.ENGAGED, CombatBrain.assess(List.of(sniper), STRONG, BALANCED, WORK).posture());
        assertEquals(Move.APPROACH, act(sniper, STRONG, 1.0, true, false, true, WORK).move(), "goes for the archer");
        // ...but one with no shot at it, or one not shooting, is not.
        Hostile hidden = new Hostile("skeleton", 12, 0, 20, true, false, false, true, false, 0, true, 12, false);
        assertEquals(Posture.SAFE, CombatBrain.assess(List.of(hidden), STRONG, BALANCED, WORK).posture());
        Hostile idleArcher = new Hostile("skeleton", 12, 0, 20, false, false, false, false, true, 0, false, 12, false);
        assertEquals(Posture.SAFE, CombatBrain.assess(List.of(idleArcher), STRONG, BALANCED, WORK).posture());
        // Done with it 15 blocks out, it heads back to the work spot rather than chasing the next thing.
        Context farOut = new Context(Duty.WORKING, 6, 15);
        Hostile wanderer = new Hostile("zombie", 3.5, 0, 20, false, false, false, false, true, 0, false, 18, false);
        assertEquals(Move.RETURN, act(wanderer, STRONG, 1.0, true, false, false, farOut).move());
    }

    @Test void aGuardClearsItsPerimeterAndNeverChasesOutOfIt() {
        assertEquals(Posture.ENGAGED, CombatBrain.assess(List.of(mobAt("zombie", 9, false, 6)), STRONG, BALANCED, GUARD).posture(),
                "inside the perimeter it is cleared, not watched");
        assertEquals(Posture.SAFE, CombatBrain.assess(List.of(mobAt("zombie", 8, true, 15)), STRONG, BALANCED, GUARD).posture(),
                "a zombie outside is left alone");
        Hostile shooting = new Hostile("skeleton", 14, 0, 20, true, false, false, true, true, 0, true, 14, false);
        assertNotEquals(Posture.SAFE, CombatBrain.assess(List.of(shooting), STRONG, BALANCED, GUARD).posture(),
                "a skeleton shooting in from just outside");
        Context offPost = new Context(Duty.GUARD, 10, 13);
        Action back = act(new Hostile("zombie", 6, 0, 20, false, false, false, false, true, 0, false, 8, false),
                STRONG, 1.0, true, false, false, offPost);
        assertEquals(Move.RETURN, back.move(), "off its post: back first");
        Hostile leaving = new Hostile("zombie", 6, 0, 20, false, false, false, false, true, 0, true, 13, true);
        Move m = act(leaving, STRONG, 1.0, true, false, false, GUARD).move();
        assertTrue(m == Move.RETURN || m == Move.HOLD, "never chased out: " + m);
        Hostile farArcher = new Hostile("skeleton", 20, 0, 20, true, false, false, true, true, 0, true, 20, false);
        assertEquals(Posture.SAFE, CombatBrain.assess(List.of(farArcher), STRONG, BALANCED, GUARD).posture(),
                "an archer 10 past the perimeter is not chased");
    }

    @Test void arrowsArePredictedFromWhereTheyAreGoing() {
        assertTrue(ArrowPredictor.incoming(10, 1.5, 0, -1.5, 0.05, 0, ArrowPredictor.ARROW_GRAVITY, 0, 1, 0));
        assertFalse(ArrowPredictor.incoming(10, 1.5, 0, 1.5, 0, 0, ArrowPredictor.ARROW_GRAVITY, 0, 1, 0), "going away");
        assertFalse(ArrowPredictor.incoming(10, 1.5, 5, -1.5, 0, 0, ArrowPredictor.ARROW_GRAVITY, 0, 1, 0), "5 blocks wide");
        double[] p = ArrowPredictor.closestApproach(0, 10, 0, 1, 1, 0, 0.05, 10, 0);
        assertEquals(10 + 10 - 0.5 * 0.05 * 100, p[1], 1e-9, "a rising arrow keeps rising first");
    }

    @Test void theModelsTacticsAreCheckedAndClamped() {
        var t = Tactics.parse("<think>x</think>{\"stance\":\"Kite\",\"target_order\":[\"minecraft:skeleton\",\"zombie\"],"
                + "\"retreat_health\":1,\"shield\":true}", 6).orElseThrow();
        assertEquals("kite", t.stance());
        assertEquals(4, t.retreatHealth());
        assertEquals(List.of("skeleton", "zombie"), t.targetOrder());
        assertTrue(Tactics.parse("{\"stance\":\"berserk\"}", 6).isEmpty());
        assertTrue(Tactics.parse("nope", 6).isEmpty());
        assertEquals(16, Tactics.parse("{\"stance\":\"aggressive\",\"retreat_health\":30}", 6).orElseThrow().retreatHealth());
    }

    @Test void theMobTableNamesAreReadWithOrWithoutTheirNamespace() {
        assertTrue(MobPolicy.of("minecraft:creeper").neverMeleeBare());
        assertTrue(MobPolicy.of("WARDEN").mustFlee());
        assertSame(MobPolicy.DEFAULT, MobPolicy.of("some_mod:unknown_beast"));
        assertSame(MobPolicy.DEFAULT, MobPolicy.of(null));
    }
}
