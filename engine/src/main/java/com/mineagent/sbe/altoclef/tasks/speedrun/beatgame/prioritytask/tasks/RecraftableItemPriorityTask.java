// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/speedrun/beatgame/prioritytask/tasks/RecraftableItemPriorityTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.speedrun.beatgame.prioritytask.tasks;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.util.RecipeTarget;

import java.util.function.Function;

public class RecraftableItemPriorityTask extends CraftItemPriorityTask {
   private final double recraftPriority;

   public RecraftableItemPriorityTask(double priority, double recraftPriority, RecipeTarget toCraft, Function<AltoClefController, Boolean> canCall) {
      super(priority, toCraft, canCall);
      this.recraftPriority = recraftPriority;
   }

   @Override
   protected double getPriority(AltoClefController mod) {
      return this.isSatisfied() ? this.recraftPriority : super.getPriority(mod);
   }
}
