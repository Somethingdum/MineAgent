package com.mineagent.engine.settings;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** SMH plan 69, MA2.4 and MA2.5: each level sets each limit; Busy and High are the defaults. */
final class CallRateTest {
    @Test void eachCallRateSetsEachLimit() {
        assertEquals(new CallRate("calm", true, 10, 24, 12, 1.0), CallRate.of("calm"));
        assertEquals(new CallRate("normal", true, 14, 32, 16, 1.5), CallRate.of("Normal"));
        assertEquals(new CallRate("busy", false, 20, 40, 20, 2.0), CallRate.of("busy"));
        assertEquals(CallRate.BUSY, CallRate.of(null));
        assertEquals(CallRate.BUSY, CallRate.of("frantic"));
    }

    @Test void eachExperimentLevelSetsTheBudget() {
        assertEquals(6, CallRate.experimentBudget("low"));
        assertEquals(12, CallRate.experimentBudget("normal"));
        assertEquals(24, CallRate.experimentBudget("high"));
        assertEquals(24, CallRate.experimentBudget(null));
    }

    @Test void theSettingsKeepBothAndDefaultToBusyAndHigh() {
        assertEquals("busy", AgentSettings.DEFAULTS.callRate());
        assertEquals("high", AgentSettings.DEFAULTS.experiments());
        AgentSettings s = AgentSettings.DEFAULTS.withCallRate("calm").withExperiments("LOW");
        assertEquals(s, AgentSettings.fromJson(s.toJson()));
        assertEquals("low", s.experiments());
        AgentSettings old = AgentSettings.fromJson("{\"trusted\":[],\"persona\":\"x\"}");
        assertEquals("busy", old.callRate());
        assertEquals("high", old.experiments());
        assertEquals("busy", new AgentSettings(List.of(), "", true, "x", "nonsense").callRate());
    }
}
