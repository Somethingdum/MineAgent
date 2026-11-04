package com.mineagent.engine.chat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** SMH plan 70, MA10.2: "that", "it", "there" mean one of the candidates code made, and only those. */
final class ReferentsTest {
    private static final List<Referents.Candidate> LIST = List.of(
            new Referents.Candidate("the chest they are looking at", "minecraft:overworld", 10, 64, -3),
            new Referents.Candidate("where they stand", "minecraft:overworld", 4, 64, 2));

    @Test void wordsThatPoint() {
        assertTrue(Referents.deictic("put the cobblestone in that"));
        assertTrue(Referents.deictic("go there"));
        assertTrue(Referents.deictic("Bring it here"));
        assertFalse(Referents.deictic("mine some iron"));
        assertFalse(Referents.deictic("thatch the roof"), "a word with 'that' in it is not 'that'");
    }

    @Test void theBriefNumbersThemWithTheirPlaces() {
        String b = Referents.brief(LIST);
        assertTrue(b.startsWith("[REFERENTS]"));
        assertTrue(b.contains("[1] the chest they are looking at at 10, 64, -3"), b);
        assertTrue(b.contains("[2] where they stand at 4, 64, 2"), b);
        assertEquals("", Referents.brief(List.of()));
    }

    @Test void onlyAPickFromTheList() {
        assertEquals("the chest they are looking at", Referents.match(LIST, 10, 65, -3, 3).orElseThrow().label());
        assertTrue(Referents.match(LIST, 30, 64, 30, 3).isEmpty(), "somewhere else is no pick");
        assertTrue(Referents.refusal(LIST).contains("10, 64, -3"));
    }
}
