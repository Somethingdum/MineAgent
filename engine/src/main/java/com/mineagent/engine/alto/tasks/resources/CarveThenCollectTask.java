// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/tasks/resources/CarveThenCollectTask.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.tasks.resources;

import com.mineagent.engine.alto.AltoClef;
import com.mineagent.engine.alto.TaskCatalogue;
import com.mineagent.engine.alto.tasks.DoToClosestBlockTask;
import com.mineagent.engine.alto.tasks.InteractWithBlockTask;
import com.mineagent.engine.alto.tasks.ResourceTask;
import com.mineagent.engine.alto.tasks.construction.DestroyBlockTask;
import com.mineagent.engine.alto.tasks.construction.PlaceBlockNearbyTask;
import com.mineagent.engine.alto.tasksystem.Task;
import com.mineagent.engine.alto.util.ItemTarget;
import com.mineagent.engine.alto.util.helpers.StorageHelper;
import java.util.Arrays;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public class CarveThenCollectTask extends ResourceTask {

    private final ItemTarget _target;
    private final Block[] _targetBlocks;
    private final ItemTarget _toCarve;
    private final Block[] _toCarveBlocks;
    private final ItemTarget _carveWith;

    public CarveThenCollectTask(ItemTarget target, Block[] targetBlocks, ItemTarget toCarve, Block[] toCarveBlocks, ItemTarget carveWith) {
        super(target);
        _target = target;
        _targetBlocks = targetBlocks;
        _toCarve = toCarve;
        _toCarveBlocks = toCarveBlocks;
        _carveWith = carveWith;
    }

    public CarveThenCollectTask(Item target, int targetCount, Block targetBlock, Item toCarve, Block toCarveBlock, Item carveWith) {
        this(new ItemTarget(target, targetCount), new Block[]{targetBlock}, new ItemTarget(toCarve, targetCount), new Block[]{toCarveBlock}, new ItemTarget(carveWith, 1));
    }

    @Override
    protected boolean shouldAvoidPickingUp(AltoClef mod) {
        return false;
    }

    @Override
    protected void onResourceStart(AltoClef mod) {

    }

    @Override
    protected Task onResourceTick(AltoClef mod) {
        // If target block spotted, break it!
        // If toCarve block spotted, carve it!
        // neededCarve = (neededTarget - currentTarget)
        // If neededCarve > currentCarveItems:
        //      collect carve items!
        // ELSE:
        //      Place carved items down

        // If our target block is placed, break it!
        if (mod.getBlockScanner().anyFound(_targetBlocks)) {
            setDebugState("Breaking carved/target block");
            return new DoToClosestBlockTask(DestroyBlockTask::new, _targetBlocks);
        }
        // Collect our "carve with" item (can be shears, axe, whatever)
        if (!StorageHelper.itemTargetsMetInventory(_carveWith)) {
            setDebugState("Collect our carve tool");
            return TaskCatalogue.getItemTask(_carveWith);
        }
        // If our carve block is spotted, carve it.
        if (mod.getBlockScanner().anyFound(_toCarveBlocks)) {
            setDebugState("Carving block");
            return new DoToClosestBlockTask(blockPos -> new InteractWithBlockTask(_carveWith, blockPos, false), _toCarveBlocks);
        }
        // Collect carve blocks if we don't have enough, or place them down if we do.
        int neededCarveItems = _target.getTargetCount() - mod.getItemStorage().getItemCount(_target);
        int currentCarveItems = mod.getItemStorage().getItemCount(_toCarve);
        if (neededCarveItems > currentCarveItems) {
            setDebugState("Collecting more blocks to carve");
            return TaskCatalogue.getItemTask(_toCarve);
        } else {
            setDebugState("Placing blocks to carve down");
            return new PlaceBlockNearbyTask(_toCarveBlocks);
        }
    }

    @Override
    protected void onResourceStop(AltoClef mod, Task interruptTask) {

    }

    @Override
    protected boolean isEqualResource(ResourceTask other) {
        if (other instanceof CarveThenCollectTask task) {
            return (task._target.equals(_target) && task._toCarve.equals(_toCarve) && Arrays.equals(task._targetBlocks, _targetBlocks) && Arrays.equals(task._toCarveBlocks, _toCarveBlocks));
        }
        return false;
    }

    @Override
    protected String toDebugStringName() {
        return "Getting after carving: " + _target;
    }
}
