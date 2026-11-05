// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/resources/CollectGoldIngotTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.resources;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.tasks.ResourceTask;
import com.mineagent.sbe.altoclef.tasks.container.CraftInTableTask;
import com.mineagent.sbe.altoclef.tasks.container.SmeltInFurnaceTask;
import com.mineagent.sbe.altoclef.tasks.movement.DefaultGoToDimensionTask;
import com.mineagent.sbe.altoclef.tasksystem.Task;
import com.mineagent.sbe.altoclef.util.CraftingRecipe;
import com.mineagent.sbe.altoclef.util.Dimension;
import com.mineagent.sbe.altoclef.util.ItemTarget;
import com.mineagent.sbe.altoclef.util.MiningRequirement;
import com.mineagent.sbe.altoclef.util.RecipeTarget;
import com.mineagent.sbe.altoclef.util.SmeltTarget;
import com.mineagent.sbe.altoclef.util.helpers.WorldHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class CollectGoldIngotTask extends ResourceTask {
   private final int count;

   public CollectGoldIngotTask(int count) {
      super(Items.GOLD_INGOT, count);
      this.count = count;
   }

   @Override
   protected boolean shouldAvoidPickingUp(AltoClefController mod) {
      return false;
   }

   @Override
   protected void onResourceStart(AltoClefController mod) {
      mod.getBehaviour().push();
   }

   @Override
   protected Task onResourceTick(AltoClefController mod) {
      if (WorldHelper.getCurrentDimension(mod) == Dimension.OVERWORLD) {
         return new SmeltInFurnaceTask(new SmeltTarget(new ItemTarget(Items.GOLD_INGOT, this.count), new ItemTarget(Items.RAW_GOLD, this.count)));
      } else if (WorldHelper.getCurrentDimension(mod) == Dimension.NETHER) {
         int nuggs = mod.getItemStorage().getItemCount(Items.GOLD_NUGGET);
         int nuggs_needed = this.count * 9 - mod.getItemStorage().getItemCount(Items.GOLD_INGOT) * 9;
         if (nuggs >= nuggs_needed) {
            ItemTarget n = new ItemTarget(Items.GOLD_NUGGET);
            CraftingRecipe recipe = CraftingRecipe.newShapedRecipe("gold_ingot", new ItemTarget[]{n, n, n, n, n, n, n, n, n}, 1);
            return new CraftInTableTask(new RecipeTarget(Items.GOLD_INGOT, this.count, recipe));
         } else {
            return new MineAndCollectTask(new ItemTarget(Items.GOLD_NUGGET, this.count * 9), new Block[]{Blocks.NETHER_GOLD_ORE}, MiningRequirement.WOOD);
         }
      } else {
         return new DefaultGoToDimensionTask(Dimension.OVERWORLD);
      }
   }

   @Override
   protected void onResourceStop(AltoClefController mod, Task interruptTask) {
      mod.getBehaviour().pop();
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return other instanceof CollectGoldIngotTask && ((CollectGoldIngotTask)other).count == this.count;
   }

   @Override
   protected String toDebugStringName() {
      return "Collecting " + this.count + " gold.";
   }
}
