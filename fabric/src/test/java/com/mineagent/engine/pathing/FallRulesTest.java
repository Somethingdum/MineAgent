package com.mineagent.engine.pathing;

import com.mineagent.engine.pathing.util.FallRules;
import com.mineagent.engine.pathing.util.MobAvoidance;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** SMH plan 70, MA5.10: falls by the game's rules, and mobs in the path's cost. */
final class FallRulesTest {
    @Test void fallDamageIsTheDropLessThreeRoundedUp() {
        assertEquals(0, FallRules.damage(3, 3.0, 1.0));
        assertEquals(1, FallRules.damage(4, 3.0, 1.0));
        assertEquals(17, FallRules.damage(20, 3.0, 1.0));
        assertEquals(0, FallRules.damage(20, 3.0, 0.0));
        assertEquals(3, FallRules.safeDrop(3.0));
    }

    @Test void aBucketDescentIsOnlyFromAHeightItWouldSurviveIfTheWaterFailed() {
        // 20 health keeps 6 if the drop costs 14 or less: 17 blocks.
        assertEquals(17, FallRules.maxBucketDrop(20.0, 3.0, 1.0));
        assertEquals(7, FallRules.maxBucketDrop(10.0, 3.0, 1.0));
        assertEquals(0, FallRules.maxBucketDrop(6.0, 3.0, 1.0));
        // Never past the planner's own cap.
        assertEquals(FallRules.MAX_BUCKET_DROP, FallRules.maxBucketDrop(1000.0, 3.0, 1.0));
    }

    @Test void cellsNearAMobCostMoreAndCreepersMost() {
        var zombie = List.of(MobAvoidance.mob(0, 64, 0, false));
        var creeper = List.of(MobAvoidance.mob(0, 64, 0, true));
        assertEquals(MobAvoidance.WEIGHT, MobAvoidance.cost(zombie, 0, 64, 0), 1e-9);
        assertTrue(MobAvoidance.cost(zombie, 2, 64, 0) > MobAvoidance.cost(zombie, 4, 64, 0));
        assertEquals(0.0, MobAvoidance.cost(zombie, 6, 64, 0), 1e-9);
        assertTrue(MobAvoidance.cost(zombie, 5, 64, 0) > 0.0);
        assertTrue(MobAvoidance.cost(creeper, 7, 64, 0) > 0.0);
        assertTrue(MobAvoidance.cost(creeper, 1, 64, 0) > MobAvoidance.cost(zombie, 1, 64, 0));
        assertEquals(0.0, MobAvoidance.cost(List.of(), 0, 64, 0), 1e-9);
    }
}
