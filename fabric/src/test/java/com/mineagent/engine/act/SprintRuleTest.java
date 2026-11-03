package com.mineagent.engine.act;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** SMH plan 70, MA5.10: the companion sprints only when the game would let a player. */
final class SprintRuleTest {
    @Test void aPlayerWithFoodToSpareMaySprint() {
        assertTrue(SprintRule.allowed(20, false, false, false, false));
        assertTrue(SprintRule.allowed(7, false, false, false, false));
    }

    @Test void sixFoodOrLessStopsItUnlessItCanFly() {
        assertFalse(SprintRule.allowed(6, false, false, false, false));
        assertFalse(SprintRule.allowed(0, false, false, false, false));
        assertTrue(SprintRule.allowed(6, true, false, false, false));
    }

    @Test void blindnessSneakingAndUsingAnItemStopIt() {
        assertFalse(SprintRule.allowed(20, false, true, false, false));
        assertFalse(SprintRule.allowed(20, false, false, true, false));
        assertFalse(SprintRule.allowed(20, false, false, false, true));
    }
}
