// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/automatone/api/pathing/goals/GoalComposite.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
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

import java.util.Arrays;

/**
 * A composite of many goals, any one of which satisfies the composite.
 * For example, a GoalComposite of block goals for every oak log in loaded chunks
 * would result in it pathing to the easiest oak log to get to
 *
 * @author avecowa
 */
public class GoalComposite implements Goal {

    /**
     * An array of goals that any one of must be satisfied
     */
    private final Goal[] goals;

    public GoalComposite(Goal... goals) {
        this.goals = goals;
    }

    @Override
    public boolean isInGoal(int x, int y, int z) {
        for (Goal goal : goals) {
            if (goal.isInGoal(x, y, z)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public double heuristic(int x, int y, int z) {
        double min = Double.MAX_VALUE;
        for (Goal g : goals) {
            // TODO technically this isn't admissible...?
            min = Math.min(min, g.heuristic(x, y, z)); // whichever is closest
        }
        return min;
    }

    @Override
    public double heuristic() {
        double min = Double.MAX_VALUE;
        for (Goal g : goals) {
            // just take the highest value that is guaranteed to be inside the goal
            min = Math.min(min, g.heuristic());
        }
        return min;
    }

    @Override
    public String toString() {
        return "GoalComposite" + Arrays.toString(goals);
    }

    public Goal[] goals() {
        return goals;
    }
}
