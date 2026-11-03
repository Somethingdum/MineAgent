package com.mineagent.engine.loop;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** "Going AFK, <what you want>" (SMH plan 70, MA5.4): the owner's own examples, and a few more. */
final class AfkOrdersTest {
    @Test void fillAChestWhileImOutIsTheJob() {
        var o = AfkOrders.parse("fill up a chest with food while I'm out.");
        assertEquals("fill up a chest with food", o.task());
        assertFalse(o.guardMe());
        assertFalse(o.shelter());
        assertEquals(0, o.stayWithin());
        assertEquals("Got it. While you're away, I'll fill up a chest with food, then find something else to do.",
                AfkOrders.reply(o, false));
    }

    @Test void makeSureIDontDieIsKeepMeSafe() {
        for (String w : new String[] {"make sure I dont die", "make sure i don't die", "keep me safe", "watch my back",
                "don't let me die"}) {
            var o = AfkOrders.parse(w);
            assertTrue(o.guardMe(), w);
            assertNull(o.task(), w);
        }
        assertEquals("Got it. While you're away, I'll keep you safe.", AfkOrders.reply(AfkOrders.parse("make sure I dont die"), false));
    }

    @Test void blockYourselfInDontDieIsAShelter() {
        var o = AfkOrders.parse("block yourself in, don't die");
        assertTrue(o.shelter());
        assertTrue(o.careful());
        assertFalse(o.guardMe(), "its own life, not theirs");
        assertNull(o.task());
        assertEquals("Got it. While you're away, I'll block myself in and wait for you.", AfkOrders.reply(o, false));
        assertTrue(AfkOrders.parse("wall yourself in and don't die").shelter());
        assertTrue(AfkOrders.parse("hole up").shelter());
    }

    @Test void aJobAndWhatItKnowsTogether() {
        var o = AfkOrders.parse("mine some iron and don't die, stay within 40 blocks of me");
        assertEquals("mine some iron", o.task());
        assertTrue(o.careful());
        assertEquals(40, o.stayWithin());
        var both = AfkOrders.parse("smelt my iron, then block yourself in");
        assertEquals("smelt my iron", both.task());
        assertTrue(both.shelter());
        assertEquals("Got it. While you're away, I'll smelt your iron, then block myself in and wait for you.",
                AfkOrders.reply(both, false));
        var bread = AfkOrders.parse("bake bread and steak");
        assertEquals("bake bread and steak", bread.task(), "an 'and' inside the job stays in it");
    }

    @Test void justAwayIsNoOrder() {
        for (String w : new String[] {"", "for a bit", "for 10 minutes", "while i'm out", null}) {
            assertFalse(AfkOrders.parse(w).any(), String.valueOf(w));
        }
        assertEquals("Got it. I'll keep busy while you're away.", AfkOrders.reply(AfkOrders.Order.NONE, false));
        assertEquals("Got it. I'll keep busy and keep an eye on you.", AfkOrders.reply(AfkOrders.Order.NONE, true));
        assertEquals(16, AfkOrders.parse("stay close").stayWithin());
    }

    @Test void itsWordsAreFromItsSide() {
        assertEquals("fill your chest with my spare wood", AfkOrders.echo("fill my chest with your spare wood"));
        assertEquals("bring you food, then wait", AfkOrders.echo("bring me food, then wait"));
    }
}
