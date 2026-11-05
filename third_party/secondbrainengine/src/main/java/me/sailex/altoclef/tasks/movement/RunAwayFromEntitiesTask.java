package me.sailex.altoclef.tasks.movement;

import me.sailex.altoclef.AltoClefController;
import me.sailex.altoclef.util.baritone.GoalRunAwayFromEntities;
import me.sailex.automatone.api.pathing.goals.Goal;
import net.minecraft.world.entity.Entity;
import java.util.List;
import java.util.function.Supplier;

public abstract class RunAwayFromEntitiesTask extends CustomBaritoneGoalTask {
   private final Supplier<List<Entity>> runAwaySupplier;
   private final double distanceToRun;
   private final boolean xz;
   private final double penalty;

   public RunAwayFromEntitiesTask(Supplier<List<Entity>> toRunAwayFrom, double distanceToRun, boolean xz, double penalty) {
      this.runAwaySupplier = toRunAwayFrom;
      this.distanceToRun = distanceToRun;
      this.xz = xz;
      this.penalty = penalty;
   }

   public RunAwayFromEntitiesTask(Supplier<List<Entity>> toRunAwayFrom, double distanceToRun, double penalty) {
      this(toRunAwayFrom, distanceToRun, false, penalty);
   }

   @Override
   protected Goal newGoal(AltoClefController mod) {
      return new GoalRunAwayStuff(mod, this.distanceToRun, this.xz);
   }

   private class GoalRunAwayStuff extends GoalRunAwayFromEntities {
      public GoalRunAwayStuff(AltoClefController mod, double distance, boolean xz) {
         super(mod, distance, xz, RunAwayFromEntitiesTask.this.penalty);
      }

      @Override
      protected List<Entity> getEntities(AltoClefController mod) {
         return RunAwayFromEntitiesTask.this.runAwaySupplier.get();
      }
   }
}
