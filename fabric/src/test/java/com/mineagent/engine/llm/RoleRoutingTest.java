package com.mineagent.engine.llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** SMH plan 69, MA1.6: each turn's role by its trigger, and the model id sent for it. */
final class RoleRoutingTest {
    @Test void planningWakesArePlanAndEveryOtherTurnIsAct() {
        for (String t : new String[] {"rolling_replan", "goal_verified", "curriculum", "goal_book"}) {
            assertEquals("plan", RoleRouting.roleForTrigger(t), t);
        }
        for (String t : new String[] {"owner_message", "body_log", "cognition_decision", "companion_spawned", "team_event", "anything_else", ""}) {
            assertEquals("act", RoleRouting.roleForTrigger(t), t);
        }
        assertEquals("act", RoleRouting.roleForTrigger(null));
    }

    @Test void smhAutoSendsTheRoleAndAnyOtherModelPassesThroughUnchanged() {
        assertEquals("smh/act", RoleRouting.modelFor("smh/auto", "act"));
        assertEquals("smh/plan", RoleRouting.modelFor("smh/auto", "plan"));
        assertEquals("smh/talk", RoleRouting.modelFor("smh/auto", "talk"));
        assertEquals("deepseek-chat", RoleRouting.modelFor("deepseek-chat", "plan"));
        assertEquals("smh/fight", RoleRouting.modelFor("smh/fight", "act"));
        assertEquals("SMH/AUTO", RoleRouting.modelFor("SMH/AUTO", "act"));
        assertNull(RoleRouting.modelFor(null, "act"));
    }
}
