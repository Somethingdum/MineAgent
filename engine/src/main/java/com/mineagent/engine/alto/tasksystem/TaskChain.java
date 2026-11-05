// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/tasksystem/TaskChain.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.tasksystem;

import java.util.ArrayList;
import java.util.List;

public abstract class TaskChain {

    private final List<Task> cachedTaskChain = new ArrayList<>();

    public TaskChain(TaskRunner runner) {
        runner.addTaskChain(this);
    }

    public void tick() {
        cachedTaskChain.clear();
        onTick();
    }

    public void stop() {
        cachedTaskChain.clear();
        onStop();
    }

    protected abstract void onStop();

    public abstract void onInterrupt(TaskChain other);

    protected abstract void onTick();

    public abstract float getPriority();

    public abstract boolean isActive();

    public abstract String getName();

    public List<Task> getTasks() {
        return cachedTaskChain;
    }

    void addTaskToChain(Task task) {
        cachedTaskChain.add(task);
    }

    public String toString() {
        return getName();
    }

}
