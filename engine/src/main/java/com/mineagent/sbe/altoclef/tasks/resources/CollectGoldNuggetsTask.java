// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/resources/CollectGoldNuggetsTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.resources;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.TaskCatalogue;
import com.mineagent.sbe.altoclef.tasks.CraftInInventoryTask;
import com.mineagent.sbe.altoclef.tasks.ResourceTask;
import com.mineagent.sbe.altoclef.tasks.movement.DefaultGoToDimensionTask;
import com.mineagent.sbe.altoclef.tasksystem.Task;
import com.mineagent.sbe.altoclef.util.CraftingRecipe;
import com.mineagent.sbe.altoclef.util.Dimension;
import com.mineagent.sbe.altoclef.util.ItemTarget;
import com.mineagent.sbe.altoclef.util.MiningRequirement;
import com.mineagent.sbe.altoclef.util.RecipeTarget;
import com.mineagent.sbe.altoclef.util.helpers.WorldHelper;
import net.minecraft.world.item.Item;
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
   protected boolean shouldAvoidPickingUp(AltoClefController mod) {
      return false;
   }

   @Override
   protected void onResourceStart(AltoClefController mod) {
   }

    protected Task onResourceTick(AltoClefController mod) {
        int potentialNuggies, nuggiesStillNeeded;
        switch (WorldHelper.getCurrentDimension(controller).ordinal()) {
            case 1:
                setDebugState("Getting gold ingots to convert to nuggets");
                potentialNuggies = mod.getItemStorage().getItemCount(new Item[]{Items.GOLD_NUGGET}) + mod.getItemStorage().getItemCount(new Item[]{Items.GOLD_INGOT}) * 9;
                if (potentialNuggies >= this.count && mod.getItemStorage().hasItem(new Item[]{Items.GOLD_INGOT}))
                    return (Task) new CraftInInventoryTask(new RecipeTarget(Items.GOLD_NUGGET, this.count, CraftingRecipe.newShapedRecipe("golden_nuggets", new ItemTarget[]{new ItemTarget(Items.GOLD_INGOT, 1), null, null, null}, 9)));
                nuggiesStillNeeded = this.count - potentialNuggies;
                return (Task) TaskCatalogue.getItemTask(Items.GOLD_INGOT, (int) Math.ceil(nuggiesStillNeeded / 9.0D));
            case 2:
                setDebugState("Mining nuggies");
                return (Task) new MineAndCollectTask(Items.GOLD_NUGGET, this.count, new Block[]{Blocks.NETHER_GOLD_ORE, Blocks.GILDED_BLACKSTONE}, MiningRequirement.WOOD);
            case 3:
                setDebugState("Going to overworld");
                return (Task) new DefaultGoToDimensionTask(Dimension.OVERWORLD);
        }
        setDebugState("INVALID DIMENSION??: " + String.valueOf(WorldHelper.getCurrentDimension(controller)));
        return null;
    }

   @Override
   protected void onResourceStop(AltoClefController mod, Task interruptTask) {
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return other instanceof CollectGoldNuggetsTask;
   }

   @Override
   protected String toDebugStringName() {
      return "Collecting " + this.count + " nuggets";
   }
}
