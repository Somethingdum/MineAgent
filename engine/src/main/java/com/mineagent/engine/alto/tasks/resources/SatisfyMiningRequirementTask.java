// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/tasks/resources/SatisfyMiningRequirementTask.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.tasks.resources;

import com.mineagent.engine.alto.TaskCatalogue;
import com.mineagent.engine.alto.tasksystem.Task;
import com.mineagent.engine.alto.util.MiningRequirement;
import com.mineagent.engine.alto.util.helpers.StorageHelper;
import net.minecraft.world.item.Items;

/**
 * Make sure we have a tool at or above a mining level.
 */
public class SatisfyMiningRequirementTask extends Task {

    private final MiningRequirement requirement;

    public SatisfyMiningRequirementTask(MiningRequirement requirement) {
        this.requirement = requirement;
    }

    @Override
    protected void onStart() {

    }

    @Override
    protected Task onTick() {
        switch (requirement) {
            case HAND:
                // Will never happen if you program this right
                break;
            case WOOD:
                return TaskCatalogue.getItemTask(Items.WOODEN_PICKAXE, 1);
            case STONE:
                return TaskCatalogue.getItemTask(Items.STONE_PICKAXE, 1);
            case IRON:
                return TaskCatalogue.getItemTask(Items.IRON_PICKAXE, 1);
            case DIAMOND:
                return TaskCatalogue.getItemTask(Items.DIAMOND_PICKAXE, 1);
        }
        return null;
    }

    @Override
    protected void onStop(Task interruptTask) {

    }

    @Override
    protected boolean isEqual(Task other) {
        if (other instanceof SatisfyMiningRequirementTask task) {
            return task.requirement == requirement;
        }
        return false;
    }

    @Override
    protected String toDebugString() {
        return "Satisfy Mining Req: " + requirement;
    }

    @Override
    public boolean isFinished() {
        return StorageHelper.miningRequirementMetInventory(requirement);
    }
}
