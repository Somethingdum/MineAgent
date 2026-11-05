// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/tasks/movement/GetWithinRangeOfBlockTask.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.tasks.movement;

import com.mineagent.engine.alto.AltoClef;
import com.mineagent.engine.alto.tasksystem.Task;
import com.mineagent.engine.alto.baritone.api.pathing.goals.Goal;
import com.mineagent.engine.alto.baritone.api.pathing.goals.GoalNear;
import net.minecraft.core.BlockPos;

public class GetWithinRangeOfBlockTask extends CustomBaritoneGoalTask {

    public final BlockPos blockPos;
    public final int range;

    public GetWithinRangeOfBlockTask(BlockPos blockPos, int range) {
        this.blockPos = blockPos;
        this.range = range;
    }

    @Override
    protected Goal newGoal(AltoClef mod) {
        return new GoalNear(blockPos, range);
    }

    @Override
    protected boolean isEqual(Task other) {
        if (other instanceof GetWithinRangeOfBlockTask task) {
            return task.blockPos.equals(blockPos) && task.range == range;
        }
        return false;
    }

    @Override
    protected String toDebugString() {
        return "Getting within " + range + " blocks of " + blockPos.toShortString();
    }
}
