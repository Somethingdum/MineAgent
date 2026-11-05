// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/resources/CollectWheatSeedsTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.resources;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.tasks.ResourceTask;
import com.mineagent.sbe.altoclef.tasksystem.Task;
import com.mineagent.sbe.altoclef.util.MiningRequirement;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class CollectWheatSeedsTask extends ResourceTask {
   private final int count;

   public CollectWheatSeedsTask(int count) {
      super(Items.WHEAT_SEEDS, count);
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
      return (Task)(mod.getBlockScanner().anyFound(Blocks.WHEAT)
         ? new CollectCropTask(Items.AIR, 999, Blocks.WHEAT, Items.WHEAT_SEEDS)
         : new MineAndCollectTask(Items.WHEAT_SEEDS, this.count, new Block[]{ Blocks.SHORT_GRASS , Blocks.TALL_GRASS}, MiningRequirement.HAND));
   }

   @Override
   protected void onResourceStop(AltoClefController mod, Task interruptTask) {
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return other instanceof CollectWheatSeedsTask;
   }

   @Override
   protected String toDebugStringName() {
      return "Collecting " + this.count + " wheat seeds.";
   }
}
