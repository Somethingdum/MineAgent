// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/tasks/resources/CollectGoldNuggetsTask.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.tasks.resources;

import com.mineagent.engine.alto.AltoClef;
import com.mineagent.engine.alto.TaskCatalogue;
import com.mineagent.engine.alto.tasks.CraftInInventoryTask;
import com.mineagent.engine.alto.tasks.ResourceTask;
import com.mineagent.engine.alto.tasks.movement.DefaultGoToDimensionTask;
import com.mineagent.engine.alto.tasksystem.Task;
import com.mineagent.engine.alto.util.*;
import com.mineagent.engine.alto.util.helpers.WorldHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class CollectGoldNuggetsTask extends ResourceTask {

    private final int count;

    public CollectGoldNuggetsTask(int count) {
        super(Items.GOLD_NUGGET, count);
        this.count = count;
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
        switch (WorldHelper.getCurrentDimension()) {
            case OVERWORLD -> {
                setDebugState("Getting gold ingots to convert to nuggets");
                int potentialNuggies = mod.getItemStorage().getItemCount(Items.GOLD_NUGGET) + mod.getItemStorage().getItemCount(Items.GOLD_INGOT) * 9;
                if (potentialNuggies >= count && mod.getItemStorage().hasItem(Items.GOLD_INGOT)) {
                    // Craft gold ingots to nuggets
                    return new CraftInInventoryTask(new RecipeTarget(Items.GOLD_NUGGET, count, CraftingRecipe.newShapedRecipe("golden_nuggets", new ItemTarget[]{new ItemTarget(Items.GOLD_INGOT, 1), null, null, null}, 9)));
                }
                // Get gold ingots
                int nuggiesStillNeeded = count - potentialNuggies;
                return TaskCatalogue.getItemTask(Items.GOLD_INGOT, (int) Math.ceil((double) nuggiesStillNeeded / 9.0));
            }
            case NETHER -> {
                setDebugState("Mining nuggies");
                return new MineAndCollectTask(Items.GOLD_NUGGET, count, new Block[]{Blocks.NETHER_GOLD_ORE, Blocks.GILDED_BLACKSTONE}, MiningRequirement.WOOD);
            }
            case END -> {
                setDebugState("Going to overworld");
                return new DefaultGoToDimensionTask(Dimension.OVERWORLD);
            }
        }

        setDebugState("INVALID DIMENSION??: " + WorldHelper.getCurrentDimension());
        return null;
    }

    @Override
    protected void onResourceStop(AltoClef mod, Task interruptTask) {

    }

    @Override
    protected boolean isEqualResource(ResourceTask other) {
        return other instanceof CollectGoldNuggetsTask;
    }

    @Override
    protected String toDebugStringName() {
        return "Collecting " + count + " nuggets";
    }
}
