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

    @Test void itWorksWithin32BlocksAndWithin8AtAFarm() {
        assertEquals(32.0, AfkRules.leash(false));
        assertEquals(8.0, AfkRules.leash(true));
        assertTrue(AfkRules.FARM_QUIET > AfkRules.FARM_LEASH, "no block changes farther out than it stays");
    }

    @Test void aQuestionEndsWithAQuestionMark() {
        assertTrue(AfkRules.isQuestion("Shall I smelt the iron?"));
        assertTrue(AfkRules.isQuestion("  Which chest? "));
        assertFalse(AfkRules.isQuestion("Smelting the iron."));
        assertFalse(AfkRules.isQuestion(null));
    }
}
