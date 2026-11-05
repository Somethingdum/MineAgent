// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/resources/CollectHayBlockTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.resources;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.tasks.ResourceTask;
import com.mineagent.sbe.altoclef.tasks.container.CraftInTableTask;
import com.mineagent.sbe.altoclef.tasksystem.Task;
import com.mineagent.sbe.altoclef.util.CraftingRecipe;
import com.mineagent.sbe.altoclef.util.ItemTarget;
import com.mineagent.sbe.altoclef.util.MiningRequirement;
import com.mineagent.sbe.altoclef.util.RecipeTarget;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class CollectHayBlockTask extends ResourceTask {
   private final int count;

   public CollectHayBlockTask(int count) {
      super(Items.HAY_BLOCK, count);
      this.count = count;
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
      if (mod.getBlockScanner().anyFound(Blocks.HAY_BLOCK)) {
         return new MineAndCollectTask(Items.HAY_BLOCK, this.count, new Block[]{Blocks.HAY_BLOCK}, MiningRequirement.HAND);
      } else {
         ItemTarget w = new ItemTarget(Items.WHEAT, 1);
         return new CraftInTableTask(
            new RecipeTarget(Items.HAY_BLOCK, this.count, CraftingRecipe.newShapedRecipe("hay_block", new ItemTarget[]{w, w, w, w, w, w, w, w, w}, 1))
         );
      }
   }

   @Override
   protected void onResourceStop(AltoClefController mod, Task interruptTask) {
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return other instanceof CollectHayBlockTask;
   }

   @Override
   protected String toDebugStringName() {
      return "Collecting " + this.count + " hay blocks.";
   }
}
