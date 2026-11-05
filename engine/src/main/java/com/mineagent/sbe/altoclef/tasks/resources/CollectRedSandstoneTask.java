// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/resources/CollectRedSandstoneTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.resources;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.tasks.CraftInInventoryTask;
import com.mineagent.sbe.altoclef.tasks.ResourceTask;
import com.mineagent.sbe.altoclef.tasksystem.Task;
import com.mineagent.sbe.altoclef.util.CraftingRecipe;
import com.mineagent.sbe.altoclef.util.Dimension;
import com.mineagent.sbe.altoclef.util.ItemTarget;
import com.mineagent.sbe.altoclef.util.MiningRequirement;
import com.mineagent.sbe.altoclef.util.RecipeTarget;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class CollectRedSandstoneTask extends ResourceTask {
   private final int count;

   public CollectRedSandstoneTask(int targetCount) {
      super(Items.RED_SANDSTONE, targetCount);
      this.count = targetCount;
   }

   @Override
   protected boolean shouldAvoidPickingUp(AltoClefController mod) {
      return false;
   }

   @Override
   protected void onResourceStart(AltoClefController mod) {
   }

   @Override
   protected Task onResourceTick(AltoClefController mod) {
      if (mod.getItemStorage().getItemCount(Items.RED_SAND) >= 4) {
         int target = mod.getItemStorage().getItemCount(Items.RED_SANDSTONE) + 1;
         ItemTarget s = new ItemTarget(Items.RED_SAND, 1);
         return new CraftInInventoryTask(
            new RecipeTarget(Items.RED_SANDSTONE, target, CraftingRecipe.newShapedRecipe("red_sandstone", new ItemTarget[]{s, s, s, s}, 1))
         );
      } else {
         return new MineAndCollectTask(
               new ItemTarget(Items.RED_SANDSTONE, Items.RED_SAND), new Block[]{Blocks.RED_SANDSTONE, Blocks.RED_SAND}, MiningRequirement.WOOD
            )
            .forceDimension(Dimension.OVERWORLD);
      }
   }

   @Override
   protected void onResourceStop(AltoClefController mod, Task interruptTask) {
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return other instanceof CollectRedSandstoneTask;
   }

   @Override
   protected String toDebugStringName() {
      return "Collecting " + this.count + " red sandstone.";
   }
}
