package com.mineagent.engine.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AskingTest {
    @Test void askedOnceAnsweredAndKept() {
        var a = new Asking();
        assertTrue(a.ask("golems", "May I?", true, 0, 1200));
        assertFalse(a.ask("golems", "May I?", true, 10, 1200), "not asked twice while it waits");
        assertFalse(a.ask("other", "And this?", true, 10, 1200), "one question at a time");
        assertTrue(a.hear("follow me").isEmpty(), "an order is not an answer");
        var s = a.hear("Nope.").orElseThrow();
        assertFalse(s.yes());
        assertFalse(s.assumed());
        assertEquals(false, a.answer("golems", 999_999).orElseThrow(), "what it was told is kept");
        assertFalse(a.ask("golems", "May I?", true, 1_000_000, 1200));
    }

    @Test void unansweredItTakesTheConvenientAnswerForADay() {
        var a = new Asking();
        a.ask("golems", "May I?", true, 100, 1200);
        assertTrue(a.tick(500).isEmpty());
        var s = a.tick(1300).orElseThrow();
        assertTrue(s.yes() && s.assumed());
        assertEquals(true, a.answer("golems", 1300 + Asking.ASSUMED_FOR - 1).orElseThrow());
        assertTrue(a.answer("golems", 1300 + Asking.ASSUMED_FOR).isEmpty(), "a day on, it asks again");
        assertTrue(a.exportTold().isEmpty(), "an assumption is not kept as told");
    }

    @Test void yesAndNoInTheWaysPeopleSayThem() {
        for (String y : new String[] {"yes", "Yeah", "sure!", "go ahead", "ok"}) assertEquals(Boolean.TRUE, Asking.yesNo(y), y);
        for (String n : new String[] {"no", "Nah", "don't", "no thanks"}) assertEquals(Boolean.FALSE, Asking.yesNo(n), n);
        assertNull(Asking.yesNo("make me 3 iron"));
    }
}
