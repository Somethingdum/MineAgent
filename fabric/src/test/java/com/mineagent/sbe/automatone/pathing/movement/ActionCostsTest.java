/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

// Ported from sailex428/automatone (LGPL-3.0, Ladysnake's Automatone, a fork of Baritone) at b7953df8:
// src/test/java/baritone/pathing/movement/ActionCostsTest.java. Translated for this mod (SMH plan 71, step 1.9): its package renamed, Minecraft's
// names in Mojang's mappings, JUnit 5 for JUnit 4 (the assertion message last).
package com.mineagent.sbe.automatone.pathing.movement;

import org.junit.jupiter.api.Test;

import static com.mineagent.sbe.automatone.api.pathing.movement.ActionCosts.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ActionCostsTest {

    @Test
    public void testFallNBlocksCost() {
        assertEquals(FALL_N_BLOCKS_COST.length, 513); // Fall 0 blocks through fall 512 blocks
        for (int i = 0; i < 257; i++) {
            double blocks = ticksToBlocks(FALL_N_BLOCKS_COST[i]);
            assertEquals(blocks, i, 0.000000000001); // If you add another 0 the test fails at i=217 LOL
        }
        assertEquals(FALL_1_25_BLOCKS_COST, 6.2344, 0.00001);
        assertEquals(FALL_0_25_BLOCKS_COST, 3.0710, 0.00001);
        assertEquals(JUMP_ONE_BLOCK_COST, 3.1634, 0.00001);
    }

    public double ticksToBlocks(double ticks) {
        double fallDistance = 0;
        int integralComponent = (int) Math.floor(ticks);
        for (int tick = 0; tick < integralComponent; tick++) {
            fallDistance += velocity(tick);
        }
        double partialTickComponent = ticks - Math.floor(ticks);
        double finalPartialTickVelocity = velocity(integralComponent);
        double finalPartialTickDistance = finalPartialTickVelocity * partialTickComponent;
        fallDistance += finalPartialTickDistance;
        return fallDistance;
    }

}
