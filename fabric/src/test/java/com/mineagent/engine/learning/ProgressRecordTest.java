package com.mineagent.engine.learning;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** SMH plan 70, MA10.3: the progress record and the stall alarm. */
final class ProgressRecordTest {
    @Test void talliesByHourAndSession() {
        var p = new ProgressRecord();
        p.goalDone(100);
        p.goalFailed(200);
        p.gained("minecraft:stone_bricks", 64, 300);
        p.gained("minecraft:iron_ingot", 12, 1_500);
        p.died(1_600);
        p.closeCall(1_700);
        p.modelCall(1_800);
        p.modelCall(1_900);
        p.action("produce", 100);
        p.action("produce", 1_100);
        var s = p.session();
        assertEquals(1, s.goalsDone);
        assertEquals(1, s.goalsFailed);
        assertEquals(64, s.gained.get("minecraft:stone_bricks").intValue());
        assertEquals(1, s.deaths);
        assertEquals(2, s.modelCalls);
        assertEquals(2, s.actions.get("produce").intValue());
        var hour = p.hour(1_999);
        assertEquals(0, hour.goalsDone, "the goal was done in the hour before");
        assertEquals(12, hour.gained.get("minecraft:iron_ingot").intValue());
        String line = p.summary(1_999);
        assertTrue(line.contains("1 goal done, 1 failed"), line);
        assertTrue(line.contains("64 stone bricks"), line);
        assertTrue(line.contains("1 death, 1 close call; 2 model calls"), line);
    }

    @Test void aStallIsCalledOnceAndOnlyWithWorkInHand() {
        var p = new ProgressRecord();
        assertFalse(p.stalled(false, 0), "nothing to do: no stall");
        assertFalse(p.stalled(false, 10_000));
        assertFalse(p.stalled(true, 10_000));
        assertFalse(p.stalled(true, 10_000 + ProgressRecord.STALL - 1));
        assertTrue(p.stalled(true, 10_000 + ProgressRecord.STALL));
        assertFalse(p.stalled(true, 10_000 + ProgressRecord.STALL + 200), "said once");
        p.gained("minecraft:cobblestone", 1, 20_000);
        assertFalse(p.stalled(true, 20_000 + 100), "moving again");
        assertTrue(p.stalled(true, 20_000 + ProgressRecord.STALL), "and stalled again later");
    }
}
