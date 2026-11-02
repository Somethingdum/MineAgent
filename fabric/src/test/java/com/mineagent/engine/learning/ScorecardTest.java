package com.mineagent.engine.learning;

import com.mineagent.engine.skill.SkillLibrary;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** SMH plan 69: every episode rated by code from the game alone, and the better way used first. */
final class ScorecardTest {
    private static Scorecard.Episode done(String key, long ticks, double damage) {
        return new Scorecard.Episode(key, Scorecard.Outcome.DONE, false, ticks, damage, 20);
    }

    @Test void theScoreComesFromGameFactsOnly() {
        Scorecard card = new Scorecard();
        assertEquals(100.0, card.record(done("skill:a", 400, 0), 1), 1e-9);
        // Twice as slow as its own best and half the health lost: 60 + 12.5 + 7.5.
        assertEquals(80.0, card.record(done("skill:a", 800, 10), 2), 1e-9);
        assertEquals(0.0, card.score(new Scorecard.Episode("skill:b", Scorecard.Outcome.DONE, true, 100, 20, 20)), 1e-9);
        assertEquals(15.0, card.score(new Scorecard.Episode("x", Scorecard.Outcome.FAILED, false, 100, 0, 20)), 1e-9);
        assertEquals(35.0, card.score(new Scorecard.Episode("x", Scorecard.Outcome.RETREATED, false, 100, 0, 20)), 1e-9);
        assertEquals(400L, card.stat("skill:a").fastestTicks());
        assertEquals("score 90/100 over 2 runs (best 100, last 80)", card.describe("skill:a"));
    }

    @Test void theUntriedIsTriedAndTheBetterComesBeforeTheWorse() {
        Scorecard card = new Scorecard();
        for (int i = 0; i < 5; i++) {
            card.record(done("skill:good", 100, 0), i);
            card.record(new Scorecard.Episode("skill:poor", Scorecard.Outcome.FAILED, false, 100, 15, 20), i);
        }
        List<String> order = card.rank(List.of("skill:poor", "skill:good", "skill:new"));
        assertEquals(List.of("skill:new", "skill:good", "skill:poor"), order);
    }

    @Test void ratingsSurviveTheirSaveFile() {
        Scorecard card = new Scorecard();
        card.record(done("Skill:A ", 100, 0), 1);
        Scorecard again = new Scorecard();
        again.importState(card.exportState());
        assertEquals(1, again.stat("skill:a").runs());
    }

    @Test void theTrackerCountsEveryHealthDropAndNoHealing() {
        EpisodeTracker t = new EpisodeTracker();
        t.begin("goal:x", 100, 20, 20);
        t.observe(15, false);
        t.observe(18, false);
        t.observe(10, false);
        Scorecard.Episode e = t.end(Scorecard.Outcome.DONE, 300);
        assertEquals(13.0, e.damageTaken(), 1e-9);
        assertEquals(200L, e.ticks());
        assertFalse(t.active());
    }

    @Test void theSkillWithTheBetterRecordIsOfferedFirst() {
        Scorecard card = new Scorecard();
        SkillLibrary lib = new SkillLibrary();
        lib.setScorecard(card);
        String steps = "[{\"tool\":\"auto_mine\",\"args\":{}}]";
        lib.register("mine_stone_slow", "mine stone", "stone", steps, true);
        lib.register("mine_stone_fast", "mine stone", "stone", steps, true);
        for (int i = 0; i < 6; i++) {
            card.record(done("skill:mine_stone_fast", 100, 0), i);
            card.record(new Scorecard.Episode("skill:mine_stone_slow", Scorecard.Outcome.FAILED, false, 100, 15, 20), i);
        }
        assertEquals("mine_stone_fast", lib.relevant("mine stone", 2).get(0).name());
    }
}
