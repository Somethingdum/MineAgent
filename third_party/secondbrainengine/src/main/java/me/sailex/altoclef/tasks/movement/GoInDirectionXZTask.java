package me.sailex.altoclef.tasks.movement;

import me.sailex.altoclef.AltoClefController;
import me.sailex.altoclef.Debug;
import me.sailex.altoclef.tasksystem.Task;
import me.sailex.altoclef.util.baritone.GoalDirectionXZ;
import me.sailex.automatone.api.pathing.goals.Goal;
import net.minecraft.world.phys.Vec3;

public class GoInDirectionXZTask extends CustomBaritoneGoalTask {
   private final Vec3 origin;
   private final Vec3 delta;
   private final double sidePenalty;

   public GoInDirectionXZTask(Vec3 origin, Vec3 delta, double sidePenalty) {
      this.origin = origin;
      this.delta = delta;
      this.sidePenalty = sidePenalty;
   }

   private static boolean closeEnough(Vec3 a, Vec3 b) {
      return a.distanceToSqr(b) < 0.001;
   }

   @Override
   protected Goal newGoal(AltoClefController mod) {
      try {
         return new GoalDirectionXZ(this.origin, this.delta, this.sidePenalty);
      } catch (Exception var3) {
         Debug.logMessage("Invalid goal direction XZ (probably zero distance)");
         return null;
      }
   }

   @Override
   protected boolean isEqual(Task other) {
      return !(other instanceof GoInDirectionXZTask task) ? false : closeEnough(task.origin, this.origin) && closeEnough(task.delta, this.delta);
   }

   @Override
   protected String toDebugString() {
      return "Going in direction: <" + this.origin.x + "," + this.origin.z + "> direction: <" + this.delta.x + "," + this.delta.z + ">";
   }
}
