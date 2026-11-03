package com.mineagent.engine.survival;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Hard's own rules (SMH plan 70, MA8.7). */
final class HardRulesTest {
    @Test void hungerKillsOnHardSoTheFoodDoubles() {
        assertEquals(8, HardRules.tripFood(8, false));
        assertEquals(16, HardRules.tripFood(8, true));
        assertEquals(64, HardRules.tripFood(40, true), "kept to a stack");
        assertTrue(HardRules.maySetOut(0, false), "on Normal, hunger stops at half a heart");
        assertFalse(HardRules.maySetOut(0, true), "on Hard, never out with no food");
        assertTrue(HardRules.maySetOut(3, true));
    }

    @Test void neverARaidNeverTheWarden() {
        assertTrue(HardRules.neverUse("minecraft:ominous_bottle"));
        assertFalse(HardRules.neverUse("minecraft:honey_bottle"));
        assertTrue(HardRules.listening("minecraft:sculk_sensor"));
        assertTrue(HardRules.listening("minecraft:sculk_shrieker"));
        assertFalse(HardRules.listening("minecraft:sculk"), "plain sculk hears nothing");
    }
}
