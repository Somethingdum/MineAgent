// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/speedrun/beatgame/prioritytask/prioritycalculators/DistancePriorityCalculator.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.speedrun.beatgame.prioritytask.prioritycalculators;

public abstract class DistancePriorityCalculator {
   public final int minCount;
   public final int maxCount;
   protected boolean minCountSatisfied = false;
   protected boolean maxCountSatisfied = false;

   public DistancePriorityCalculator(int minCount, int maxCount) {
      this.minCount = minCount;
      this.maxCount = maxCount;
   }

   public void update(int count) {
      if (count >= this.minCount) {
         this.minCountSatisfied = true;
      }

      if (count >= this.maxCount) {
         this.maxCountSatisfied = true;
      }
   }

   public double getPriority(double distance) {
      return !Double.isInfinite(distance) && distance != 2.147483647E9 && !this.maxCountSatisfied ? this.calculatePriority(distance) : Double.NEGATIVE_INFINITY;
   }

   abstract double calculatePriority(double var1);
}
