package com.mineagent.engine.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** SMH plan 69, MA3.3: one line per event, and a burst still says one a minute per kind. */
final class MilestoneTalkTest {
    @Test void eachKindSaysOneLineAMinute() {
        MilestoneTalk talk = new MilestoneTalk();
        assertTrue(talk.take(MilestoneTalk.Kind.MILESTONE, 0));
        assertTrue(talk.take(MilestoneTalk.Kind.SKILL, 1), "another kind is not held back");
        for (long t = 2; t < 60_000; t += 5_000) assertFalse(talk.take(MilestoneTalk.Kind.MILESTONE, t), "a burst at " + t);
        assertTrue(talk.take(MilestoneTalk.Kind.MILESTONE, 60_000));
        assertTrue(talk.take(MilestoneTalk.Kind.RECOVERED, 60_001));
    }

    @Test void theReplyIsOneSentence() {
        assertEquals("Got the iron, boss!", MilestoneTalk.sentence("<think>hm</think>\"Got the iron, boss! Next I'll smelt it.\""));
        assertEquals("Phew, that was close", MilestoneTalk.sentence("Phew, that was close"));
        assertNull(MilestoneTalk.sentence("   "));
        assertNull(MilestoneTalk.sentence(null));
        assertEquals("Found 3.5 blocks of fun.", MilestoneTalk.sentence("Found 3.5 blocks of fun. Really."));
    }
}
