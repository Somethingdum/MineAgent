package adris.altoclef.tasks.container;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.InteractWithBlockTask;
import adris.altoclef.tasks.construction.DestroyBlockTask;
import adris.altoclef.tasks.movement.TimeoutWanderTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.trackers.storage.ContainerCache;
import adris.altoclef.trackers.storage.ContainerType;
import adris.altoclef.util.helpers.WorldHelper;
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
