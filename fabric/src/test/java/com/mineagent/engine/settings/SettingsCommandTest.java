package com.mineagent.engine.settings;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** SMH plan 69, MA2.6: what the Agent settings panel may change, checked before it is kept. */
final class SettingsCommandTest {
    @Test void eachSettingTakesOnlyItsOwnValues() {
        AgentSettings s = AgentSettings.DEFAULTS;
        assertFalse(SettingsCommand.apply(s, "curriculum", "off").settings().curriculum());
        assertEquals("low", SettingsCommand.apply(s, "experiments", "LOW").settings().experiments());
        assertEquals("calm", SettingsCommand.apply(s, "callrate", "calm").settings().callRate());
        assertEquals("Cheerful, short answers.", SettingsCommand.apply(s, "persona", " Cheerful, short answers. ").settings().persona());
        assertEquals("", SettingsCommand.apply(s.withPersona("x"), "persona", "none").settings().persona());
        for (String[] bad : new String[][] {{"curriculum", "maybe"}, {"experiments", "max"}, {"callrate", "frantic"},
                {"persona", "x".repeat(601)}, {"trusted", "Bob"}, {"apiKey", "sk-123"}}) {
            var r = SettingsCommand.apply(s, bad[0], bad[1]);
            assertFalse(r.ok(), String.join(" ", bad));
            assertEquals(s, r.settings(), "a refused change keeps the settings as they were");
        }
    }

    @Test void theSettingsRoundTripAsJson() {
        AgentSettings s = SettingsCommand.apply(AgentSettings.DEFAULTS, "callrate", "normal").settings();
        assertEquals(s, AgentSettings.fromJson(s.toJson()));
    }

    @Test void thePanelIsShownEverythingButTheKey() {
        String json = SettingsCommand.state(AgentSettings.DEFAULTS, List.of("Alex"),
                List.of(new SettingsCommand.SkillLine("mine_stone", "score 80/100 over 3 runs (best 90, last 70)")),
                "smh/auto", "http://100.64.0.1:47821/apps", true);
        var o = JsonParser.parseString(json).getAsJsonObject();
        assertEquals("Alex", o.getAsJsonArray("trusted").get(0).getAsString());
        assertEquals("busy", o.get("callRate").getAsString());
        assertTrue(o.get("keySet").getAsBoolean());
        assertFalse(json.contains("smhg_"), "never the key itself");
        assertEquals("mine_stone", o.getAsJsonArray("skills").get(0).getAsJsonObject().get("name").getAsString());
    }
}
