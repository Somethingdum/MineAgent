package com.mineagent.engine.act;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TossAimTest {
    /** Standing a block above a lava pool's floor: the hand is 2.32 over it, the lava's top 0.89. */
    static final double DROP = 1.0 + 1.62 - TossAim.BELOW_EYES - TossAim.FLUID_TOP;

    @Test void aLevelTossComesDownAboutThreeBlocksOff() {
        assertEquals(2.95, TossAim.reach(0, DROP), 0.1);
    }

    @Test void aimingAtThePoolShortsIt() {
        // The old aim: looking at the lava from three blocks back, 30 degrees down, it lands under two blocks off.
        double pitch = Math.toDegrees(Math.atan2(2.62 - 0.9, 3.0));
        assertTrue(TossAim.reach(pitch, DROP) < 2.0, "short of a pool two to three and a half blocks off");
    }

    @Test void thePitchThatLandsItIsTheFlattest() {
        for (double d : new double[] {2.0, 2.5, 3.0, 3.5}) {
            double p = TossAim.pitchFor(d, DROP);
            assertFalse(Double.isNaN(p), "reaches " + d);
            assertEquals(d, TossAim.reach(p, DROP), 0.05, "lands " + d + " off");
            assertTrue(p > -30 && p < 30, "a flat toss, not a lob: " + p);
        }
        assertTrue(Double.isNaN(TossAim.pitchFor(6.0, DROP)), "too far to toss");
    }
}
