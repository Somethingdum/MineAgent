// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/tasks/resources/CollectSandstoneTask.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.tasks.resources;

import com.mineagent.engine.alto.AltoClef;
import com.mineagent.engine.alto.tasks.CraftInInventoryTask;
import com.mineagent.engine.alto.tasks.ResourceTask;
import com.mineagent.engine.alto.tasksystem.Task;
import com.mineagent.engine.alto.util.*;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class CollectSandstoneTask extends ResourceTask {

    private final int _count;

    public CollectSandstoneTask(int targetCount) {
        super(Items.SANDSTONE, targetCount);
        _count = targetCount;
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
        if (mod.getItemStorage().getItemCount(Items.SAND) >= 4) {
            int target = mod.getItemStorage().getItemCount(Items.SANDSTONE) + 1;
            ItemTarget s = new ItemTarget(Items.SAND, 1);
            return new CraftInInventoryTask(new RecipeTarget(Items.SANDSTONE, target, CraftingRecipe.newShapedRecipe("sandstone", new ItemTarget[]{s, s, s, s}, 1)));
        }
        return new MineAndCollectTask(new ItemTarget(Items.SANDSTONE, Items.SAND), new Block[]{Blocks.SANDSTONE, Blocks.SAND}, MiningRequirement.WOOD).forceDimension(Dimension.OVERWORLD);
    }

    @Override
    protected void onResourceStop(AltoClef mod, Task interruptTask) {

    }

    @Override
    protected boolean isEqualResource(ResourceTask other) {
        return other instanceof CollectSandstoneTask;
    }

    @Override
    protected String toDebugStringName() {
        return "Collecting " + _count + " sandstone.";
    }
}
