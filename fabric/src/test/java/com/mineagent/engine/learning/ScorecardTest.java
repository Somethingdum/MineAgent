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
        assertEquals("new, 2/2 done (reliability 0.34), score 90/100", card.describe("skill:a"));
    }

    @Test void theWilsonBoundMatchesAndyOmegasReferenceValues() {
        assertEquals(0.490, Scorecard.wilson(8, 10), 0.0005);
        assertEquals(0.764, Scorecard.wilson(19, 20), 0.0005);
        assertEquals(0.798, Scorecard.wilson(23, 24), 0.0005);
        assertEquals(0.787, Scorecard.wilson(28, 30), 0.0005);
        assertEquals(0.886, Scorecard.wilson(58, 60), 0.0005);
    }

    private static final String[] BIOMES = {"plains", "forest", "desert", "taiga", "swamp"};

    @Test void tiersNeedTrialsReliabilityAndDistinctSituations() {
        Scorecard card = new Scorecard();
        for (int i = 0; i < 10; i++) {
            card.record(new Scorecard.Episode("skill:mine", i == 3 ? Scorecard.Outcome.FAILED : Scorecard.Outcome.DONE,
                    false, 100, 0, 20, "biome=" + BIOMES[i % 3] + ",tool=iron"), i);
        }
        assertEquals(Scorecard.Tier.CANDIDATE, card.tier("skill:mine"));
        Scorecard oneSituation = new Scorecard();
        for (int i = 0; i < 10; i++) oneSituation.record(new Scorecard.Episode("skill:one", Scorecard.Outcome.DONE, false, 100, 0, 20, "biome=plains"), i);
        assertEquals(Scorecard.Tier.NEW, oneSituation.tier("skill:one"), "one memorised situation is not a skill");
    }

    @Test void aCertifiedSkillThatStartsFailingIsDemotedAndTheConditionNamed() {
        Scorecard card = new Scorecard();
        for (int i = 0; i < 30; i++) {
            card.record(new Scorecard.Episode("skill:iron", Scorecard.Outcome.DONE, false, 100, 0, 20,
                    "biome=" + BIOMES[i % 5] + ",time=" + (i % 2 == 0 ? "day" : "night")), i);
        }
        assertEquals(Scorecard.Tier.CERTIFIED, card.tier("skill:iron"));
        for (int i = 0; i < 20; i++) {
            boolean ok = i % 4 == 0;
            card.record(new Scorecard.Episode("skill:iron", ok ? Scorecard.Outcome.DONE : Scorecard.Outcome.FAILED,
                    false, 100, 0, 20, ok ? "biome=plains" : "biome=swamp"), 100 + i);
        }
        assertTrue(card.stat("skill:iron").demoted());
        assertEquals("biome=swamp", card.failureCondition("skill:iron"));
        assertTrue(card.describe("skill:iron").contains("failures share biome=swamp"));
    }

    @Test void learningProgressSaysWhereItIsImprovingAndWhereItIsStuck() {
        Scorecard card = new Scorecard();
        for (int i = 0; i < 20; i++) {
            boolean ok = i < 10 ? i % 3 == 0 : i % 5 != 0;
            card.record(new Scorecard.Episode("skill:learn", ok ? Scorecard.Outcome.DONE : Scorecard.Outcome.FAILED, false, 100, 0, 20), i);
            card.record(new Scorecard.Episode("skill:stuck", i % 6 == 0 ? Scorecard.Outcome.DONE : Scorecard.Outcome.FAILED, false, 100, 0, 20), i);
            card.record(new Scorecard.Episode("skill:easy", Scorecard.Outcome.DONE, false, 100, 0, 20), i);
        }
        assertEquals(Scorecard.Trend.IMPROVING, card.trend("skill:learn"));
        assertEquals(Scorecard.Trend.STUCK, card.trend("skill:stuck"));
        assertEquals(Scorecard.Trend.MASTERED, card.trend("skill:easy"));
        assertEquals(Scorecard.Trend.UNTESTED, card.trend("skill:never"));
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

    @Test void theFightRecordNamesTheBestStancePerMob() {
        Scorecard card = new Scorecard();
        for (int i = 0; i < 6; i++) {
            card.record(new Scorecard.Episode("fight:zombie:balanced", Scorecard.Outcome.DONE, false, 200, 2, 20), i);
            card.record(new Scorecard.Episode("fight:zombie:aggressive", i < 2 ? Scorecard.Outcome.DONE : Scorecard.Outcome.FAILED,
                    i >= 2, 200, 12, 20), i);
        }
        card.record(new Scorecard.Episode("fight:skeleton:kite", Scorecard.Outcome.DONE, false, 300, 4, 20), 9);
        card.record(new Scorecard.Episode("skill:mine_iron", Scorecard.Outcome.DONE, false, 300, 0, 20), 9);
        var lines = card.fightRecord(5);
        assertEquals(2, lines.size(), lines.toString());
        assertTrue(lines.get(0).startsWith("zombie: balanced works best, won 6 of 6"), lines.get(0));
        assertTrue(lines.get(0).endsWith("12 fights in all"), lines.get(0));
        assertTrue(lines.get(1).startsWith("skeleton: kite works best"), lines.get(1));
        assertEquals(1, card.fightRecord(1).size());
    }
}
