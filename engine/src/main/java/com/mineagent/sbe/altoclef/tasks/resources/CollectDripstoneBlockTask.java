// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/resources/CollectDripstoneBlockTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
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

public class CollectDripstoneBlockTask extends ResourceTask {
   private final int count;

   public CollectDripstoneBlockTask(int targetCount) {
      super(Items.DRIPSTONE_BLOCK, targetCount);
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
      if (mod.getItemStorage().getItemCount(Items.POINTED_DRIPSTONE) >= 4) {
         int target = mod.getItemStorage().getItemCount(Items.DRIPSTONE_BLOCK) + 1;
         ItemTarget s = new ItemTarget(Items.POINTED_DRIPSTONE, 1);
         return new CraftInInventoryTask(
            new RecipeTarget(Items.DRIPSTONE_BLOCK, target, CraftingRecipe.newShapedRecipe("dri", new ItemTarget[]{s, s, s, s}, 1))
         );
      } else {
         return new MineAndCollectTask(
               new ItemTarget(Items.DRIPSTONE_BLOCK, Items.POINTED_DRIPSTONE),
               new Block[]{Blocks.DRIPSTONE_BLOCK, Blocks.POINTED_DRIPSTONE},
               MiningRequirement.WOOD
            )
            .forceDimension(Dimension.OVERWORLD);
      }
   }

   @Override
   protected void onResourceStop(AltoClefController mod, Task interruptTask) {
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return other instanceof CollectDripstoneBlockTask;
   }

   @Override
   protected String toDebugStringName() {
      return "Collecting " + this.count + " Dripstone Blocks.";
   }
}
