// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/chains/UserTaskChain.java
// at commit 1f8e038, in Mojang's names (third_party/altoclef). SMH: the order the companion was given is its one user
// task, set by its host each tick (alto/AltoTreeTask); finishing is the engine's to report, so nothing is said here, and
// there are no idle commands.
package com.mineagent.engine.alto.chains;

import com.mineagent.engine.alto.AltoClef;
import com.mineagent.engine.alto.tasksystem.TaskRunner;

public class UserTaskChain extends SingleTaskChain {

    public UserTaskChain(TaskRunner runner) {
        super(runner);
    }

    @Override
    protected void onTaskFinish(AltoClef mod) {
        // SMH: the host sees the task finished and ends the order itself.
    }

    @Override
    public float getPriority() {
        return 50;
    }

    @Override
    public String getName() {
        return "User Tasks";
    }
}
