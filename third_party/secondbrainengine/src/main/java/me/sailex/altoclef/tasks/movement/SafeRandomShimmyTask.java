package me.sailex.altoclef.tasks.movement;

import me.sailex.altoclef.Debug;
import me.sailex.altoclef.tasksystem.Task;
import me.sailex.altoclef.util.helpers.LookHelper;
import me.sailex.altoclef.util.time.TimerGame;
import me.sailex.automatone.api.IBaritone;
import me.sailex.automatone.api.utils.input.Input;

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
