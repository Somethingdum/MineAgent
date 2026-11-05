// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/util/baritone/GoalBlockSide.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.util.baritone;

import com.mineagent.sbe.automatone.api.pathing.goals.Goal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.Vec3;

public class GoalBlockSide implements Goal {
   private final BlockPos block;
   private final Direction direction;
   private final double buffer;

   public GoalBlockSide(BlockPos block, Direction direction, double bufferDistance) {
      this.block = block;
      this.direction = direction;
      this.buffer = bufferDistance;
   }

   public GoalBlockSide(BlockPos block, Direction direction) {
      this(block, direction, 1.0);
   }

   @Override
   public boolean isInGoal(int x, int y, int z) {
      return this.getDistanceInRightDirection(x, y, z) > 0.0;
   }

   @Override
   public double heuristic(int x, int y, int z) {
      return Math.min(this.getDistanceInRightDirection(x, y, z), 0.0);
   }

   private double getDistanceInRightDirection(int x, int y, int z) {
      Vec3 delta = new Vec3(x, y, z).subtract(this.block.getX(), this.block.getY(), this.block.getZ());
      Vec3i dir = this.direction.getNormal();
      double dot = new Vec3(dir.getX(), dir.getY(), dir.getZ()).dot(delta);
      return dot - this.buffer;
   }
}
