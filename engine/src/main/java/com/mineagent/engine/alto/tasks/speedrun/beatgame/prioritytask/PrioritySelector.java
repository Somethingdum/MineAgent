// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): the way
// adris/altoclef/tasks/speedrun/beatgame/BeatMinecraftTask.java at commit 1f8e038 picks what to gather in the overworld
// (its onTick's OVERWORLD branch, with its TaskChange record and isTaskRunning), lifted out so a list of priority tasks
// can be run by any task. Changes are marked SMH.
package com.mineagent.engine.alto.tasks.speedrun.beatgame.prioritytask;

import com.mineagent.engine.alto.AltoClef;
import com.mineagent.engine.alto.tasks.speedrun.beatgame.prioritytask.tasks.PriorityTask;
import com.mineagent.engine.alto.tasksystem.Task;
import com.mineagent.engine.alto.util.time.TimerGame;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;

public class PrioritySelector {

    private final TimerGame changedTaskTimer = new TimerGame(3);
    private final TimerGame forcedTaskTimer = new TimerGame(10);
    private final List<TaskChange> taskChanges = new ArrayList<>();
    private PriorityTask lastGather = null;
    private PriorityTask prevLastGather = null;
    private Task lastTask = null;
    private String debug = "";
    /** SMH: priority tasks left alone until this game tick (one that got nowhere, see {@link #rest}). */
    private final Map<PriorityTask, Long> restingUntil = new HashMap<>();

    public static boolean isTaskRunning(Task task) {
        return task != null && task.isActive() && !task.isFinished();
    }

    /** The priority task chosen last, or null. */
    public PriorityTask current() {
        return lastGather;
    }

    /** The task it is running for it, or null. */
    public Task currentTask() {
        return lastTask;
    }

    /** What it chose and why, for a debug state. */
    public String debug() {
        return debug;
    }

    /**
     * SMH (after Voyager's "failed tasks that are too hard", MIT): a priority task that got nowhere is left alone for a
     * while, and the next best is done meanwhile.
     */
    public void rest(PriorityTask task, long untilTick) {
        if (task == null) return;
        restingUntil.put(task, untilTick);
        if (task == lastGather) {
            lastGather = null;
            lastTask = null;
        }
    }

    public boolean resting(AltoClef mod, PriorityTask task) {
        Long until = restingUntil.get(task);
        if (until == null) return false;
        if (mod.getWorld().getGameTime() >= until) {
            restingUntil.remove(task);
            return false;
        }
        return true;
    }

    /** Forget what it was doing (its task was stopped from outside). */
    public void reset() {
        lastGather = null;
        prevLastGather = null;
        lastTask = null;
        taskChanges.clear();
    }

    /**
     * The task to run now from {@code gatherResources}, or null when none has a priority above zero. As BeatMinecraftTask
     * does: what it is doing is kept for a few seconds after a change, while forced, and when it would only switch back
     * and forth on the spot; else the highest priority wins.
     */
    public Task choose(AltoClef mod, List<PriorityTask> gatherResources) {
        PriorityTask toGather = null;
        double maxPriority = 0;

        if (!gatherResources.isEmpty()) {
            if (!forcedTaskTimer.elapsed() && isTaskRunning(lastTask) && lastGather != null && lastGather.calculatePriority(mod) > 0) {
                return lastTask;
            }

            if (!changedTaskTimer.elapsed() && lastTask != null && !lastGather.bypassForceCooldown && isTaskRunning(lastTask)) {
                return lastTask;
            }
            if (isTaskRunning(lastTask) && lastGather != null && lastGather.shouldForce()) {
                return lastTask;
            }

            for (PriorityTask gatherResource : gatherResources) {
                if (resting(mod, gatherResource)) continue; // SMH
                double priority = gatherResource.calculatePriority(mod);

                if (priority > maxPriority) {
                    maxPriority = priority;
                    toGather = gatherResource;
                }
            }
        }
        if (toGather == null) {
            debug = "Nothing to do";
            lastGather = null;
            lastTask = null;
            return null;
        }
        boolean sameTask = lastGather == toGather;

        debug = "Priority: " + String.format(Locale.US, "%.2f", maxPriority) + ", " + toGather;
        if (!sameTask && prevLastGather == toGather && lastTask != null && lastGather.calculatePriority(mod) > 0 && isTaskRunning(lastTask)) {
            mod.logWarning("might be stuck or switching too much, forcing current resource for a bit more");
            changedTaskTimer.reset();
            prevLastGather = null; //do not force infinitely, 3 sec should be enough I hope
            debug = "Priority: FORCED, " + lastGather;
            return lastTask;
        }

        if (sameTask && toGather.canCache()) {
            return lastTask;
        }
        if (!sameTask) {
            taskChanges.add(0, new TaskChange(lastGather, toGather, mod.getPlayer().blockPosition()));
        }

        if (taskChanges.size() >= 3 && !sameTask) {
            TaskChange t1 = taskChanges.get(0);
            TaskChange t2 = taskChanges.get(1);
            TaskChange t3 = taskChanges.get(2);

            if (t1.original == t2.interrupt && t1.pos.closerThan(t3.pos, 5) && t3.original == t1.interrupt) {
                forcedTaskTimer.reset();
                mod.logWarning("Probably stuck! Forcing timer...");
                taskChanges.clear();
                return lastTask;
            }
            if (taskChanges.size() > 3) {
                taskChanges.remove(taskChanges.size() - 1);
            }
        }

        prevLastGather = lastGather;
        lastGather = toGather;

        Task task = toGather.getTask(mod);
        lastTask = task;
        changedTaskTimer.reset();
        return task;
    }

    private record TaskChange(PriorityTask original, PriorityTask interrupt, BlockPos pos) {
    }
}
