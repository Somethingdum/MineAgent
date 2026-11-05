package me.sailex.altoclef.util.baritone;

import me.sailex.altoclef.AltoClefController;
import me.sailex.altoclef.multiversion.EntityVer;
import me.sailex.altoclef.util.helpers.BaritoneHelper;
import me.sailex.automatone.api.pathing.goals.Goal;
import me.sailex.automatone.api.pathing.goals.GoalXZ;
import me.sailex.automatone.api.pathing.goals.GoalYLevel;
import net.minecraft.world.entity.Entity;
import java.util.List;

public abstract class GoalRunAwayFromEntities implements Goal {
   private final AltoClefController mod;
   private final double distance;
   private final boolean xzOnly;
   private final double penaltyFactor;

   public GoalRunAwayFromEntities(AltoClefController mod, double distance, boolean xzOnly, double penaltyFactor) {
      this.mod = mod;
      this.distance = distance;
      this.xzOnly = xzOnly;
      this.penaltyFactor = penaltyFactor;
   }

   @Override
   public boolean isInGoal(int x, int y, int z) {
      List<Entity> entities = this.getEntities(this.mod);
      synchronized (BaritoneHelper.MINECRAFT_LOCK) {
         if (!entities.isEmpty()) {
            for (Entity entity : entities) {
               if (entity != null && entity.isAlive()) {
                  double sqDistance;
                  if (this.xzOnly) {
                     sqDistance = EntityVer.getPos(entity).subtract(x, y, z).multiply(1.0, 0.0, 1.0).lengthSqr();
                  } else {
                     sqDistance = entity.distanceToSqr(x, y, z);
                  }

                  if (sqDistance < this.distance * this.distance) {
                     return false;
                  }
               }
            }
         }

         return true;
      }
   }

   @Override
   public double heuristic(int x, int y, int z) {
      double costSum = 0.0;
      List<Entity> entities = this.getEntities(this.mod);
      synchronized (BaritoneHelper.MINECRAFT_LOCK) {
         int max = 10;
         int counter = 0;
         if (!entities.isEmpty()) {
            for (Entity entity : entities) {
               counter++;
               if (entity != null && entity.isAlive()) {
                  double cost = this.getCostOfEntity(entity, x, y, z);
                  if (cost != 0.0) {
                     costSum += 1.0 / cost;
                  } else {
                     costSum += 1000.0;
                  }

                  if (counter >= max) {
                     break;
                  }
               }
            }
         }

         if (counter > 0) {
            costSum /= counter;
         }

         return costSum * this.penaltyFactor;
      }
   }

   protected abstract List<Entity> getEntities(AltoClefController var1);

   protected double getCostOfEntity(Entity entity, int x, int y, int z) {
      double heuristic = 0.0;
      if (!this.xzOnly) {
         heuristic += GoalYLevel.calculate(entity.blockPosition().getY(), y);
      }

      return heuristic + GoalXZ.calculate(entity.blockPosition().getX() - x, entity.blockPosition().getZ() - z);
   }
}
