// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/tasks/movement/GetToXZTask.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.tasks.movement;

import com.mineagent.engine.alto.AltoClef;
import com.mineagent.engine.alto.tasksystem.Task;
import com.mineagent.engine.alto.util.Dimension;
import com.mineagent.engine.alto.util.helpers.WorldHelper;
import com.mineagent.engine.alto.baritone.api.pathing.goals.Goal;
import com.mineagent.engine.alto.baritone.api.pathing.goals.GoalXZ;
import net.minecraft.core.BlockPos;

public class GetToXZTask extends CustomBaritoneGoalTask {

    private final int x, z;
    private final Dimension dimension;

    public GetToXZTask(int x, int z) {
        this(x, z, null);
    }

    public GetToXZTask(int x, int z, Dimension dimension) {
        this.x = x;
        this.z = z;
        this.dimension = dimension;
    }

    @Override
    protected Task onTick() {
        if (dimension != null && WorldHelper.getCurrentDimension() != dimension) {
            return new DefaultGoToDimensionTask(dimension);
        }
        return super.onTick();
    }

    @Override
    protected Goal newGoal(AltoClef mod) {
        return new GoalXZ(x, z);
    }

    @Override
    protected boolean isEqual(Task other) {
        if (other instanceof GetToXZTask task) {
            return task.x == x && task.z == z && task.dimension == dimension;
        }
        return false;
    }

    @Override
    public boolean isFinished() {
        BlockPos cur = AltoClef.getInstance().getPlayer().blockPosition();
        return (cur.getX() == x && cur.getZ() == z && (dimension == null || dimension == WorldHelper.getCurrentDimension()));
    }

    @Override
    protected String toDebugString() {
        return "Getting to (" + x + "," + z + ")" + (dimension != null ? " in dimension " + dimension : "");
    }
}
