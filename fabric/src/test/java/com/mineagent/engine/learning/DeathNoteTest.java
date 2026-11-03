package com.mineagent.engine.learning;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** SMH plan 69, MA3.9: a death leaves a note, and a way back to its things unless nothing is left. */
final class DeathNoteTest {
    @Test void aLavaOrVoidDeathAddsNoRecoveryGoal() {
        assertFalse(DeathNote.recoverable("minecraft:lava", false, false, true));
        assertFalse(DeathNote.recoverable("fell_out_of_world", false, false, true));
        assertTrue(DeathNote.recoverable("minecraft:mob_attack", false, false, true));
        assertTrue(DeathNote.recoverable("minecraft:fall", false, false, true));
    }

    @Test void nothingToFetchWithKeepInventoryInHardcoreOrEmptyHanded() {
        assertFalse(DeathNote.recoverable("mob_attack", true, false, true));
        assertFalse(DeathNote.recoverable("mob_attack", false, true, true));
        assertFalse(DeathNote.recoverable("mob_attack", false, false, false));
    }

    @Test void theGoalAndTheNoteSayWhereAndWhy() {
        String goal = DeathNote.recoveryGoal(10, 64, -5, "minecraft:the_nether");
        assertTrue(goal.startsWith(DeathNote.RECOVER_PREFIX + "10 64 -5 in the nether:"), goal);
        assertFalse(DeathNote.recoveryGoal(1, 2, 3, "minecraft:overworld").contains(" in the "));
        assertEquals("Died at 1 2 3: Steve was slain by Zombie", DeathNote.note("Steve was slain by Zombie", 1, 2, 3));
    }
}
