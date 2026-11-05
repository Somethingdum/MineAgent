// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/resources/CollectAmethystBlockTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
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
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.util.function.Predicate;

public class CollectAmethystBlockTask extends ResourceTask {
   private final int count;

   public CollectAmethystBlockTask(int targetCount) {
      super(Items.AMETHYST_BLOCK, targetCount);
      this.count = targetCount;
   }

   @Override
   protected boolean shouldAvoidPickingUp(AltoClefController mod) {
      return false;
   }

   @Override
   protected void onResourceStart(AltoClefController mod) {
      mod.getBehaviour().push();
      mod.getBehaviour().avoidBlockBreaking((Predicate<BlockPos>)(blockPos -> {
         BlockState s = mod.getWorld().getBlockState(blockPos);
         return s.getBlock() == Blocks.BUDDING_AMETHYST;
      }));
   }

   @Override
   protected Task onResourceTick(AltoClefController mod) {
      if (mod.getItemStorage().getItemCount(Items.AMETHYST_SHARD) >= 4) {
         int target = mod.getItemStorage().getItemCount(Items.AMETHYST_BLOCK) + 1;
         ItemTarget s = new ItemTarget(Items.AMETHYST_SHARD, 1);
         return new CraftInInventoryTask(
            new RecipeTarget(Items.AMETHYST_BLOCK, target, CraftingRecipe.newShapedRecipe("amethyst_block", new ItemTarget[]{s, s, s, s}, 1))
         );
      } else {
         return new MineAndCollectTask(
               new ItemTarget(Items.AMETHYST_BLOCK, Items.AMETHYST_SHARD), new Block[]{Blocks.AMETHYST_BLOCK, Blocks.AMETHYST_CLUSTER}, MiningRequirement.WOOD
            )
            .forceDimension(Dimension.OVERWORLD);
      }
   }

   @Override
   protected void onResourceStop(AltoClefController mod, Task interruptTask) {
      mod.getBehaviour().pop();
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return other instanceof CollectAmethystBlockTask;
   }

   @Override
   protected String toDebugStringName() {
      return "Collecting " + this.count + " Amethyst Blocks.";
   }
}
