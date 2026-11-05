// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/util/baritone/GoalDirectionXZ.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.util.baritone;

import com.mineagent.sbe.automatone.api.BaritoneAPI;
import com.mineagent.sbe.automatone.api.pathing.goals.Goal;
import net.minecraft.world.phys.Vec3;

public class GoalDirectionXZ implements Goal {
   private final double originx;
   private final double originz;
   private final double dirx;
   private final double dirz;
   private final double sidePenalty;

   public GoalDirectionXZ(Vec3 origin, Vec3 offset, double sidePenalty) {
      this.originx = origin.x();
      this.originz = origin.z();
      offset = offset.multiply(1.0, 0.0, 1.0);
      offset = offset.normalize();
      this.dirx = offset.x;
      this.dirz = offset.z;
      if (this.dirx == 0.0 && this.dirz == 0.0) {
         throw new IllegalArgumentException(String.valueOf(offset));
      } else {
         this.sidePenalty = sidePenalty;
      }
   }

   private static String maybeCensor(double value) {
      return BaritoneAPI.getGlobalSettings().censorCoordinates.get() ? "<censored>" : Double.toString(value);
   }

   @Override
   public boolean isInGoal(int x, int y, int z) {
      return false;
   }

   @Override
   public double heuristic(int x, int y, int z) {
      double dx = x - this.originx;
      double dz = z - this.originz;
      double correctDistance = dx * this.dirx + dz * this.dirz;
      double px = this.dirx * correctDistance;
      double pz = this.dirz * correctDistance;
      double perpendicularDistance = (dx - px) * (dx - px) + (dz - pz) * (dz - pz);
      return -correctDistance * BaritoneAPI.getGlobalSettings().costHeuristic.get() + perpendicularDistance * this.sidePenalty;
   }

   @Override
   public String toString() {
      return String.format(
         "GoalDirection{x=%s, z=%s, dx=%s, dz=%s}", maybeCensor(this.originx), maybeCensor(this.originz), maybeCensor(this.dirx), maybeCensor(this.dirz)
      );
   }
}
