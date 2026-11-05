// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/util/baritone/GoalFollowEntity.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.util.baritone;

import com.mineagent.sbe.automatone.api.pathing.goals.Goal;
import com.mineagent.sbe.automatone.api.pathing.goals.GoalBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import com.mineagent.sbe.altoclef.multiversion.EntityVer;

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
