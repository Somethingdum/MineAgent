// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/speedrun/beatgame/prioritytask/tasks/PriorityTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.speedrun.beatgame.prioritytask.tasks;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.tasksystem.Task;

import java.util.function.Function;

public abstract class PriorityTask {
   private final Function<AltoClefController, Boolean> canCall;
   private final boolean shouldForce;
   private final boolean canCache;
   public final boolean bypassForceCooldown;

   public PriorityTask(Function<AltoClefController, Boolean> canCall, boolean shouldForce, boolean canCache, boolean bypassForceCooldown) {
      this.canCall = canCall;
      this.shouldForce = shouldForce;
      this.canCache = canCache;
      this.bypassForceCooldown = bypassForceCooldown;
   }

   public final double calculatePriority(AltoClefController mod) {
      return !this.canCall.apply(mod) ? Double.NEGATIVE_INFINITY : this.getPriority(mod);
   }

   @Override
   public String toString() {
      return this.getDebugString();
   }

   public abstract Task getTask(AltoClefController var1);

   public abstract String getDebugString();

   protected abstract double getPriority(AltoClefController var1);

   public boolean needCraftingOnStart(AltoClefController mod) {
      return false;
   }

   public boolean shouldForce() {
      return this.shouldForce;
   }

   public boolean canCache() {
      return this.canCache;
   }
}
