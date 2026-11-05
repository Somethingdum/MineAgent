// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/examples/ExampleTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.examples;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.TaskCatalogue;
import com.mineagent.sbe.altoclef.tasks.construction.PlaceBlockTask;
import com.mineagent.sbe.altoclef.tasks.movement.GetToBlockTask;
import com.mineagent.sbe.altoclef.tasksystem.Task;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

public class ExampleTask extends Task {
   private final int numberOfStonePickaxesToGrab;
   private final BlockPos whereToPlaceCobblestone;

   public ExampleTask(int numberOfStonePickaxesToGrab, BlockPos whereToPlaceCobblestone) {
      this.numberOfStonePickaxesToGrab = numberOfStonePickaxesToGrab;
      this.whereToPlaceCobblestone = whereToPlaceCobblestone;
   }

   @Override
   protected void onStart() {
      AltoClefController mod = this.controller;
      mod.getBehaviour().push();
      mod.getBehaviour().addProtectedItems(Items.COBBLESTONE);
   }

   @Override
   protected Task onTick() {
      AltoClefController mod = this.controller;
      if (mod.getItemStorage().getItemCount(Items.STONE_PICKAXE) < this.numberOfStonePickaxesToGrab) {
         return TaskCatalogue.getItemTask(Items.STONE_PICKAXE, this.numberOfStonePickaxesToGrab);
      } else if (!mod.getItemStorage().hasItem(Items.COBBLESTONE)) {
         return TaskCatalogue.getItemTask(Items.COBBLESTONE, 1);
      } else if (mod.getChunkTracker().isChunkLoaded(this.whereToPlaceCobblestone)) {
         return mod.getWorld().getBlockState(this.whereToPlaceCobblestone).getBlock() != Blocks.COBBLESTONE
            ? new PlaceBlockTask(this.whereToPlaceCobblestone, Blocks.COBBLESTONE)
            : null;
      } else {
         return new GetToBlockTask(this.whereToPlaceCobblestone);
      }
   }

   @Override
   protected void onStop(Task interruptTask) {
      this.controller.getBehaviour().pop();
   }

   @Override
   public boolean isFinished() {
      AltoClefController mod = this.controller;
      return mod.getItemStorage().getItemCount(Items.STONE_PICKAXE) >= this.numberOfStonePickaxesToGrab
         && mod.getWorld().getBlockState(this.whereToPlaceCobblestone).getBlock() == Blocks.COBBLESTONE;
   }

   @Override
   protected boolean isEqual(Task other) {
      return !(other instanceof ExampleTask task)
         ? false
         : task.numberOfStonePickaxesToGrab == this.numberOfStonePickaxesToGrab && task.whereToPlaceCobblestone.equals(this.whereToPlaceCobblestone);
   }

   @Override
   protected String toDebugString() {
      return "Boofin";
   }
}
