// Ported from Baritone (LGPL-3.0, the licence of this mod; see THIRD_PARTY_NOTICES.md): src/api/java/baritone/api/process/ICustomGoalProcess.java
// at commit 11ea66c (branch 1.21.1). SMH: without IBaritoneProcess (the companion's Baritone has no process manager).
package com.mineagent.engine.alto.baritone.api.process;

import com.mineagent.engine.alto.baritone.api.pathing.goals.Goal;

public interface ICustomGoalProcess {

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

    boolean isActive();

    void onLostControl();

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
