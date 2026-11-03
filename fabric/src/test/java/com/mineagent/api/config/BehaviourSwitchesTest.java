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
}
