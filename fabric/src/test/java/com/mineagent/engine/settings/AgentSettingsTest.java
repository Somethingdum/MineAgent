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

    @Test void keepsOnlyRealUuidsOnceEach() {
        UUID a = UUID.randomUUID();
        AgentSettings s = new AgentSettings(List.of("not-a-uuid", a.toString().toUpperCase(), a.toString()), null);
        assertEquals(List.of(a.toString()), s.trusted());
        assertEquals("", s.persona());
        assertFalse(s.withoutTrusted(a).isTrusted(a));
        assertSame(s, s.withTrusted(a));
    }
}
