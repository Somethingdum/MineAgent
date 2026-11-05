// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/tasks/squashed/CataloguedResourceTask.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.tasks.squashed;

import com.mineagent.engine.alto.AltoClef;
import com.mineagent.engine.alto.TaskCatalogue;
import com.mineagent.engine.alto.tasks.ResourceTask;
import com.mineagent.engine.alto.tasks.container.CraftInTableTask;
import com.mineagent.engine.alto.tasks.container.UpgradeInSmithingTableTask;
import com.mineagent.engine.alto.tasksystem.Task;
import com.mineagent.engine.alto.util.ItemTarget;
import com.mineagent.engine.alto.util.helpers.StorageHelper;
import org.apache.commons.lang3.ArrayUtils;

import java.util.*;

public class CataloguedResourceTask extends ResourceTask {


    private final TaskSquasher squasher;
    private final ItemTarget[] targets;
    private final List<ResourceTask> tasksToComplete;

    public CataloguedResourceTask(boolean squash, ItemTarget... targets) {
        super(targets);
        squasher = new TaskSquasher();
        this.targets = targets;
        tasksToComplete = new ArrayList<>(targets.length);

        for (ItemTarget target : targets) {
            if (target != null) {
                tasksToComplete.add(TaskCatalogue.getItemTask(target));
            }
        }

        if (squash) {
            squashTasks(tasksToComplete);
        }
    }

    public CataloguedResourceTask(ItemTarget... targets) {
        this(true, targets);
    }

    @Override
    protected void onResourceStart(AltoClef mod) {

    }

    @Override
    protected Task onResourceTick(AltoClef mod) {
        for (ResourceTask task : tasksToComplete) {
            for (ItemTarget target : task.getItemTargets()) {
                // If we failed to meet this task's targets, do the task.
                if (!StorageHelper.itemTargetsMetInventory(target)) return task;
            }
        }
        return null;
    }

    @Override
    public boolean isFinished() {
        for (ResourceTask task : tasksToComplete) {
            for (ItemTarget target : task.getItemTargets()) {
                if (!StorageHelper.itemTargetsMetInventory(target)) return false;
            }
        }
        // All targets are met.
        return true;
    }

    @Override
    protected boolean shouldAvoidPickingUp(AltoClef mod) {
        // Useless
        return false;
    }

    @Override
    protected void onResourceStop(AltoClef mod, Task interruptTask) {

    }

    @Override
    protected boolean isEqualResource(ResourceTask other) {
        if (other instanceof CataloguedResourceTask task) {
            return Arrays.equals(task.targets, targets);
        }
        return false;
    }

    @Override
    protected String toDebugStringName() {
        return "Get catalogued: " + ArrayUtils.toString(targets);
    }

    private void squashTasks(List<ResourceTask> tasks) {
        squasher.addTasks(tasks);
        tasks.clear();
        tasks.addAll(squasher.getSquashed());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static class TaskSquasher {

        private final Map<Class, com.mineagent.engine.alto.tasks.squashed.TypeSquasher> _squashMap = new HashMap<>();

        private final List<ResourceTask> _unSquashableTasks = new ArrayList<>();

        public TaskSquasher() {
            _squashMap.put(CraftInTableTask.class, new CraftSquasher());
            _squashMap.put(UpgradeInSmithingTableTask.class, new SmithingSquasher());
            //_squashMap.put(MineAndCollectTask.class)
        }

        public void addTask(ResourceTask t) {
            Class type = t.getClass();
            if (_squashMap.containsKey(type)) {
                _squashMap.get(type).add(t);
            } else {
                //Debug.logMessage("Unsquashable: " + type + ": " + t);
                _unSquashableTasks.add(t);
            }
        }

        public void addTasks(List<ResourceTask> tasks) {
            for (ResourceTask task : tasks) {
                addTask(task);
            }
        }

        public List<ResourceTask> getSquashed() {
            List<ResourceTask> result = new ArrayList<>();

            for (Class type : _squashMap.keySet()) {
                result.addAll(_squashMap.get(type).getSquashed());
            }
            result.addAll(_unSquashableTasks);

            return result;
        }
    }


}
