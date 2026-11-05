// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/speedrun/beatgame/prioritytask/prioritycalculators/DistanceItemPriorityCalculator.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.speedrun.beatgame.prioritytask.prioritycalculators;

public class DistanceItemPriorityCalculator extends DistancePriorityCalculator {
   private final double multiplier;
   private final double unneededMultiplier;
   private final double unneededDistanceThreshold;

   public DistanceItemPriorityCalculator(double multiplier, double unneededMultiplier, double unneededDistanceThreshold, int minCount, int maxCount) {
      super(minCount, maxCount);
      this.multiplier = multiplier;
      this.unneededMultiplier = unneededMultiplier;
      this.unneededDistanceThreshold = unneededDistanceThreshold;
   }

   @Override
   protected double calculatePriority(double distance) {
      double priority = 1.0 / distance;
      if (this.minCountSatisfied) {
         return distance < this.unneededDistanceThreshold ? priority * this.unneededMultiplier : Double.NEGATIVE_INFINITY;
      } else {
         return priority * this.multiplier;
      }
   }
}
