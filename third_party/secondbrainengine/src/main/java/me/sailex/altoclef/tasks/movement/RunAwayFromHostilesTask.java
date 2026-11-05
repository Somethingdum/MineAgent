package me.sailex.altoclef.tasks.movement;

import me.sailex.altoclef.AltoClefController;
import me.sailex.altoclef.tasksystem.Task;
import me.sailex.altoclef.util.baritone.GoalRunAwayFromEntities;
import me.sailex.altoclef.util.helpers.BaritoneHelper;
import me.sailex.automatone.api.pathing.goals.Goal;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Skeleton;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RunAwayFromHostilesTask extends CustomBaritoneGoalTask {
   private final double distanceToRun;
   private final boolean includeSkeletons;

   public RunAwayFromHostilesTask(double distance, boolean includeSkeletons) {
      this.distanceToRun = distance;
      this.includeSkeletons = includeSkeletons;
   }

   public RunAwayFromHostilesTask(double distance) {
      this(distance, false);
   }

   @Override
   protected Goal newGoal(AltoClefController mod) {
      mod.getBaritone().getPathingBehavior().forceCancel();
      return new GoalRunAwayFromHostiles(mod, this.distanceToRun);
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof RunAwayFromHostilesTask task ? Math.abs(task.distanceToRun - this.distanceToRun) < 1.0 : false;
   }

   @Override
   protected String toDebugString() {
      return "NIGERUNDAYOO, SUMOOKEYY! distance=" + this.distanceToRun + ", skeletons=" + this.includeSkeletons;
   }

   private class GoalRunAwayFromHostiles extends GoalRunAwayFromEntities {
      public GoalRunAwayFromHostiles(AltoClefController mod, double distance) {
         super(mod, distance, false, 0.8);
      }

      @Override
      protected List<Entity> getEntities(AltoClefController mod) {
         Stream<LivingEntity> stream = mod.getEntityTracker().getHostiles().stream();
         synchronized (BaritoneHelper.MINECRAFT_LOCK) {
            if (!RunAwayFromHostilesTask.this.includeSkeletons) {
               stream = stream.filter(hostile -> !(hostile instanceof Skeleton));
            }

            return stream.collect(Collectors.toList());
         }
      }
   }
}
