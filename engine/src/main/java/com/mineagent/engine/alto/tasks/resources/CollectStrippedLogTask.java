// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/tasks/resources/CollectStrippedLogTask.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.tasks.resources;

import com.mineagent.engine.alto.AltoClef;
import com.mineagent.engine.alto.TaskCatalogue;
import com.mineagent.engine.alto.tasks.InteractWithBlockTask;
import com.mineagent.engine.alto.tasks.ResourceTask;
import com.mineagent.engine.alto.tasks.movement.TimeoutWanderTask;
import com.mineagent.engine.alto.tasksystem.Task;
import com.mineagent.engine.alto.util.ItemTarget;
import com.mineagent.engine.alto.util.MiningRequirement;
import com.mineagent.engine.alto.util.helpers.ItemHelper;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public class CollectStrippedLogTask extends ResourceTask {
    private static final Item[] _axes = new Item[]{Items.WOODEN_AXE, Items.STONE_AXE, Items.GOLDEN_AXE, Items.IRON_AXE,
            Items.DIAMOND_AXE, Items.NETHERITE_AXE};
    private final Item[] _strippedLogs;
    private final Item[] _strippableLogs;
    private final int _targetCount;

    public CollectStrippedLogTask(Item[] strippedLogs, Item[] strippableLogs, int count) {
        super(new ItemTarget(strippedLogs, count));
        _strippedLogs = strippedLogs;
        _strippableLogs = strippableLogs;
        _targetCount = count;
    }

    public CollectStrippedLogTask(int count) {
        this(ItemHelper.STRIPPED_LOGS, ItemHelper.STRIPPABLE_LOGS, count);
    }

    public CollectStrippedLogTask(Item strippedLogs, Item strippableLogs, int count) {
        this(new Item[]{strippedLogs}, new Item[]{strippableLogs}, count);
    }

    public CollectStrippedLogTask(Item strippedLog, int count) {
        this(strippedLog, ItemHelper.strippedToLogs(strippedLog), count);
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
        if (!mod.getItemStorage().hasItem(_axes)) {
            setDebugState("Getting axe for stripping");
            return TaskCatalogue.getItemTask(Items.WOODEN_AXE, 1);
        }
        if (mod.getItemStorage().getItemCount(_strippedLogs) < _targetCount) {
            Optional<BlockPos> strippedLogBlockPos = mod.getBlockScanner().getNearestBlock(ItemHelper.itemsToBlocks(_strippedLogs));
            if (strippedLogBlockPos.isPresent()) {
                setDebugState("Getting stripped log");
                return new MineAndCollectTask(new ItemTarget(_strippedLogs), ItemHelper.itemsToBlocks(_strippedLogs), MiningRequirement.HAND);
            }
        }
        Optional<BlockPos> strippableLogBlockPos = mod.getBlockScanner().getNearestBlock(ItemHelper.itemsToBlocks(_strippableLogs));
        if (strippableLogBlockPos.isPresent()) {
            setDebugState("Stripping log");
            return new InteractWithBlockTask(new ItemTarget(_axes), strippableLogBlockPos.get());
        }
        setDebugState("Searching log");
        return new TimeoutWanderTask();
    }

    @Override
    protected void onResourceStop(AltoClef mod, Task interruptTask) {
    }

    @Override
    protected boolean isEqualResource(ResourceTask other) {
        if (other instanceof CollectStrippedLogTask task) {
            return task._targetCount == _targetCount;
        }
        return false;
    }

    @Override
    protected String toDebugStringName() {
        return "Collect Stripped Log";
    }
}
