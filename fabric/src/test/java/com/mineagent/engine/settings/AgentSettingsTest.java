package com.mineagent.engine.settings;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** SMH plan 69, MA1.7: the companion's own settings, kept as JSON with the companion. */
final class AgentSettingsTest {
    @Test void roundTripsThroughJson() {
        UUID a = UUID.randomUUID();
        AgentSettings s = AgentSettings.DEFAULTS.withTrusted(a).withPersona("Dry wit, short answers.");
        assertEquals(s, AgentSettings.fromJson(s.toJson()));
        assertTrue(AgentSettings.fromJson(s.toJson()).isTrusted(a));
    }

    @Test void oldSavesAndBrokenTextGiveTheDefaults() {
        assertEquals(AgentSettings.DEFAULTS, AgentSettings.fromJson(null));
        assertEquals(AgentSettings.DEFAULTS, AgentSettings.fromJson(""));
        assertEquals(AgentSettings.DEFAULTS, AgentSettings.fromJson("{not json"));
        assertEquals(AgentSettings.DEFAULTS, AgentSettings.fromJson("[1,2]"));
    }

    @Test void playersItMayAttackAreKeptClearedAndSaved() {
        UUID enemy = UUID.randomUUID();
        AgentSettings s = AgentSettings.DEFAULTS.withTarget(enemy);
        assertTrue(s.isAllowedTarget(enemy));
        assertFalse(AgentSettings.DEFAULTS.isAllowedTarget(enemy), "nobody by default");
        assertTrue(AgentSettings.fromJson(s.toJson()).isAllowedTarget(enemy), "kept with the companion");
        assertFalse(s.withoutTargets().isAllowedTarget(enemy), "the owner's stop clears it");
        assertTrue(s.withMode("auto").withPersona("gruff").isAllowedTarget(enemy), "other changes keep it");
        // Saved before targets existed: none.
        assertTrue(AgentSettings.fromJson("{\"trusted\":[],\"mode\":\"auto\"}").allowedTargets().isEmpty());
        AgentSettings full = AgentSettings.DEFAULTS;
        for (int i = 0; i < AgentSettings.MAX_TARGETS + 3; i++) full = full.withTarget(UUID.randomUUID());
        assertEquals(AgentSettings.MAX_TARGETS, full.allowedTargets().size());
    }

    @Test void keepsOnlyRealUuidsOnceEach() {
        UUID a = UUID.randomUUID();
        AgentSettings s = new AgentSettings(List.of("not-a-uuid", a.toString().toUpperCase(), a.toString()), null);
        assertEquals(List.of(a.toString()), s.trusted());
        assertEquals("", s.persona());
        assertFalse(s.withoutTrusted(a).isTrusted(a));
        assertSame(s, s.withTrusted(a));
    }
}
