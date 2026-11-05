// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/resources/CollectCoarseDirtTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.resources;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.multiversion.EntityVer;
import com.mineagent.sbe.altoclef.tasks.CraftInInventoryTask;
import com.mineagent.sbe.altoclef.tasks.ResourceTask;
import com.mineagent.sbe.altoclef.tasksystem.Task;
import com.mineagent.sbe.altoclef.util.CraftingRecipe;
import com.mineagent.sbe.altoclef.util.Dimension;
import com.mineagent.sbe.altoclef.util.ItemTarget;
import com.mineagent.sbe.altoclef.util.MiningRequirement;
import com.mineagent.sbe.altoclef.util.RecipeTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import java.util.Optional;

public class CollectCoarseDirtTask extends ResourceTask {
   private static final float CLOSE_ENOUGH_COARSE_DIRT = 128.0F;
   private final int count;

   public CollectCoarseDirtTask(int targetCount) {
      super(Items.COARSE_DIRT, targetCount);
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
      double c = Math.ceil((this.count - mod.getItemStorage().getItemCount(Items.COARSE_DIRT)) / 4.0) * 2.0;
      Optional<BlockPos> closest = mod.getBlockScanner().getNearestBlock(Blocks.COARSE_DIRT);
      if ((mod.getItemStorage().getItemCount(Items.DIRT) < c || mod.getItemStorage().getItemCount(Items.GRAVEL) < c)
         && closest.isPresent()
         && closest.get().closerToCenterThan(EntityVer.getPos(mod.getPlayer()), 128.0)) {
         return new MineAndCollectTask(new ItemTarget(Items.COARSE_DIRT), new Block[]{Blocks.COARSE_DIRT}, MiningRequirement.HAND)
            .forceDimension(Dimension.OVERWORLD);
      } else {
         int target = this.count;
         ItemTarget d = new ItemTarget(Items.DIRT, 1);
         ItemTarget g = new ItemTarget(Items.GRAVEL, 1);
         return new CraftInInventoryTask(
            new RecipeTarget(Items.COARSE_DIRT, target, CraftingRecipe.newShapedRecipe("coarse_dirt", new ItemTarget[]{d, g, g, d}, 4))
         );
      }
   }

   @Override
   protected void onResourceStop(AltoClefController mod, Task interruptTask) {
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return other instanceof CollectCoarseDirtTask;
   }

   @Override
   protected String toDebugStringName() {
      return "Collecting " + this.count + " Coarse Dirt.";
   }
}
