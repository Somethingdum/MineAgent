// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/misc/SleepThroughNightTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.misc;

import com.mineagent.sbe.altoclef.tasksystem.Task;

public class SleepThroughNightTask extends Task {
   @Override
   protected void onStart() {
   }

   @Override
   protected Task onTick() {
      return new PlaceBedAndSetSpawnTask().stayInBed();
   }

   @Override
   protected void onStop(Task interruptTask) {
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof SleepThroughNightTask;
   }

   @Override
   protected String toDebugString() {
      return "Sleeping through the night";
   }

   @Override
   public boolean isFinished() {
      int time = (int)(this.controller.getWorld().getDayTime() % 24000L);
      return 0 <= time && time < 13000;
   }
}
