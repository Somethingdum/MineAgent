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
// src/test/java/baritone/pathing/goals/GoalGetToBlockTest.java. Translated for this mod (SMH plan 71, step 1.9): its package renamed, Minecraft's
// names in Mojang's mappings, JUnit 5 for JUnit 4 (the assertion message last); its print of every one of the 9,261 positions left out.
package com.mineagent.sbe.automatone.pathing.goals;

import com.mineagent.sbe.automatone.api.pathing.goals.GoalGetToBlock;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class GoalGetToBlockTest {

    @Test
    public void isInGoal() {
        List<String> acceptableOffsets = new ArrayList<>(Arrays.asList("0,0,0", "0,0,1", "0,0,-1", "1,0,0", "-1,0,0", "0,-1,1", "0,-1,-1", "1,-1,0", "-1,-1,0", "0,1,0", "0,-1,0", "0,-2,0"));
        for (int x = -10; x <= 10; x++) {
            for (int y = -10; y <= 10; y++) {
                for (int z = -10; z <= 10; z++) {
                    boolean inGoal = new GoalGetToBlock(new BlockPos(0, 0, 0)).isInGoal(new BlockPos(x, y, z));
                    String repr = x + "," + y + "," + z;
                            if (inGoal) {
                        assertTrue(acceptableOffsets.contains(repr), repr);
                        acceptableOffsets.remove(repr);
                    }
                }
            }
        }
        assertTrue(acceptableOffsets.isEmpty(), acceptableOffsets.toString());
    }
}
