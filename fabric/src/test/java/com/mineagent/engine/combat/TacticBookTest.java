package com.mineagent.engine.combat;

import com.google.gson.Gson;
import com.mineagent.engine.learning.Scorecard;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Combat learning: the model's fighting ways kept, tried, rated and dropped when worse. */
final class TacticBookTest {
    private static final double FLEE = 6;

    private static void fights(Scorecard card, String mob, String name, int won, int lost) {
        for (int i = 0; i < won; i++) card.record(new Scorecard.Episode(TacticBook.key(mob, name), Scorecard.Outcome.DONE, false, 200, 2, 20), i);
        for (int i = 0; i < lost; i++) card.record(new Scorecard.Episode(TacticBook.key(mob, name), Scorecard.Outcome.FAILED, true, 200, 20, 20), i);
    }

    @Test void namesSayWhatDiffers() {
        assertEquals("balanced", TacticBook.nameOf(Tactics.of("balanced", FLEE), FLEE));
        assertEquals("balanced-r10-noshield", TacticBook.nameOf(new Tactics("balanced", List.of(), 10, false, true), FLEE));
        assertEquals("kite-first-skeleton", TacticBook.nameOf(new Tactics("kite", List.of("skeleton"), FLEE, true, false), FLEE));
    }

    @Test void advisedTacticsBecomeAVariantThatIsOfferedAndTried() {
        TacticBook book = new TacticBook();
        Scorecard card = new Scorecard();
        fights(card, "zombie", "balanced", 8, 2);
        fights(card, "zombie", "aggressive", 5, 5);
        fights(card, "zombie", "defensive", 6, 4);
        fights(card, "zombie", "kite", 4, 6);
        String name = book.adopt("zombie", new Tactics("aggressive", List.of(), 10, true, true), FLEE, card, 0);
        assertEquals("aggressive-r10", name);
        assertTrue(book.names("zombie").contains(name));
        assertEquals(name, book.best("zombie", card, FLEE).name(), "never tried: it gets its trial first");
        assertEquals(10, book.best("zombie", card, FLEE).tactics().retreatHealth());
        assertEquals("balanced", book.adopt("zombie", Tactics.of("balanced", FLEE), FLEE, card, 1), "a stance's own settings are that stance");
    }

    @Test void aVariantThatDoesWorseIsDroppedAndOneThatDoesBetterLeads() {
        TacticBook book = new TacticBook();
        Scorecard card = new Scorecard();
        fights(card, "creeper", "balanced", 9, 1);
        String bad = book.adopt("creeper", new Tactics("aggressive", List.of(), 4, false, true), FLEE, card, 0);
        fights(card, "creeper", bad, 2, 8);
        assertEquals(List.of(bad), book.prune("creeper", card));
        assertFalse(book.names("creeper").contains(bad));
        String good = book.adopt("creeper", new Tactics("kite", List.of(), 10, true, false), FLEE, card, 1);
        fights(card, "creeper", good, 30, 0);
        fights(card, "creeper", "aggressive", 3, 3);
        fights(card, "creeper", "defensive", 3, 3);
        fights(card, "creeper", "kite", 3, 3);
        assertTrue(book.prune("creeper", card).isEmpty());
        assertEquals(good, book.best("creeper", card, FLEE).name());
    }

    @Test void aFullListMakesRoomOnlyFromAVariantThatHasHadFights() {
        TacticBook book = new TacticBook();
        Scorecard card = new Scorecard();
        for (int r = 7; r < 7 + TacticBook.MAX_PER_MOB; r++) book.adopt("spider", new Tactics("balanced", List.of(), r, true, true), FLEE, card, r);
        assertEquals("aggressive", book.adopt("spider", new Tactics("aggressive", List.of(), 15, true, true), FLEE, card, 20),
                "nothing has been tried yet: used under its stance");
        fights(card, "spider", "balanced-r7", 1, 5);
        assertEquals("aggressive-r15", book.adopt("spider", new Tactics("aggressive", List.of(), 15, true, true), FLEE, card, 21));
        assertFalse(book.names("spider").contains("balanced-r7"), "the worst-rated made room");
    }

    @Test void theBookSurvivesASave() {
        TacticBook book = new TacticBook();
        book.adopt("witch", new Tactics("kite", List.of("witch"), 12, true, false), FLEE, new Scorecard(), 5);
        Gson gson = new Gson();
        TacticBook loaded = new TacticBook();
        loaded.importState(gson.fromJson(gson.toJson(book.exportState()), TacticBook.State.class));
        assertEquals(book.names("witch"), loaded.names("witch"));
        assertEquals(12, loaded.tactics("witch", "kite-r12-first-witch", FLEE).retreatHealth());
    }
}
