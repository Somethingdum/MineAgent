// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/movement/SafeRandomShimmyTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.movement;

import com.mineagent.sbe.altoclef.Debug;
import com.mineagent.sbe.altoclef.tasksystem.Task;
import com.mineagent.sbe.altoclef.util.helpers.LookHelper;
import com.mineagent.sbe.altoclef.util.time.TimerGame;
import com.mineagent.sbe.automatone.api.IBaritone;
import com.mineagent.sbe.automatone.api.utils.input.Input;

public class SafeRandomShimmyTask extends Task {
   private final TimerGame lookTimer;

   public SafeRandomShimmyTask(float randomLookInterval) {
      this.lookTimer = new TimerGame(randomLookInterval);
   }

   public SafeRandomShimmyTask() {
      this(5.0F);
   }

   @Override
   protected void onStart() {
      this.lookTimer.reset();
   }

   @Override
   protected Task onTick() {
      if (this.lookTimer.elapsed()) {
         Debug.logMessage("Random Orientation");
         this.lookTimer.reset();
         LookHelper.randomOrientation(this.controller);
      }

      IBaritone baritone = this.controller.getBaritone();
      baritone.getInputOverrideHandler().setInputForceState(Input.SNEAK, true);
      baritone.getInputOverrideHandler().setInputForceState(Input.MOVE_FORWARD, true);
      baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, true);
      return null;
   }

   @Override
   protected void onStop(Task interruptTask) {
      IBaritone baritone = this.controller.getBaritone();
      baritone.getInputOverrideHandler().setInputForceState(Input.MOVE_FORWARD, false);
      baritone.getInputOverrideHandler().setInputForceState(Input.SNEAK, false);
      baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, false);
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof SafeRandomShimmyTask;
   }

   @Override
   protected String toDebugString() {
      return "Shimmying";
   }
}
