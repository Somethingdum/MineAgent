// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/movement/IdleTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.movement;

import com.mineagent.sbe.altoclef.Playground;
import com.mineagent.sbe.altoclef.tasksystem.Task;

public class IdleTask extends Task {
   @Override
   protected void onStart() {
   }

   @Override
   protected Task onTick() {
      
      Playground.IDLE_TEST_TICK_FUNCTION(this.controller);
      return null;
   }

   @Override
   protected void onStop(Task interruptTask) {
   }

   @Override
   public boolean isFinished() {
      return false;
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof IdleTask;
   }

   @Override
   protected String toDebugString() {
      return "Idle";
   }
}
