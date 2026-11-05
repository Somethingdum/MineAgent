// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/misc/FarmTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.misc;

import com.mineagent.sbe.altoclef.tasksystem.Task;
import com.mineagent.sbe.automatone.api.process.IFarmProcess;
import net.minecraft.core.BlockPos;
import java.util.Objects;

public class FarmTask extends Task {
   private final Integer range;
   private final BlockPos center;

   public FarmTask(Integer range, BlockPos center) {
      this.range = range;
      this.center = center;
   }

   public FarmTask() {
      this(null, null);
   }

   @Override
   protected void onStart() {
      IFarmProcess farmProcess = this.controller.getBaritone().getFarmProcess();
      if (this.range != null && this.center != null) {
         farmProcess.farm(this.range, this.center);
      } else if (this.range != null) {
         farmProcess.farm(this.range);
      } else {
         farmProcess.farm();
      }
   }

   @Override
   protected Task onTick() {
      IFarmProcess farmProcess = this.controller.getBaritone().getFarmProcess();
      if (!farmProcess.isActive()) {
         this.onStart();
      }

      this.setDebugState("Farming with Automatone...");
      return null;
   }

   @Override
   protected void onStop(Task interruptTask) {
      IFarmProcess farmProcess = this.controller.getBaritone().getFarmProcess();
      if (farmProcess.isActive()) {
         farmProcess.onLostControl();
      }
   }

   @Override
   public boolean isFinished() {
      return false;
   }

   @Override
   protected boolean isEqual(Task other) {
      return !(other instanceof FarmTask task) ? false : Objects.equals(task.range, this.range) && Objects.equals(task.center, this.center);
   }

   @Override
   protected String toDebugString() {
      return this.range != null && this.center != null ? "Farming in range " + this.range + " around " + this.center.toShortString() : "Farming nearby";
   }
}
