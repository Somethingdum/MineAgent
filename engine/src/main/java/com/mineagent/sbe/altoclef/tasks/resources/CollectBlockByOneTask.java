// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/resources/CollectBlockByOneTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.resources;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.tasks.ResourceTask;
import com.mineagent.sbe.altoclef.tasksystem.Task;
import com.mineagent.sbe.altoclef.util.MiningRequirement;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import java.util.Arrays;

public class CollectBlockByOneTask extends ResourceTask {
   private final Item item;
   private final Block[] blocks;
   private final MiningRequirement requirement;
   private final int count;

   public CollectBlockByOneTask(Item item, Block[] blocks, MiningRequirement requirement, int targetCount) {
      super(item, targetCount);
      this.item = item;
      this.blocks = blocks;
      this.requirement = requirement;
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
      return new MineAndCollectTask(this.item, this.count, this.blocks, this.requirement);
   }

   @Override
   protected void onResourceStop(AltoClefController mod, Task interruptTask) {
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return !(other instanceof CollectBlockByOneTask task)
         ? false
         : task.count == this.count
            && task.item.equals(this.item)
            && Arrays.stream(task.blocks).allMatch(block -> Arrays.stream(this.blocks).toList().contains(block));
   }

   @Override
   protected String toDebugStringName() {
      return "Collect " + this.item;
   }

   public static class CollectCobbledDeepslateTask extends CollectBlockByOneTask {
      public CollectCobbledDeepslateTask(int targetCount) {
         super(Items.COBBLED_DEEPSLATE, new Block[]{Blocks.DEEPSLATE, Blocks.COBBLED_DEEPSLATE}, MiningRequirement.WOOD, targetCount);
      }
   }

   public static class CollectCobblestoneTask extends CollectBlockByOneTask {
      public CollectCobblestoneTask(int targetCount) {
         super(Items.COBBLESTONE, new Block[]{Blocks.STONE, Blocks.COBBLESTONE}, MiningRequirement.WOOD, targetCount);
      }
   }

   public static class CollectEndStoneTask extends CollectBlockByOneTask {
      public CollectEndStoneTask(int targetCount) {
         super(Items.END_STONE, new Block[]{Blocks.END_STONE}, MiningRequirement.WOOD, targetCount);
      }
   }
}
