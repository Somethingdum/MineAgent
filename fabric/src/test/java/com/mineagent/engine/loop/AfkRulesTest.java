package com.mineagent.engine.loop;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The AFK rules that are numbers and words (SMH plan 70, MA5.4). */
final class AfkRulesTest {
    @Test void fishingOrStandingAtAFarmsHoppersIsAFarm() {
        assertTrue(AfkRules.atFarm(true, 0), "fishing");
        assertTrue(AfkRules.atFarm(false, 2), "two hoppers by them: a farm's collection");
        assertFalse(AfkRules.atFarm(false, 1), "one hopper is a furnace's, not a farm");
        assertFalse(AfkRules.atFarm(false, 0));
    }

    @Test void awayMeansNoLimitUnlessADistanceIsSet() {
        assertEquals(Double.POSITIVE_INFINITY, AfkRules.leash(0), "AFK only means they're away: no limit");
        assertEquals(32.0, AfkRules.leash(32));
        assertEquals(Double.POSITIVE_INFINITY, AfkRules.leash(-5));
    }

    @Test void aQuestionEndsWithAQuestionMark() {
        assertTrue(AfkRules.isQuestion("Shall I smelt the iron?"));
        assertTrue(AfkRules.isQuestion("  Which chest? "));
        assertFalse(AfkRules.isQuestion("Smelting the iron."));
        assertFalse(AfkRules.isQuestion(null));
    }
}
