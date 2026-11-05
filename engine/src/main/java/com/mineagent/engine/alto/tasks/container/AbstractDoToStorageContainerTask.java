// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/tasks/container/AbstractDoToStorageContainerTask.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.tasks.container;

import com.mineagent.engine.alto.AltoClef;
import com.mineagent.engine.alto.tasks.InteractWithBlockTask;
import com.mineagent.engine.alto.tasks.construction.DestroyBlockTask;
import com.mineagent.engine.alto.tasks.movement.TimeoutWanderTask;
import com.mineagent.engine.alto.tasksystem.Task;
import com.mineagent.engine.alto.trackers.storage.ContainerCache;
import com.mineagent.engine.alto.trackers.storage.ContainerType;
import com.mineagent.engine.alto.util.helpers.WorldHelper;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;

/**
 * Opens a STORAGE container and does whatever you want inside of it
 */
public abstract class AbstractDoToStorageContainerTask extends Task {

    private ContainerType currentContainerType = null;

    @Override
    protected void onStart() {

    }

    @Override
    protected Task onTick() {
        Optional<BlockPos> containerTarget = getContainerTarget();

        AltoClef mod = AltoClef.getInstance();
        // No container found
        if (containerTarget.isEmpty()) {
            setDebugState("Wandering");
            currentContainerType = null;
            return onSearchWander();
        }

        BlockPos targetPos = containerTarget.get();

        // We're open
        if (currentContainerType != null && ContainerType.screenHandlerMatches(currentContainerType)) {

            Optional<ContainerCache> cache = mod.getItemStorage().getContainerAtPosition(targetPos);
            if (cache.isPresent()) {
                return onContainerOpenSubtask(mod, cache.get());
            }
        }

        // Get to the container
        if (mod.getChunkTracker().isChunkLoaded(targetPos)) {
            Block type = mod.getWorld().getBlockState(targetPos).getBlock();
            currentContainerType = ContainerType.getFromBlock(type);
        }
        if (WorldHelper.isChest(targetPos) && WorldHelper.isSolidBlock(targetPos.above()) && WorldHelper.canBreak(targetPos.above())) {
            setDebugState("Clearing block above chest");
            return new DestroyBlockTask(targetPos.above());
        }
        setDebugState("Opening container: " + targetPos.toShortString());
        return new InteractWithBlockTask(targetPos);
    }

    @Override
    protected void onStop(Task interruptTask) {

    }

    protected abstract Optional<BlockPos> getContainerTarget();

    protected abstract Task onContainerOpenSubtask(AltoClef mod, ContainerCache containerCache);

    // Virtual
    // TODO: Interface this
    protected Task onSearchWander() {
        return new TimeoutWanderTask();
    }
}
