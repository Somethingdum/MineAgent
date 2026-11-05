// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/slot/ThrowCursorTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.slot;

import com.mineagent.sbe.altoclef.tasksystem.Task;
import com.mineagent.sbe.altoclef.util.slots.Slot;

public class ThrowCursorTask extends Task {
   private final Task throwTask = new ClickSlotTask(Slot.UNDEFINED);

   @Override
   protected void onStart() {
   }

   @Override
   protected Task onTick() {
      return this.throwTask;
   }

   @Override
   protected void onStop(Task interruptTask) {
   }

   @Override
   protected boolean isEqual(Task obj) {
      return obj instanceof ThrowCursorTask;
   }

   @Override
   protected String toDebugString() {
      return "Throwing Cursor";
   }

   @Override
   public boolean isFinished() {
      return this.throwTask.isFinished();
   }
}
