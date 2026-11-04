package com.mineagent.engine.combat;

import com.mineagent.engine.combat.CombatBrain.Hostile;
import com.mineagent.engine.combat.CombatBrain.Self;
import com.mineagent.engine.combat.FightWeighing.Call;
import com.mineagent.engine.combat.FightWeighing.Charge;
import com.mineagent.engine.combat.FightWeighing.Doing;
import com.mineagent.engine.combat.FightWeighing.Reasons;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The owner's cases, one by one. */
class FightWeighingTest {
    static final Self FIT = new Self(20, 20, 15, true, 3, true, 32, 18, false);
    static final Self HURT = new Self(8, 20, 15, true, 3, true, 32, 18, false);
    static final Doing RUNNING_HOME = new Doing(true, true, false, false, false);
    static final Doing TUNNEL = new Doing(true, false, true, false, false);
    static final Doing MIDDAY_TRIP = new Doing(true, true, false, true, false);
    static final Doing AT_HOME = Doing.idle();

    static Hostile mob(String type, double d, boolean atMe, boolean bow) {
        return new Hostile(type, d, atMe ? 0.1 : 0, 20, atMe, false, false, bow, true, 0, atMe);
    }

    static Hostile atOwner(String type, double d) {
        return new Hostile(type, d, 0, 20, false, true, false, false, true, 0, true);
    }

    static Call w(Hostile h, Self me, Doing d) {
        return FightWeighing.weigh(h, me, 1.0, d, Reasons.none(), null);
    }

    @Test void runningHomeInTheDarkAndShotAtItKeepsRunning() {
        assertEquals(Call.KEEP_GOING, w(mob("skeleton", 18, true, true), FIT, RUNNING_HOME));
        assertEquals(Call.KEEP_GOING, w(mob("zombie", 6, true, false), FIT, RUNNING_HOME));
        assertEquals(Call.KEEP_GOING, w(mob("zombie", 2, true, false), FIT, RUNNING_HOME), "a zombie is outrun");
        assertEquals(Call.FIGHT, w(mob("spider", 6, true, false), FIT, RUNNING_HOME), "a spider would catch it");
    }

    @Test void shutInWithAMobItKillsIt() {
        assertEquals(Call.FIGHT, w(mob("zombie", 6, true, false), FIT, TUNNEL));
        assertEquals(Call.FIGHT, w(mob("skeleton", 7, true, true), FIT, TUNNEL));
    }

    @Test void aCreeperByDayThatCantReachItIsPassed() {
        assertEquals(Call.LET_BE, w(mob("creeper", 14, false, false), FIT, MIDDAY_TRIP));
        assertEquals(Call.KEEP_GOING, w(mob("creeper", 14, true, false), FIT, MIDDAY_TRIP), "coming, but far: on its way");
        assertEquals(Call.FIGHT, w(mob("creeper", 7, true, false), FIT, AT_HOME), "close and coming, with a bow: shot");
    }

    @Test void itStepsInForItsOwnerOnlyWhenTheyCouldBeSeriouslyHurt() {
        var fineAndArmoured = new Charge(20, 20, 15, true, 3, 1, false);
        var low = new Charge(6, 20, 15, true, 3, 1, false);
        var swarmed = new Charge(20, 20, 0, true, 9, 3, false);
        var creeperByThem = new Charge(20, 20, 20, true, 0, 0, true);
        var saidNo = new Charge(4, 20, 0, false, 9, 3, true);
        Hostile z = atOwner("zombie", 10);
        assertEquals(Call.LET_BE, FightWeighing.weigh(z, FIT, 1, AT_HOME, Reasons.none(), fineAndArmoured), "hurt a little is not seriously");
        assertEquals(Call.FIGHT, FightWeighing.weigh(z, FIT, 1, AT_HOME, Reasons.none(), low));
        assertEquals(Call.FIGHT, FightWeighing.weigh(z, FIT, 1, AT_HOME, Reasons.none(), swarmed));
        assertEquals(Call.FIGHT, FightWeighing.weigh(atOwner("creeper", 9), FIT, 1, AT_HOME, Reasons.none(), creeperByThem));
        assertEquals(Call.LET_BE, FightWeighing.weigh(z, FIT, 1, AT_HOME, Reasons.none(), saidNo), "told: don't protect me");
    }

    @Test void aReasonAndTheMeansMakeAFightWorthIt() {
        var string = new Reasons(Set.of("spider"), false);
        Hostile spider = mob("spider", 12, false, false);
        assertEquals(Call.FIGHT, FightWeighing.weigh(spider, FIT, 1, AT_HOME, string, null), "a job needs its string");
        assertEquals(Call.LET_BE, FightWeighing.weigh(spider, HURT, 1, AT_HOME, string, null), "not at 8 health");
        assertEquals(Call.LET_BE, FightWeighing.weigh(spider, new Self(20, 20, 15, true, 3, false, 0, 18, false), 0.05, AT_HOME, string, null),
                "not with its sword about to break");
        assertEquals(Call.FIGHT, FightWeighing.weigh(mob("zombie", 12, false, false), FIT, 1, AT_HOME, new Reasons(Set.of(), true), null),
                "it wants the experience");
        assertEquals(Call.LET_BE, FightWeighing.weigh(mob("zombie", 12, false, false), FIT, 1, AT_HOME, Reasons.none(), null), "no reason");
    }

    static final Self SWORD_ONLY = new Self(20, 20, 15, true, 3, false, 0, 18, false);

    @Test void idleItMeetsWhatComesWhenItGetsHere() {
        assertEquals(Call.LET_BE, w(mob("zombie", 10, true, false), SWORD_ONLY, AT_HOME));
        assertEquals(Call.FIGHT, w(mob("zombie", 2, true, false), SWORD_ONLY, AT_HOME));
        assertEquals(Call.FIGHT, w(mob("zombie", 10, true, false), FIT, AT_HOME), "with a bow, shot as it comes");
        assertEquals(Call.KEEP_GOING, w(mob("zombie", 10, true, false), FIT, RUNNING_HOME), "but not on the way home");
        assertEquals(Call.FIGHT, w(mob("skeleton", 16, true, true), FIT, AT_HOME), "an archer isn't let go on shooting");
        assertEquals(Call.KEEP_GOING, w(mob("warden", 20, true, false), FIT, AT_HOME), "never a warden");
        assertEquals(Call.FIGHT, w(mob("skeleton", 16, true, true), FIT, new Doing(true, false, false, false, false)),
                "at work on the spot, an archer pins it down: dealt with");
    }
}
