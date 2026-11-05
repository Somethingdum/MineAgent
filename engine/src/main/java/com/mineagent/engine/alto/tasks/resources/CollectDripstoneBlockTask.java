// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/tasks/resources/CollectDripstoneBlockTask.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.tasks.resources;

import com.mineagent.engine.alto.AltoClef;
import com.mineagent.engine.alto.multiversion.versionedfields.Blocks;
import com.mineagent.engine.alto.multiversion.versionedfields.Items;
import com.mineagent.engine.alto.tasks.CraftInInventoryTask;
import com.mineagent.engine.alto.tasks.ResourceTask;
import com.mineagent.engine.alto.tasksystem.Task;
import com.mineagent.engine.alto.util.*;
import net.minecraft.world.level.block.Block;

public class CollectDripstoneBlockTask extends ResourceTask {

    private final int _count;

    public CollectDripstoneBlockTask(int targetCount) {
        super(Items.DRIPSTONE_BLOCK, targetCount);
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
        if (mod.getItemStorage().getItemCount(Items.POINTED_DRIPSTONE) >= 4) {
            int target = mod.getItemStorage().getItemCount(Items.DRIPSTONE_BLOCK) + 1;
            ItemTarget s = new ItemTarget(Items.POINTED_DRIPSTONE, 1);
            return new CraftInInventoryTask(new RecipeTarget(Items.DRIPSTONE_BLOCK, target, CraftingRecipe.newShapedRecipe("dri", new ItemTarget[]{s, s, s, s}, 1)));
        }
        return new MineAndCollectTask(new ItemTarget(Items.DRIPSTONE_BLOCK, Items.POINTED_DRIPSTONE), new Block[]{Blocks.DRIPSTONE_BLOCK, Blocks.POINTED_DRIPSTONE}, MiningRequirement.WOOD).forceDimension(Dimension.OVERWORLD);
    }

    @Override
    protected void onResourceStop(AltoClef mod, Task interruptTask) {

    }

    @Override
    protected boolean isEqualResource(ResourceTask other) {
        return other instanceof CollectDripstoneBlockTask;
    }

    @Override
    protected String toDebugStringName() {
        return "Collecting " + _count + " Dripstone Blocks.";
    }
}
