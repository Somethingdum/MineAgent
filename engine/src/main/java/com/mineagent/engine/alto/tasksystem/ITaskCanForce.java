// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/tasksystem/ITaskCanForce.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.tasksystem;

/**
 * Lets a task declare that it's parent can NOT interrupt itself, and that this task MUST keep executing.
 */
public interface ITaskCanForce {

    /**
     * @param interruptingCandidate This task will try to interrupt our current task.
     * @return Whether the task should forcefully keep going, even when the parent decides it shouldn't
     */
    boolean shouldForce(Task interruptingCandidate);
}
