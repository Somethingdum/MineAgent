package com.mineagent.engine.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** SMH plan 70, MA10.2: phrasing it learns, three readings the same, and a correction. */
final class PhraseBookTest {
    @Test void threeReadingsTheSameMakeAFixedPhrase() {
        var b = new PhraseBook();
        assertFalse(b.rephrased("do the thing", "fill this chest with stone bricks"));
        assertFalse(b.rephrased("Do the  thing", "fill this chest with stone bricks"));
        assertTrue(b.meaning("do the thing").isEmpty(), "two are not enough");
        assertTrue(b.rephrased("do the thing", "fill this chest with stone bricks"), "the third fixes it");
        assertEquals("fill this chest with stone bricks", b.meaning("DO THE THING").orElseThrow());
    }

    @Test void aDifferentReadingStartsAgain() {
        var b = new PhraseBook();
        b.rephrased("sort it", "light up the area");
        b.rephrased("sort it", "light up the area");
        assertFalse(b.rephrased("sort it", "build a hut"), "a different reading: back to one");
        assertFalse(b.rephrased("sort it", "build a hut"));
        assertTrue(b.rephrased("sort it", "build a hut"));
        assertEquals("build a hut", b.meaning("sort it").orElseThrow());
    }

    @Test void taughtAtOnceAndACorrectionRemovesIt() {
        var b = new PhraseBook();
        b.teach("the usual", "make me 64 torches");
        assertEquals("make me 64 torches", b.meaning("the usual").orElseThrow());
        assertTrue(b.correct("the usual"));
        assertTrue(b.meaning("the usual").isEmpty());
        assertFalse(b.correct("the usual"), "nothing left to correct");
    }

    @Test void keptAcrossARestartWithTheReadingsOnTheWay() {
        var b = new PhraseBook();
        b.teach("the usual", "make me 64 torches");
        b.rephrased("sort it", "light up the area");
        b.rephrased("sort it", "light up the area");
        var json = new com.google.gson.Gson().toJson(b.exportState());
        var again = new PhraseBook();
        again.importState(new com.google.gson.Gson().fromJson(json, PhraseBook.State.class));
        assertEquals("make me 64 torches", again.meaning("the usual").orElseThrow());
        assertTrue(again.meaning("sort it").isEmpty(), "two readings: not fixed yet");
        assertTrue(again.rephrased("sort it", "light up the area"), "the third, after the restart, fixes it");
        again.importState(new PhraseBook.State(java.util.Map.of("same", "same", "", "x"), null, null));
        assertTrue(again.fixedPhrases().isEmpty(), "malformed phrases are not taken back");
    }
}
