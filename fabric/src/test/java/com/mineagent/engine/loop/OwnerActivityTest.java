package com.mineagent.engine.loop;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** When the owner is AFK (SMH plan 70, MA5.4): the game's idle timer, or their word, until their next input. */
final class OwnerActivityTest {
    private static final long MIN = 60_000L;

    @Test void threeMinutesWithoutInputIsAfk() {
        assertFalse(OwnerActivity.idle(10 * MIN, 8 * MIN, 0, OwnerActivity.AFK_AFTER_MS));
        assertTrue(OwnerActivity.idle(11 * MIN, 8 * MIN, 0, OwnerActivity.AFK_AFTER_MS));
    }

    @Test void sayingAfkIsAfkUntilTheNextInput() {
        long said = 5 * MIN;
        // their "afk" was itself their last input
        assertTrue(OwnerActivity.idle(said + 1000, said + 200, said, OwnerActivity.AFK_AFTER_MS));
        // a move after it means they're back
        assertFalse(OwnerActivity.idle(said + 10_000, said + 9_000, said, OwnerActivity.AFK_AFTER_MS));
    }

    @Test void goingAndComingBackSayOnce() {
        OwnerActivity a = new OwnerActivity();
        assertEquals(OwnerActivity.Change.NONE, a.update(MIN, MIN));
        a.saidAfk(2 * MIN);
        assertEquals(OwnerActivity.Change.WENT_AFK, a.update(2 * MIN + 100, 2 * MIN));
        assertTrue(a.afk());
        assertEquals(OwnerActivity.Change.NONE, a.update(3 * MIN, 2 * MIN));
        a.wonFight();
        a.wonFight();
        assertEquals(OwnerActivity.Change.CAME_BACK, a.update(9 * MIN, 9 * MIN));
        assertFalse(a.afk());
        String line = a.welcomeBack(9 * MIN);
        assertTrue(line.contains("2 mobs"), line);
        assertTrue(line.startsWith("Welcome back."), line);
    }

    @Test void aQuietSpellSaysSo() {
        OwnerActivity a = new OwnerActivity();
        a.update(0, 1);
        assertEquals(OwnerActivity.Change.WENT_AFK, a.update(4 * MIN, 1));
        assertEquals(OwnerActivity.Change.CAME_BACK, a.update(10 * MIN, 10 * MIN));
        assertTrue(a.welcomeBack(10 * MIN).contains("All quiet"), a.welcomeBack(10 * MIN));
    }
}
