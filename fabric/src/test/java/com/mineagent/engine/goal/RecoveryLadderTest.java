package com.mineagent.engine.goal;

import com.mineagent.engine.goal.RecoveryLadder.Failure;
import com.mineagent.engine.goal.RecoveryLadder.Rung;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The recovery ladder (SMH plan 70, MA6.4): the rungs in order, none skipped that could help. */
final class RecoveryLadderTest {
    @Test void failuresReadFromWhatTheTaskSaid() {
        assertEquals(Failure.NO_PATH, RecoveryLadder.classify("I can't get to 12, 64, -3"));
        assertEquals(Failure.STALLED, RecoveryLadder.classify("the furnaces stopped before the stone was done"));
        assertEquals(Failure.NO_TOOL, RecoveryLadder.classify("I have no shears and can't make any"));
        assertEquals(Failure.NO_CHEST, RecoveryLadder.classify("there is no chest at 1, 2, 3"));
        assertEquals(Failure.NO_SOURCE, RecoveryLadder.classify("I can't see a way to make 16 torches: torches needs coal or charcoal"));
        assertEquals(Failure.NO_ROOM, RecoveryLadder.classify("that chest is already full"));
        assertEquals(Failure.UNKNOWN, RecoveryLadder.classify("something went wrong: x"));
    }

    @Test void eachFailureClimbsInOrder() {
        // A blocked path: once more, then you (here), then the model, then it gives up.
        assertEquals(Rung.RETRY, RecoveryLadder.next(Failure.NO_PATH, 0, true, true));
        assertEquals(Rung.ASK, RecoveryLadder.next(Failure.NO_PATH, RecoveryLadder.after(Rung.RETRY), true, true));
        assertEquals(Rung.PATCH, RecoveryLadder.next(Failure.NO_PATH, RecoveryLadder.after(Rung.ASK), true, true));
        assertEquals(Rung.GIVE_UP, RecoveryLadder.next(Failure.NO_PATH, RecoveryLadder.after(Rung.PATCH), true, true));
        // A missing tool is fixed before anyone is asked.
        assertEquals(Rung.FIX, RecoveryLadder.next(Failure.NO_TOOL, 0, true, true));
        // Not enough to be had: what it can, first.
        assertEquals(Rung.VARIATION, RecoveryLadder.next(Failure.NO_SOURCE, 0, false, false));
        // You away and no model: straight to giving up cleanly, never a question you can't answer.
        assertEquals(Rung.GIVE_UP, RecoveryLadder.next(Failure.NO_CHEST, 0, false, false));
        assertEquals(Rung.ASK, RecoveryLadder.next(Failure.NO_CHEST, 0, true, true), "which chest: yours to say");
        assertEquals(Rung.GIVE_UP, RecoveryLadder.next(Failure.DANGER, RecoveryLadder.after(Rung.ASK), true, true),
                "danger is never handed to the model to try again");
        // Past the end: give up.
        assertEquals(Rung.GIVE_UP, RecoveryLadder.next(Failure.UNKNOWN, 99, true, true));
    }

    @Test void aToolItCouldntMakeIsNoToolToMakeFirst() {
        // The thing it was making is the order, not why it failed: no pickaxe to make first, round and round.
        assertEquals(Failure.NO_SOURCE, RecoveryLadder.classify("I couldn't make any stone pickaxe: no more oak logs near here."));
        assertEquals(Failure.NO_SOURCE, RecoveryLadder.classify("I couldn't make any stone pickaxe: no more oak logs near here.",
                "make me 1 stone pickaxe"));
        assertNotEquals(Rung.FIX, RecoveryLadder.next(RecoveryLadder.classify("I couldn't make any stone pickaxe: no more oak logs near here."),
                0, false, false));
        // A pickaxe order that failed for want of a pickaxe: that is the order itself.
        assertEquals(Failure.NO_SOURCE, RecoveryLadder.classify("I have no pickaxe to mine the stone", "make me 1 stone pickaxe"));
        // Shears still made first for wool, and a pickaxe for ore.
        assertEquals(Failure.NO_TOOL, RecoveryLadder.classify("I have no shears and can't make any", "make me 16 white wool"));
        assertEquals(Failure.NO_TOOL, RecoveryLadder.classify("I couldn't make any iron ingot: I need a pickaxe for the ore", "make me 3 iron ingots"));
        // The reason, whatever it was, read on its own.
        assertEquals(Failure.STALLED, RecoveryLadder.classify("I couldn't make any charcoal: the furnaces stopped before it was done"));
        assertEquals(Failure.NO_SOURCE, RecoveryLadder.classify("I couldn't make any bread: something odd"));
    }
}
