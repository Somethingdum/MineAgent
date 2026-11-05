// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/resources/CollectSticksTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.resources;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.multiversion.EntityVer;
import com.mineagent.sbe.altoclef.tasks.CraftInInventoryTask;
import com.mineagent.sbe.altoclef.tasks.ResourceTask;
import com.mineagent.sbe.altoclef.tasksystem.Task;
import com.mineagent.sbe.altoclef.trackers.storage.ItemStorageTracker;
import com.mineagent.sbe.altoclef.util.CraftingRecipe;
import com.mineagent.sbe.altoclef.util.ItemTarget;
import com.mineagent.sbe.altoclef.util.MiningRequirement;
import com.mineagent.sbe.altoclef.util.RecipeTarget;
import com.mineagent.sbe.altoclef.util.helpers.ItemHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import java.util.Optional;

public class CollectSticksTask extends ResourceTask {
   private final int targetCount;

   public CollectSticksTask(int targetCount) {
      super(Items.STICK, targetCount);
      this.targetCount = targetCount;
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
   protected double getPickupRange(AltoClefController mod) {
      ItemStorageTracker storage = mod.getItemStorage();
      return storage.getItemCount(ItemHelper.PLANKS) * 4 + storage.getItemCount(ItemHelper.LOG) * 4 * 4 > this.targetCount ? 10.0 : 35.0;
   }

   @Override
   protected Task onResourceTick(AltoClefController mod) {
      if (mod.getItemStorage().getItemCount(Items.BAMBOO) >= 2) {
         return new CraftInInventoryTask(
            new RecipeTarget(
               Items.STICK,
               Math.min(mod.getItemStorage().getItemCount(Items.BAMBOO) / 2, this.targetCount),
               CraftingRecipe.newShapedRecipe("sticks", new ItemTarget[]{new ItemTarget("bamboo"), null, new ItemTarget("bamboo"), null}, 1)
            )
         );
      } else {
         Optional<BlockPos> nearestBush = mod.getBlockScanner().getNearestBlock(Blocks.DEAD_BUSH);
         return (Task)(nearestBush.isPresent() && nearestBush.get().closerToCenterThan(EntityVer.getPos(mod.getPlayer()), 20.0)
            ? new MineAndCollectTask(Items.DEAD_BUSH, 1, new Block[]{Blocks.DEAD_BUSH}, MiningRequirement.HAND)
            : new CraftInInventoryTask(
               new RecipeTarget(
                  Items.STICK,
                  this.targetCount,
                  CraftingRecipe.newShapedRecipe("sticks", new ItemTarget[]{new ItemTarget("planks"), null, new ItemTarget("planks"), null}, 4)
               )
            ));
      }
   }

   @Override
   protected void onResourceStop(AltoClefController mod, Task interruptTask) {
      mod.getBehaviour().pop();
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return other instanceof CollectSticksTask;
   }

   @Override
   protected String toDebugStringName() {
      return "Crafting " + this.targetCount + " sticks";
   }
}
