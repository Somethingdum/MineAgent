package me.sailex.altoclef.tasks.movement;

import me.sailex.altoclef.AltoClefController;
import me.sailex.altoclef.tasksystem.Task;
import me.sailex.altoclef.util.Dimension;
import me.sailex.altoclef.util.helpers.WorldHelper;
import me.sailex.automatone.api.pathing.goals.Goal;
import me.sailex.automatone.api.pathing.goals.GoalYLevel;

public class GetToYTask extends CustomBaritoneGoalTask {
   private final int yLevel;
   private final Dimension dimension;

   public GetToYTask(int ylevel, Dimension dimension) {
      this.yLevel = ylevel;
      this.dimension = dimension;
   }

   public GetToYTask(int ylevel) {
      this(ylevel, null);
   }

   @Override
   protected Task onTick() {
      return (Task)(this.dimension != null && WorldHelper.getCurrentDimension(this.controller) != this.dimension
         ? new DefaultGoToDimensionTask(this.dimension)
         : super.onTick());
   }

   @Override
   protected Goal newGoal(AltoClefController mod) {
      return new GoalYLevel(this.yLevel);
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof GetToYTask task ? task.yLevel == this.yLevel : false;
   }

   @Override
   protected String toDebugString() {
      return "Going to y=" + this.yLevel + (this.dimension != null ? "in dimension" + this.dimension : "");
   }
}
