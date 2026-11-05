// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/speedrun/beatgame/prioritytask/prioritycalculators/StaticItemPriorityCalculator.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.speedrun.beatgame.prioritytask.prioritycalculators;

public class StaticItemPriorityCalculator extends ItemPriorityCalculator {
   private final int priority;

   public static StaticItemPriorityCalculator of(int priority) {
      return new StaticItemPriorityCalculator(priority, 1, 1);
   }

   public StaticItemPriorityCalculator(int priority, int minCount, int maxCount) {
      super(minCount, maxCount);
      this.priority = priority;
   }

   @Override
   double calculatePriority(int count) {
      return this.priority;
   }
}
