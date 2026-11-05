package me.sailex.altoclef.tasks.movement;

import me.sailex.altoclef.AltoClefController;
import me.sailex.altoclef.tasksystem.Task;
import me.sailex.automatone.api.pathing.goals.Goal;
import me.sailex.automatone.api.pathing.goals.GoalRunAway;
import net.minecraft.core.BlockPos;
import java.util.Arrays;

public class RunAwayFromPositionTask extends CustomBaritoneGoalTask {
   private final BlockPos[] dangerBlocks;
   private final double distance;
   private final Integer maintainY;

   public RunAwayFromPositionTask(double distance, BlockPos... toRunAwayFrom) {
      this(distance, null, toRunAwayFrom);
   }

   public RunAwayFromPositionTask(double distance, Integer maintainY, BlockPos... toRunAwayFrom) {
      this.distance = distance;
      this.dangerBlocks = toRunAwayFrom;
      this.maintainY = maintainY;
   }

   @Override
   protected Goal newGoal(AltoClefController mod) {
      return new GoalRunAway(this.distance, this.maintainY, this.dangerBlocks);
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof RunAwayFromPositionTask task ? Arrays.equals((Object[])task.dangerBlocks, (Object[])this.dangerBlocks) : false;
   }

   @Override
   protected String toDebugString() {
      return "Running away from " + Arrays.toString((Object[])this.dangerBlocks);
   }
}
