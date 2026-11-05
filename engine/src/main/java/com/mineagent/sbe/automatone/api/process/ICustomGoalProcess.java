// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/automatone/api/process/ICustomGoalProcess.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
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

package com.mineagent.sbe.automatone.api.process;

import com.mineagent.sbe.automatone.api.pathing.goals.Goal;

public interface ICustomGoalProcess extends IBaritoneProcess {

    /**
     * Sets the pathing goal
     *
     * @param goal The new goal
     */
    void setGoal(Goal goal);

    /**
     * Starts path calculation and execution.
     */
    void path();

    /**
     * @return The current goal
     */
    Goal getGoal();

    /**
     * Sets the goal and begins the path execution.
     *
     * @param goal The new goal
     */
    default void setGoalAndPath(Goal goal) {
        this.setGoal(goal);
        this.path();
    }
}
