// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/misc/FishTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.misc;

import com.mineagent.sbe.altoclef.tasksystem.Task;
import com.mineagent.sbe.automatone.Baritone;
import com.mineagent.sbe.automatone.process.FishingProcess;
import net.minecraft.world.item.Items;

public class FishTask extends Task {
   @Override
   protected void onStart() {
      ((Baritone)this.controller.getBaritone()).getFishingProcess().fish();
   }

   @Override
   protected Task onTick() {
      FishingProcess fishingProcess = ((Baritone)this.controller.getBaritone()).getFishingProcess();
      if (!this.controller.getSlotHandler().forceEquipItem(Items.FISHING_ROD)) {
         this.setDebugState("Can't fish without a fishing rod");
         return null;
      } else {
         if (!fishingProcess.isActive()) {
            this.onStart();
         }

         this.setDebugState("Fishing with Automatone...");
         return null;
      }
   }

   @Override
   protected void onStop(Task interruptTask) {
      FishingProcess fishingProcess = ((Baritone)this.controller.getBaritone()).getFishingProcess();
      if (fishingProcess.isActive()) {
         fishingProcess.onLostControl();
      }
   }

   @Override
   public boolean isFinished() {
      return false;
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof FishTask;
   }

   @Override
   protected String toDebugString() {
      return "Fishing";
   }
}
