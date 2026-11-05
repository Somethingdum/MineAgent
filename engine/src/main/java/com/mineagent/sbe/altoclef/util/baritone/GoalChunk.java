// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/util/baritone/GoalChunk.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.util.baritone;

import com.mineagent.sbe.automatone.api.pathing.goals.Goal;
import com.mineagent.sbe.automatone.api.pathing.goals.GoalXZ;
import net.minecraft.world.level.ChunkPos;

public class GoalChunk implements Goal {
   private final ChunkPos pos;

   public GoalChunk(ChunkPos pos) {
      this.pos = pos;
   }

   @Override
   public boolean isInGoal(int x, int y, int z) {
      return this.pos.getMinBlockX() <= x && x <= this.pos.getMaxBlockX() && this.pos.getMinBlockZ() <= z && z <= this.pos.getMaxBlockZ();
   }

   @Override
   public double heuristic(int x, int y, int z) {
      double cx = (this.pos.getMinBlockX() + this.pos.getMaxBlockX()) / 2.0;
      double cz = (this.pos.getMinBlockZ() + this.pos.getMaxBlockZ()) / 2.0;
      return GoalXZ.calculate(cx - x, cz - z);
   }
}
