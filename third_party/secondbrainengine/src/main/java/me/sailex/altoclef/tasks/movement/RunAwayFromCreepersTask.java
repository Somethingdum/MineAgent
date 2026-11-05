package me.sailex.altoclef.tasks.movement;

import me.sailex.altoclef.AltoClefController;
import me.sailex.altoclef.chains.MobDefenseChain;
import me.sailex.altoclef.tasksystem.Task;
import me.sailex.altoclef.util.baritone.GoalRunAwayFromEntities;
import me.sailex.automatone.api.pathing.goals.Goal;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

public class RunAwayFromCreepersTask extends CustomBaritoneGoalTask {
   private final double distanceToRun;

   public RunAwayFromCreepersTask(double distance) {
      this.distanceToRun = distance;
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof RunAwayFromCreepersTask task ? !(Math.abs(task.distanceToRun - this.distanceToRun) > 1.0) : false;
   }

   @Override
   protected String toDebugString() {
      return "Run " + this.distanceToRun + " blocks away from creepers";
   }

   @Override
   protected Goal newGoal(AltoClefController mod) {
      mod.getBaritone().getPathingBehavior().forceCancel();
      return new GoalRunAwayFromCreepers(mod, this.distanceToRun);
   }

   private static class GoalRunAwayFromCreepers extends GoalRunAwayFromEntities {
      public GoalRunAwayFromCreepers(AltoClefController mod, double distance) {
         super(mod, distance, false, 10.0);
      }

      @Override
      protected List<Entity> getEntities(AltoClefController mod) {
         return new ArrayList<>(mod.getEntityTracker().getTrackedEntities(Creeper.class));
      }

      @Override
      protected double getCostOfEntity(Entity entity, int x, int y, int z) {
         return entity instanceof Creeper
            ? MobDefenseChain.getCreeperSafety(new Vec3(x + 0.5, y + 0.5, z + 0.5), (Creeper)entity)
            : super.getCostOfEntity(entity, x, y, z);
      }
   }
}
