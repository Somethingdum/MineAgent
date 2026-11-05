// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/tasks/movement/GetToBlockTask.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.tasks.movement;

import com.mineagent.engine.alto.AltoClef;
import com.mineagent.engine.alto.Debug;
import com.mineagent.engine.alto.tasksystem.ITaskRequiresGrounded;
import com.mineagent.engine.alto.tasksystem.Task;
import com.mineagent.engine.alto.util.Dimension;
import com.mineagent.engine.alto.util.helpers.WorldHelper;
import com.mineagent.engine.alto.util.time.TimerGame;
import com.mineagent.engine.alto.baritone.api.pathing.goals.Goal;
import com.mineagent.engine.alto.baritone.api.pathing.goals.GoalBlock;
import net.minecraft.core.BlockPos;

public class GetToBlockTask extends CustomBaritoneGoalTask implements ITaskRequiresGrounded {

    private final BlockPos _position;
    private final boolean _preferStairs;
    private final Dimension _dimension;
    private int finishedTicks = 0;
    private final TimerGame wanderTimer = new TimerGame(2);

    public GetToBlockTask(BlockPos position, boolean preferStairs) {
        this(position, preferStairs, null);
    }

    public GetToBlockTask(BlockPos position, Dimension dimension) {
        this(position, false, dimension);
    }

    public GetToBlockTask(BlockPos position, boolean preferStairs, Dimension dimension) {
        _dimension = dimension;
        _position = position;
        _preferStairs = preferStairs;
    }

    public GetToBlockTask(BlockPos position) {
        this(position, false);
    }

    @Override
    protected Task onTick() {
        if (_dimension != null && WorldHelper.getCurrentDimension() != _dimension) {
            return new DefaultGoToDimensionTask(_dimension);
        }

        if (isFinished()) {
            finishedTicks++;
        } else {
            finishedTicks = 0;
        }
        if (finishedTicks > 10*20) {
            wanderTimer.reset();
            Debug.logWarning("GetToBlock was finished for 10 seconds yet is still being called, wandering");
            finishedTicks = 0;
            return new TimeoutWanderTask();
        }
        if (!wanderTimer.elapsed()) {
            return new TimeoutWanderTask();
        }

        return super.onTick();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (_preferStairs) {
            AltoClef.getInstance().getBehaviour().push();
            AltoClef.getInstance().getBehaviour().setPreferredStairs(true);
        }
    }


    @Override
    protected void onStop(Task interruptTask) {
        super.onStop(interruptTask);
        if (_preferStairs) {
            AltoClef.getInstance().getBehaviour().pop();
        }
    }

    @Override
    protected boolean isEqual(Task other) {
        if (other instanceof GetToBlockTask task) {
            return task._position.equals(_position) && task._preferStairs == _preferStairs && task._dimension == _dimension;
        }
        return false;
    }

    @Override
    public boolean isFinished() {
        return super.isFinished() && (_dimension == null || _dimension == WorldHelper.getCurrentDimension());
    }

    @Override
    protected String toDebugString() {
        return "Getting to block " + _position + (_dimension != null ? " in dimension " + _dimension : "");
    }


    @Override
    protected Goal newGoal(AltoClef mod) {
        return new GoalBlock(_position);
    }

    @Override
    protected void onWander(AltoClef mod) {
        super.onWander(mod);
        mod.getBlockScanner().requestBlockUnreachable(_position);
    }
}
