package com.mineagent.api.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** SMH plan 70, Switches: each block 1 behaviour has a setting, on by default, kept across older config files. */
final class BehaviourSwitchesTest {
    @Test void aFileFromBeforeTheSwitchesHasThemAllOn() throws Exception {
        Path dir = Files.createTempDirectory("mineagent-config");
        Files.writeString(dir.resolve("mineagent.json"), "{\"companion\":{\"name\":\"Pal\"}}");
        MineAgentConfig config = MineAgentConfig.load(dir);
        assertEquals("Pal", config.companion().name());
        assertEquals(MineAgentConfig.BehaviourConfig.DEFAULTS, config.behaviour());
        assertTrue(config.behaviour().neverBreakBuilt());
        assertEquals(3, config.behaviour().afkMinutes());
    }

    @Test void aSwitchTurnedOffStaysOffAndTimesAreKeptInRange() throws Exception {
        Path dir = Files.createTempDirectory("mineagent-config");
        Files.writeString(dir.resolve("mineagent.json"), "{\"behaviour\":{\"sprint\":false,\"afkMinutes\":999}}");
        MineAgentConfig config = MineAgentConfig.load(dir);
        assertFalse(config.behaviour().sprint());
        assertTrue(config.behaviour().closeDoors());
        assertEquals(60, config.behaviour().afkMinutes());
    }

    @Test void theOlderConstructorGivesTheDefaults() {
        var c = MineAgentConfig.DEFAULTS;
        var same = new MineAgentConfig(c.llm(), c.companion(), c.survival(), c.pathfinding());
        assertEquals(MineAgentConfig.BehaviourConfig.DEFAULTS, same.behaviour());
    }

    @Test void memorySizesHaveDefaultsAndStayInRange() throws Exception {
        Path dir = Files.createTempDirectory("mineagent-config");
        Files.writeString(dir.resolve("mineagent.json"), "{\"memory\":{\"thingsSeen\":5,\"journalDays\":30}}");
        MineAgentConfig config = MineAgentConfig.load(dir);
        assertEquals(1_000, config.memory().thingsSeen());
        assertEquals(30, config.memory().journalDays());
        assertEquals(4_096, config.memory().chests());
        assertEquals(50_000, MineAgentConfig.DEFAULTS.memory().thingsSeen());
    }

    @Test void afkChangesNothingOfItsJobsOrDistanceUnlessAsked() throws Exception {
        var d = MineAgentConfig.BehaviourConfig.DEFAULTS;
        assertFalse(d.afkGuardMe(), "guarding an AFK owner is optional, off");
        assertEquals(0, d.afkStayWithin(), "no distance to keep within");
        assertTrue(d.afkFarmLeftAlone(), "a farm they're AFK at is still left alone");
        assertTrue(d.afkKeepBusy(), "AFK just means away: it keeps busy");
        assertTrue(d.huntForOrders(), "food gathering was asked for: it hunts for an order");
        assertTrue(d.oreSightOnly(), "fair play: only ore it has seen");
        assertTrue(d.readyCheck(), "ready before a trip");
        assertEquals(8, d.readyFood());
        assertEquals(16, d.readyTorches());
        assertTrue(d.sparePickaxe());
        assertTrue(d.escapeLavaFire(), "a check that only prevents harm: on");
        // A file saved with the earlier default ("afkGuard": true, and "afkKeepWorking") has no say over the new ones.
        Path dir = Files.createTempDirectory("mineagent-config");
        Files.writeString(dir.resolve("mineagent.json"),
                "{\"behaviour\":{\"afkGuard\":true,\"afkKeepWorking\":false,\"afkStayWithin\":9999}}");
        MineAgentConfig config = MineAgentConfig.load(dir);
        assertFalse(config.behaviour().afkGuardMe());
        assertEquals(256, config.behaviour().afkStayWithin(), "kept in range");
    }
}
