package com.mineagent.engine.learning;

import com.mineagent.engine.settings.AgentSettings;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** SMH plan 69, MA2.1: practice when nobody needs it, judged by the planner's checks. */
final class CurriculumTest {
    private static final long NOW = 1_000_000L;

    @Test void itAsksOnlyWhenFreeUnorderedSafeAndNotTooOften() {
        Curriculum c = new Curriculum();
        assertTrue(c.due(true, false, 60_000, false, 20, 6, NOW));
        assertFalse(c.due(false, false, 60_000, false, 20, 6, NOW), "switched off");
        assertFalse(c.due(true, true, 60_000, false, 20, 6, NOW), "busy");
        assertFalse(c.due(true, false, 59_999, false, 20, 6, NOW), "an order a moment ago");
        assertFalse(c.due(true, false, 60_000, true, 20, 6, NOW), "a hostile near");
        assertFalse(c.due(true, false, 60_000, false, 6, 6, NOW), "health at the flee line");
        c.asking(NOW);
        assertFalse(c.due(true, false, 60_000, false, 20, 6, NOW + 40_000), "one proposal at a time");
        c.answered();
        assertFalse(c.due(true, false, 60_000, false, 20, 6, NOW + 29_999));
        assertTrue(c.due(true, false, 60_000, false, 20, 6, NOW + 30_000));
    }

    @Test void itPicksATaskAndRecordsDoneAndTooHard() {
        Curriculum c = new Curriculum();
        assertEquals("Mine 3 iron ore", c.parse("<think>hm</think> {\"task\":\"Mine 3 iron ore\",\"why\":\"iron\"}").orElseThrow());
        assertTrue(c.parse("no json").isEmpty());
        for (int i = 0; i < Curriculum.TOO_HARD_AFTER; i++) {
            c.begin("Kill 1 creeper", 0);
            c.failed();
        }
        assertTrue(c.tooHardList().contains("kill 1 creeper"));
        assertTrue(c.parse("{\"task\":\"Kill 1 Creeper\"}").isEmpty(), "a too-hard task is never proposed again");
        c.begin("Mine 3 iron ore", 100);
        assertFalse(c.overdue(100 + Curriculum.TASK_TICKS - 1));
        assertTrue(c.overdue(100 + Curriculum.TASK_TICKS));
        c.verified();
        assertTrue(c.doneList().contains("mine 3 iron ore"));
        assertNull(c.current());
    }

    @Test void anOrderEndsItUncounted() {
        Curriculum c = new Curriculum();
        c.begin("Craft a shield", 0);
        c.abandon();
        assertNull(c.current());
        assertFalse(c.tooHardList().contains("craft a shield"));
        assertFalse(c.doneList().contains("craft a shield"));
    }

    @Test void theProposalIsAPlanCallWithNoToolsAndTheWeakestSkillsInIt() {
        Scorecard s = new Scorecard();
        s.record(new Scorecard.Episode("skill:mine_stone", Scorecard.Outcome.FAILED, false, 1, 10, 20), 1);
        List<String> weakest = s.weakest(List.of("mine_stone", "never_run"), 5);
        assertEquals(List.of("mine_stone (score 8)"), weakest);
        var req = Curriculum.request("http://x", "k", "smh/auto", 0.7, "situation", List.of("mine_stone"), weakest,
                List.of(), List.of(), List.of(), "");
        assertEquals("smh/plan", req.model());
        assertTrue(req.tools().isEmpty());
        assertTrue(req.messages().get(1).content().contains("mine_stone (score 8)"));
    }

    @Test void itsStateAndItsSwitchAreKept() {
        Curriculum c = new Curriculum();
        c.begin("Mine 3 iron ore", 0);
        c.verified();
        Curriculum d = new Curriculum();
        d.importState(c.exportState());
        assertEquals(c.doneList(), d.doneList());
        assertTrue(AgentSettings.DEFAULTS.curriculum());
        assertFalse(AgentSettings.fromJson(AgentSettings.DEFAULTS.withCurriculum(false).toJson()).curriculum());
        assertTrue(AgentSettings.fromJson("{\"trusted\":[]}").curriculum(), "a save from before the switch: on");
    }
}
