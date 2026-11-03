package com.mineagent.engine.settings;

import com.mineagent.api.config.MineAgentConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** A switch or a memory size changed from the panel or /mineagent setconfig (SMH plan 70). */
final class SettingChangeTest {
    @Test void aSwitchGoesOffAndOnAndTheRestStayAsTheyWere() {
        MineAgentConfig c = MineAgentConfig.DEFAULTS;
        MineAgentConfig off = SettingChange.withSetting(c, "behaviour.sprint", "off");
        assertNotNull(off);
        assertFalse(off.behaviour().sprint());
        assertTrue(off.behaviour().swim(), "only the one switch changed");
        assertEquals(c.llm(), off.llm());
        assertEquals(c.memory(), off.memory());
        assertTrue(SettingChange.withSetting(off, "behaviour.SPRINT", "on").behaviour().sprint(), "any case");
        assertTrue(SettingChange.withSetting(c, "behaviour.afkGuardMe", "on").behaviour().afkGuardMe());
        assertFalse(SettingChange.withSetting(c, "behaviour.afkFarmLeftAlone", "off").behaviour().afkFarmLeftAlone());
    }

    @Test void numbersAreKeptInRange() {
        MineAgentConfig c = MineAgentConfig.DEFAULTS;
        assertEquals(10, SettingChange.withSetting(c, "behaviour.afkMinutes", "10").behaviour().afkMinutes());
        assertEquals(60, SettingChange.withSetting(c, "behaviour.afkMinutes", "500").behaviour().afkMinutes());
        assertEquals(1, SettingChange.withSetting(c, "behaviour.afkMinutes", "0").behaviour().afkMinutes());
        assertEquals(200_000, SettingChange.withSetting(c, "memory.thingsSeen", "200,000").memory().thingsSeen());
        assertEquals(500_000, SettingChange.withSetting(c, "memory.thingsSeen", "9999999").memory().thingsSeen());
        assertEquals(3_650, SettingChange.withSetting(c, "memory.journalDays", "100000").memory().journalDays());
        assertEquals(64, SettingChange.withSetting(c, "memory.chests", "1").memory().chests());
        assertEquals(32, SettingChange.withSetting(c, "behaviour.afkStayWithin", "32").behaviour().afkStayWithin());
        assertEquals(256, SettingChange.withSetting(c, "behaviour.afkStayWithin", "1000").behaviour().afkStayWithin());
        assertEquals(0, SettingChange.withSetting(c, "behaviour.afkStayWithin", "off").behaviour().afkStayWithin(), "no limit");
        assertEquals(0, SettingChange.withSetting(c, "behaviour.afkStayWithin", "no limit").behaviour().afkStayWithin());
    }

    @Test void anythingElseIsRefused() {
        MineAgentConfig c = MineAgentConfig.DEFAULTS;
        assertNull(SettingChange.withSetting(c, "behaviour.flying", "on"), "no such switch");
        assertNull(SettingChange.withSetting(c, "behaviour.sprint", "maybe"));
        assertNull(SettingChange.withSetting(c, "memory.chests", "lots"));
        assertNull(SettingChange.withSetting(c, "llm.apiKey", "x"), "not a behaviour or memory setting");
        assertNull(SettingChange.withSetting(c, "sprint", "on"));
        assertNull(SettingChange.withSetting(null, "behaviour.sprint", "on"));
    }
}
