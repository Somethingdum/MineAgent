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
// src/test/java/baritone/utils/pathing/PathingBlockTypeTest.java. Translated for this mod (SMH plan 71, step 1.9): its package renamed, Minecraft's
// names in Mojang's mappings, JUnit 5 for JUnit 4 (the assertion message last).
package com.mineagent.sbe.automatone.utils.pathing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class PathingBlockTypeTest {

    @Test
    public void testBits() {
        for (PathingBlockType type : PathingBlockType.values()) {
            boolean[] bits = type.getBits();
            assertTrue(type == PathingBlockType.fromBits(bits[0], bits[1]));
        }
    }
}
