// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/movement/GetCloseToBlockTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.movement;

import com.mineagent.sbe.altoclef.tasksystem.Task;
import net.minecraft.core.BlockPos;

public class GetCloseToBlockTask extends Task {
   private final BlockPos toApproach;
   private int currentRange;

   public GetCloseToBlockTask(BlockPos toApproach) {
      this.toApproach = toApproach;
   }

   @Override
   protected void onStart() {
      this.currentRange = Integer.MAX_VALUE;
   }

   @Override
   protected Task onTick() {
      if (this.inRange()) {
         this.currentRange = this.getCurrentDistance() - 1;
      }

      return new GetWithinRangeOfBlockTask(this.toApproach, this.currentRange);
   }

   @Override
   protected void onStop(Task interruptTask) {
   }

   private int getCurrentDistance() {
      return (int)Math.sqrt(this.controller.getPlayer().blockPosition().distSqr(this.toApproach));
   }

   private boolean inRange() {
      return this.controller.getPlayer().blockPosition().distSqr(this.toApproach) <= this.currentRange * this.currentRange;
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof GetCloseToBlockTask task ? task.toApproach.equals(this.toApproach) : false;
   }

   @Override
   protected String toDebugString() {
      return "Approaching " + this.toApproach.toShortString();
   }
}
