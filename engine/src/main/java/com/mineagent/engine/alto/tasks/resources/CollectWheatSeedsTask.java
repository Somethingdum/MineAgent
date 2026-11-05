// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/tasks/resources/CollectWheatSeedsTask.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.tasks.resources;

import com.mineagent.engine.alto.AltoClef;
import com.mineagent.engine.alto.tasks.ResourceTask;
import com.mineagent.engine.alto.tasksystem.Task;
import com.mineagent.engine.alto.util.MiningRequirement;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class CollectWheatSeedsTask extends ResourceTask {

    private final int _count;

    public CollectWheatSeedsTask(int count) {
        super(Items.WHEAT_SEEDS, count);
        _count = count;
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
        // If wheat block found, collect wheat but don't pick up the wheat.
        if (mod.getBlockScanner().anyFound(Blocks.WHEAT)) {
            return new CollectCropTask(Items.AIR, 999, Blocks.WHEAT, Items.WHEAT_SEEDS);
        }
        // Otherwise, break grass blocks.
        return new MineAndCollectTask(Items.WHEAT_SEEDS, _count, new Block[]{Blocks.SHORT_GRASS, Blocks.TALL_GRASS}, MiningRequirement.HAND);
    }

    @Override
    protected void onResourceStop(AltoClef mod, Task interruptTask) {

    }

    @Override
    protected boolean isEqualResource(ResourceTask other) {
        return other instanceof CollectWheatSeedsTask;
    }

    @Override
    protected String toDebugStringName() {
        return "Collecting " + _count + " wheat seeds.";
    }
}
