package com.mineagent.engine.combat;

import com.mineagent.engine.combat.CombatBrain.Hostile;
import com.mineagent.engine.combat.CombatBrain.Self;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RangedChoiceTest {
    static final Self ARMED = new Self(20, 20, 10, true, 3, true, 32, 20, false);
    static final Self NO_ARROWS = new Self(20, 20, 10, true, 3, true, 0, 20, false);

    static Hostile h(String type, double d, boolean targetsSelf, boolean sight) {
        return new Hostile(type, d, 0, 20, targetsSelf, false, false, false, sight, 0, targetsSelf);
    }

    @Test void aCreeperComingIsShotAndANearZombieIsTheSwordsTurn() {
        assertEquals(0, RangedChoice.pick(List.of(h("creeper", 14, true, true)), ARMED, false, false));
        assertEquals(-1, RangedChoice.pick(List.of(h("creeper", 14, true, true), h("zombie", 3, true, true)), ARMED, false, false));
        assertEquals(-1, RangedChoice.pick(List.of(h("creeper", 4, true, true)), ARMED, false, false), "too close to draw: step back");
    }

    @Test void theArcherIsAnsweredFirst() {
        var hs = List.of(h("zombie", 12, true, true), h("skeleton", 20, true, true));
        assertEquals(1, RangedChoice.pick(hs, ARMED, false, false));
    }

    @Test void neverWhatArrowsMissOrWithoutArrowsOrInWaterOrUnseen() {
        assertEquals(-1, RangedChoice.pick(List.of(h("enderman", 12, true, true)), ARMED, false, false));
        assertEquals(-1, RangedChoice.pick(List.of(h("breeze", 12, true, true)), ARMED, false, false));
        assertEquals(-1, RangedChoice.pick(List.of(h("warden", 20, true, true)), ARMED, false, false));
        assertEquals(-1, RangedChoice.pick(List.of(h("skeleton", 20, true, true)), NO_ARROWS, false, false));
        assertEquals(-1, RangedChoice.pick(List.of(h("skeleton", 20, true, true)), ARMED, true, false));
        assertEquals(-1, RangedChoice.pick(List.of(h("skeleton", 20, true, false)), ARMED, false, false));
        assertEquals(-1, RangedChoice.pick(List.of(h("skeleton", 50, true, true)), ARMED, false, false));
    }

    @Test void aMobMindingItsOwnBusinessIsLeftUnlessOnGuard() {
        var idle = List.of(h("zombie", 18, false, true));
        assertEquals(-1, RangedChoice.pick(idle, ARMED, false, false));
        assertEquals(0, RangedChoice.pick(idle, ARMED, false, true));
        assertEquals(-1, RangedChoice.pick(List.of(h("zombified_piglin", 18, false, true)), ARMED, false, true), "a calm one stays calm");
    }

    @Test void withABowWhatComesForItFarOffIsItsBusiness() {
        var far = List.of(h("skeleton", 30, true, true));
        var noBow = new Self(20, 20, 10, true, 3, false, 0, 20, false);
        assertEquals(CombatBrain.Posture.SAFE, CombatBrain.assess(far, noBow, Tactics.of("balanced", 6)).posture());
        assertNotEquals(CombatBrain.Posture.SAFE, CombatBrain.assess(far, ARMED, Tactics.of("balanced", 6)).posture());
        assertEquals(CombatBrain.Posture.SAFE, CombatBrain.assess(List.of(h("skeleton", 30, true, false)), ARMED, Tactics.of("balanced", 6)).posture(),
                "out of sight it is not shot at, so not fought");
    }
}
