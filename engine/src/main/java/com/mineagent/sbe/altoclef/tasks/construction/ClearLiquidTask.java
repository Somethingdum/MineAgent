// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/construction/ClearLiquidTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.construction;

import com.mineagent.sbe.altoclef.tasks.InteractWithBlockTask;
import com.mineagent.sbe.altoclef.tasksystem.Task;
import com.mineagent.sbe.altoclef.util.ItemTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext.Fluid;

public class ClearLiquidTask extends Task {
   private final BlockPos liquidPos;

   public ClearLiquidTask(BlockPos liquidPos) {
      this.liquidPos = liquidPos;
   }

   @Override
   protected void onStart() {
   }

   @Override
   protected Task onTick() {
      if (this.controller.getItemStorage().hasItem(Items.BUCKET)) {
         this.controller.getBehaviour().setRayTracingFluidHandling(Fluid.SOURCE_ONLY);
         return new InteractWithBlockTask(new ItemTarget(Items.BUCKET, 1), this.liquidPos, false);
      } else {
         return new PlaceStructureBlockTask(this.liquidPos);
      }
   }

   @Override
   protected void onStop(Task interruptTask) {
   }

   @Override
   public boolean isFinished() {
      return this.controller.getChunkTracker().isChunkLoaded(this.liquidPos)
         ? this.controller.getWorld().getBlockState(this.liquidPos).getFluidState().isEmpty()
         : false;
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof ClearLiquidTask task ? task.liquidPos.equals(this.liquidPos) : false;
   }

   @Override
   protected String toDebugString() {
      return "Clear liquid at " + this.liquidPos;
   }
}
