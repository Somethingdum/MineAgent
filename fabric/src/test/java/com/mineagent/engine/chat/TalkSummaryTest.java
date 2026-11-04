package com.mineagent.engine.chat;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** SMH plan 70, MA10.2: the running summary of talks in a world. */
class TalkSummaryTest {
    @Test void anOrdersTopicLeavesOutTheNumbersAndTheWordsRoundThem() {
        assertEquals("iron ingots", TalkSummary.topic("make me 12 iron ingots"));
        assertEquals("stone bricks", TalkSummary.topic("make a chest full of stone bricks"));
        assertEquals("oak logs", TalkSummary.topic("get me 3 oak logs"));
        assertEquals("build hut", TalkSummary.topic("build a hut, then make me 2 white beds"));
    }

    @Test void whatTheyAskForMostLatelyAndTheirWords() {
        var t = new TalkSummary();
        assertEquals("", t.summary(Map.of()), "no talk yet: nothing in the brief");
        t.order("make me 12 iron ingots");
        t.order("make me 4 iron ingots");
        t.order("make me 8 food");
        t.order("build a hut");
        t.order("make me 3 iron ingots");
        String s = t.summary(Map.of("the usual", "make me 64 torches"));
        assertTrue(s.startsWith("Our talks in this world: you ask most for iron ingots (3)"), s);
        assertFalse(s.contains("food (1)"), "asked once is not 'most': " + s);
        assertTrue(s.contains("lately: make me 8 food; build a hut; make me 3 iron ingots"), s);
        assertTrue(s.contains("\"the usual\" means \"make me 64 torches\""), s);
    }

    @Test void keptWithTheWorldAndBounded() {
        var t = new TalkSummary();
        for (int i = 0; i < 100; i++) t.order("make me " + i + " thing" + (i % 60));
        var json = new com.google.gson.Gson().toJson(t.exportState());
        var again = new TalkSummary();
        again.importState(new com.google.gson.Gson().fromJson(json, TalkSummary.State.class));
        assertTrue(again.exportState().asked().size() <= TalkSummary.TOPICS);
        assertEquals(TalkSummary.LATELY, again.exportState().lately().size());
        assertEquals("make me 99 thing39", again.exportState().lately().get(TalkSummary.LATELY - 1));
    }
}
