// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/util/baritone/GoalAnd.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.util.baritone;

import com.mineagent.sbe.automatone.api.pathing.goals.Goal;

import java.util.Arrays;

public class GoalAnd implements Goal {
   private final Goal[] goals;

   public GoalAnd(Goal... goals) {
      this.goals = goals;
   }

   @Override
   public boolean isInGoal(int x, int y, int z) {
      Goal[] var4 = this.goals;
      int var5 = var4.length;

      for (Goal goal : var4) {
         if (!goal.isInGoal(x, y, z)) {
            return false;
         }
      }

      return true;
   }

   @Override
   public double heuristic(int x, int y, int z) {
      double sum = 0.0;
      if (this.goals != null) {
         for (Goal goal : this.goals) {
            sum += goal.heuristic(x, y, z);
         }
      }

      return sum;
   }

   @Override
   public String toString() {
      return "GoalAnd" + Arrays.toString((Object[])this.goals);
   }

   public Goal[] goals() {
      return this.goals;
   }
}
