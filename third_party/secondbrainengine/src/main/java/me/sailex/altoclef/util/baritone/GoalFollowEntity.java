package me.sailex.altoclef.util.baritone;

import me.sailex.automatone.api.pathing.goals.Goal;
import me.sailex.automatone.api.pathing.goals.GoalBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import me.sailex.altoclef.multiversion.EntityVer;

public class GoalFollowEntity implements Goal {
   private final Entity entity;
   private final double closeEnoughDistance;

   public GoalFollowEntity(Entity entity, double closeEnoughDistance) {
      this.entity = entity;
      this.closeEnoughDistance = closeEnoughDistance;
   }

   @Override
   public boolean isInGoal(int x, int y, int z) {
      BlockPos p = new BlockPos(x, y, z);
      return this.entity.blockPosition().equals(p)
            || p.closerToCenterThan(EntityVer.getPos(this.entity), this.closeEnoughDistance);
   }

   @Override
   public double heuristic(int x, int y, int z) {
      double xDiff = x - EntityVer.getPos(this.entity).x();
      int yDiff = y - this.entity.blockPosition().getY();
      double zDiff = z - EntityVer.getPos(this.entity).z();
      return GoalBlock.calculate(xDiff, yDiff, zDiff);
   }
}
