package com.mineagent.engine.learning;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Plan 69, harvest from PSN: failure found at the step, that step alone repaired, the worse version rolled back. */
final class SkillRepairTest {
    private static final Set<String> TOOLS = Set.of("goto", "auto_mine", "craft", "interact_at", "equip_item");
    private static final String SEQ = "[{\"tool\":\"goto\",\"args\":{\"x\":1,\"y\":64,\"z\":2},\"bindings\":{\"x\":1}},"
            + "{\"tool\":\"auto_mine\",\"args\":{\"block\":\"iron_ore\",\"count\":3}},{\"tool\":\"craft\",\"args\":{\"item\":\"iron_pickaxe\"}}]";

    private static void run(Scorecard card, String skill, boolean ok, int stoppedAt, long t) {
        for (var e : SkillRepair.stepEpisodes(skill, stoppedAt, 3, ok)) card.record(e, t);
        card.record(new Scorecard.Episode("skill:" + skill, ok ? Scorecard.Outcome.DONE : Scorecard.Outcome.FAILED,
                false, 400, 0, 20), t);
    }

    @Test void aRunRatesTheStepsUpToWhereItStopped() {
        var eps = SkillRepair.stepEpisodes("mine_iron", 1, 3, false);
        assertEquals(2, eps.size());
        assertEquals("step:mine_iron#0", eps.get(0).key());
        assertTrue(eps.get(0).succeeded());
        assertEquals("step:mine_iron#1", eps.get(1).key());
        assertFalse(eps.get(1).succeeded());
        assertEquals(3, SkillRepair.stepEpisodes("mine_iron", 3, 3, true).size());
    }

    @Test void theStepThatKeepsFailingIsBlamedAndNoOther() {
        Scorecard card = new Scorecard();
        for (int i = 0; i < 6; i++) run(card, "mine_iron", i % 3 == 0, 1, i);
        assertEquals(1, SkillRepair.weakestStep(card, "mine_iron", 3).orElseThrow());
        Scorecard fine = new Scorecard();
        for (int i = 0; i < 6; i++) run(fine, "mine_iron", i != 2, 2, i);
        assertTrue(SkillRepair.weakestStep(fine, "mine_iron", 3).isEmpty(), "one failure is not a pattern");
    }

    @Test void onlyTheFailingStepIsRewrittenAndItsBindingsKept() {
        String reply = "<think>…</think>{\"steps\":[{\"tool\":\"equip_item\",\"args\":{\"item_id\":\"stone_pickaxe\"}},"
                + "{\"tool\":\"auto_mine\",\"args\":{\"block\":\"iron_ore\",\"count\":3}}],\"why\":\"needs a pickaxe\"}";
        String fixed = SkillRepair.apply(SEQ, 1, reply, TOOLS).orElseThrow();
        assertTrue(fixed.startsWith("[{\"tool\":\"goto\""), fixed);
        assertTrue(fixed.contains("{\"tool\":\"equip_item\",\"args\":{\"item_id\":\"stone_pickaxe\"}}"), fixed);
        assertTrue(fixed.endsWith("{\"tool\":\"craft\",\"args\":{\"item\":\"iron_pickaxe\"}}]"), fixed);
        String atZero = SkillRepair.apply(SEQ, 0, "{\"steps\":[{\"tool\":\"goto\",\"args\":{\"x\":5}}]}", TOOLS).orElseThrow();
        assertTrue(atZero.contains("\"bindings\":{\"x\":1}"), "the step's bindings stay: " + atZero);
    }

    @Test void anUnusableRepairIsRefused() {
        assertTrue(SkillRepair.apply(SEQ, 1, "{\"steps\":[{\"tool\":\"rm_rf\",\"args\":{}}]}", TOOLS).isEmpty(), "unknown tool");
        assertTrue(SkillRepair.apply(SEQ, 1, "no json", TOOLS).isEmpty());
        assertTrue(SkillRepair.apply(SEQ, 1, "{\"steps\":[]}", TOOLS).isEmpty());
        assertTrue(SkillRepair.apply(SEQ, 7, "{\"steps\":[{\"tool\":\"goto\"}]}", TOOLS).isEmpty(), "no such step");
        assertTrue(SkillRepair.apply(SEQ, 1, "{\"steps\":[{\"tool\":\"auto_mine\",\"args\":{\"block\":\"iron_ore\",\"count\":3}}]}", TOOLS).isEmpty(),
                "a repair that changes nothing");
        assertTrue(SkillRepair.apply(SEQ, 1, "{\"steps\":[{\"tool\":\"goto\"},{\"tool\":\"goto\"},{\"tool\":\"goto\"},{\"tool\":\"goto\"}]}", TOOLS).isEmpty(),
                "too many steps for one");
    }

    @Test void versionsAreNamedAfterTheirBase() {
        assertEquals("mine_iron~r2", SkillRepair.nextVersion("mine_iron", List.of("mine_iron")));
        assertEquals("mine_iron~r3", SkillRepair.nextVersion("mine_iron", List.of("mine_iron", "mine_iron~r2")));
        assertEquals("mine_iron", SkillRepair.baseOf("mine_iron~r3"));
        assertEquals("odd~rx", SkillRepair.baseOf("odd~rx"));
        assertTrue(SkillRepair.isVersion("mine_iron~r2"));
        assertFalse(SkillRepair.isVersion("mine_iron"));
    }

    @Test void theBetterVersionStaysAndTheWorseIsRolledBack() {
        Scorecard card = new Scorecard();
        for (int i = 0; i < 12; i++) run(card, "mine_iron", i % 2 == 0, 1, i);      // half
        for (int i = 0; i < 12; i++) run(card, "mine_iron~r2", true, 3, 100 + i);    // all
        assertEquals(SkillRepair.Verdict.PROMOTE, SkillRepair.judge(card, "mine_iron~r2", List.of("mine_iron", "mine_iron~r2")));
        Scorecard worse = new Scorecard();
        for (int i = 0; i < 12; i++) run(worse, "mine_iron", i % 4 != 0, 1, i);      // three quarters
        for (int i = 0; i < 12; i++) run(worse, "mine_iron~r2", i % 4 == 0, 1, 100 + i); // a quarter
        assertEquals(SkillRepair.Verdict.ROLL_BACK, SkillRepair.judge(worse, "mine_iron~r2", List.of("mine_iron", "mine_iron~r2")));
        Scorecard young = new Scorecard();
        for (int i = 0; i < 3; i++) run(young, "mine_iron~r2", true, 3, i);
        assertEquals(SkillRepair.Verdict.TRYING, SkillRepair.judge(young, "mine_iron~r2", List.of("mine_iron")));
        Scorecard broken = new Scorecard();
        for (int i = 0; i < 5; i++) run(broken, "mine_iron~r2", false, 0, i);
        assertEquals(SkillRepair.Verdict.ROLL_BACK, SkillRepair.judge(broken, "mine_iron~r2", List.of("mine_iron")), "plainly broken");
    }

    @Test void oneRepairOnTrialAtATimeAndAHealthyCertifiedSkillIsLeftAlone() {
        Scorecard card = new Scorecard();
        for (int i = 0; i < 3; i++) run(card, "mine_iron~r2", true, 3, i);
        assertFalse(SkillRepair.mayRepair(card, "mine_iron", List.of("mine_iron", "mine_iron~r2")), "a version is on trial");
        assertTrue(SkillRepair.mayRepair(new Scorecard(), "mine_iron", List.of("mine_iron")));
        assertFalse(SkillRepair.mayRepair(new Scorecard(), "mine_iron", List.of("a", "b", "c", "d")), "lineage full");
    }
}
