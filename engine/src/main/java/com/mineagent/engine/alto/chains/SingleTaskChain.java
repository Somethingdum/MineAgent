// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/chains/SingleTaskChain.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.chains;

import com.mineagent.engine.alto.AltoClef;
import com.mineagent.engine.alto.Debug;
import com.mineagent.engine.alto.tasksystem.Task;
import com.mineagent.engine.alto.tasksystem.TaskChain;
import com.mineagent.engine.alto.tasksystem.TaskRunner;

public abstract class SingleTaskChain extends TaskChain {

    protected Task mainTask = null;
    private boolean interrupted = false;

    private final AltoClef mod;

    public SingleTaskChain(TaskRunner runner) {
        super(runner);
        mod = runner.getMod();
    }

    @Override
    protected void onTick() {
        if (!isActive()) return;

        if (interrupted) {
            interrupted = false;
            if (mainTask != null) {
                mainTask.reset();
            }
        }

        if (mainTask != null) {
            if ((mainTask.isFinished()) || mainTask.stopped()) {
                onTaskFinish(mod);
            } else {
                mainTask.tick(this);
            }
        }
    }

    protected void onStop() {
        if (isActive() && mainTask != null) {
            mainTask.stop();
            mainTask = null;
        }
    }

    public void setTask(Task task) {
        if (mainTask == null || !mainTask.equals(task)) {
            if (mainTask != null) {
                mainTask.stop(task);
            }
            mainTask = task;
            if (task != null) task.reset();
        }
    }


    @Override
    public boolean isActive() {
        return mainTask != null;
    }

    protected abstract void onTaskFinish(AltoClef mod);

    @Override
    public void onInterrupt(TaskChain other) {
        if (other != null) {
            Debug.logInternal("Chain Interrupted: " + this + " by " + other);
        }
        // Stop our task. When we're started up again, let our task know we need to run.
        interrupted = true;
        if (mainTask != null && mainTask.isActive()) {
            mainTask.interrupt(null);
        }
    }

    protected boolean isCurrentlyRunning(AltoClef mod) {
        return !interrupted && mainTask.isActive() && !mainTask.isFinished();
    }

    public Task getCurrentTask() {
        return mainTask;
    }
}
