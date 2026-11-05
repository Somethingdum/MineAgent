// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/resources/CollectWheatTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.resources;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.tasks.CraftInInventoryTask;
import com.mineagent.sbe.altoclef.tasks.ResourceTask;
import com.mineagent.sbe.altoclef.tasksystem.Task;
import com.mineagent.sbe.altoclef.util.CraftingRecipe;
import com.mineagent.sbe.altoclef.util.ItemTarget;
import com.mineagent.sbe.altoclef.util.MiningRequirement;
import com.mineagent.sbe.altoclef.util.RecipeTarget;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class CollectWheatTask extends ResourceTask {
   private final int count;

   public CollectWheatTask(int targetCount) {
      super(Items.WHEAT, targetCount);
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
      int potentialCount = mod.getItemStorage().getItemCount(Items.WHEAT) + 9 * mod.getItemStorage().getItemCount(Items.HAY_BLOCK);
      if (potentialCount >= this.count) {
         this.setDebugState("Crafting wheat");
         return new CraftInInventoryTask(
            new RecipeTarget(
               Items.WHEAT, this.count, CraftingRecipe.newShapedRecipe("wheat", new ItemTarget[]{new ItemTarget(Items.HAY_BLOCK, 1), null, null, null}, 9)
            )
         );
      } else {
         return (Task)(!mod.getBlockScanner().anyFound(Blocks.HAY_BLOCK) && !mod.getEntityTracker().itemDropped(Items.HAY_BLOCK)
            ? new CollectCropTask(new ItemTarget(Items.WHEAT, this.count), new Block[]{Blocks.WHEAT}, Items.WHEAT_SEEDS)
            : new MineAndCollectTask(Items.HAY_BLOCK, 99999999, new Block[]{Blocks.HAY_BLOCK}, MiningRequirement.HAND));
      }
   }

   @Override
   protected void onResourceStop(AltoClefController mod, Task interruptTask) {
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return other instanceof CollectWheatTask;
   }

   @Override
   protected String toDebugStringName() {
      return "Collecting " + this.count + " wheat.";
   }
}
