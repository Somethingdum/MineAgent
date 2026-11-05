// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/automatone/api/pathing/goals/GoalYLevel.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
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

package com.mineagent.sbe.automatone.api.pathing.goals;

import com.mineagent.sbe.automatone.api.pathing.movement.ActionCosts;
import com.mineagent.sbe.automatone.api.utils.SettingsUtil;

/**
 * Useful for mining (getting to diamond / iron level)
 *
 * @author leijurv
 */
public class GoalYLevel implements Goal, ActionCosts {

    /**
     * The target Y level
     */
    public final int level;

    public GoalYLevel(int level) {
        this.level = level;
    }

    @Override
    public boolean isInGoal(int x, int y, int z) {
        return y == level;
    }

    @Override
    public double heuristic(int x, int y, int z) {
        return calculate(level, y);
    }

    public static double calculate(int goalY, int currentY) {
        if (currentY > goalY) {
            // need to descend
            return FALL_N_BLOCKS_COST[2] / 2 * (currentY - goalY);
        }
        if (currentY < goalY) {
            // need to ascend
            return (goalY - currentY) * JUMP_ONE_BLOCK_COST;
        }
        return 0;
    }

    @Override
    public String toString() {
        return String.format(
                "GoalYLevel{y=%s}",
                SettingsUtil.maybeCensor(level)
        );
    }
}
