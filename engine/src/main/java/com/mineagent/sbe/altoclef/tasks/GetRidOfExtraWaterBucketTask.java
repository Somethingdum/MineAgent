// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/GetRidOfExtraWaterBucketTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.tasks.resources.CollectBucketLiquidTask;
import com.mineagent.sbe.altoclef.tasksystem.Task;
import com.mineagent.sbe.altoclef.util.ItemTarget;
import net.minecraft.world.item.Items;

public class GetRidOfExtraWaterBucketTask extends Task {
   private boolean needsPickup = false;

   @Override
   protected void onStart() {
   }

   @Override
   protected Task onTick() {
      AltoClefController mod = this.controller;
      if (mod.getItemStorage().getItemCount(Items.WATER_BUCKET) != 0 && !this.needsPickup) {
         return new InteractWithBlockTask(new ItemTarget(Items.WATER_BUCKET, 1), mod.getPlayer().blockPosition().below(), false);
      } else {
         this.needsPickup = true;
         return mod.getItemStorage().getItemCount(Items.WATER_BUCKET) < 1 ? new CollectBucketLiquidTask.CollectWaterBucketTask(1) : null;
      }
   }

   @Override
   public boolean isFinished() {
      return this.controller.getItemStorage().getItemCount(Items.WATER_BUCKET) == 1 && this.needsPickup;
   }

   @Override
   protected void onStop(Task interruptTask) {
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof GetRidOfExtraWaterBucketTask;
   }

   @Override
   protected String toDebugString() {
      return null;
   }
}
