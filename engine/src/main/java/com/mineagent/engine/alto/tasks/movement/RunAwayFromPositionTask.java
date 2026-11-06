// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/tasks/movement/RunAwayFromPositionTask.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.tasks.movement;

import com.mineagent.engine.alto.AltoClef;
import com.mineagent.engine.alto.tasksystem.Task;
import com.mineagent.sbe.automatone.api.pathing.goals.Goal;
import com.mineagent.sbe.automatone.api.pathing.goals.GoalRunAway;
import java.util.Arrays;
import net.minecraft.core.BlockPos;

public class RunAwayFromPositionTask extends CustomBaritoneGoalTask {

    private final BlockPos[] _dangerBlocks;
    private final double _distance;
    private final Integer _maintainY;

    public RunAwayFromPositionTask(double distance, BlockPos... toRunAwayFrom) {
        this(distance, null, toRunAwayFrom);
    }

    public RunAwayFromPositionTask(double distance, Integer maintainY, BlockPos... toRunAwayFrom) {
        _distance = distance;
        _dangerBlocks = toRunAwayFrom;
        _maintainY = maintainY;
    }

    @Override
    protected Goal newGoal(AltoClef mod) {
        return new GoalRunAway(_distance, _maintainY, _dangerBlocks);
    }

    @Override
    protected boolean isEqual(Task other) {
        if (other instanceof RunAwayFromPositionTask task) {
            return Arrays.equals(task._dangerBlocks, _dangerBlocks);
        }
        return false;
    }

    @Override
    protected String toDebugString() {
        return "Running away from " + Arrays.toString(_dangerBlocks);
    }
}
