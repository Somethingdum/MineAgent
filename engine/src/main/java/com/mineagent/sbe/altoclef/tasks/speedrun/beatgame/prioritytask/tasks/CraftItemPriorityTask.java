// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/speedrun/beatgame/prioritytask/tasks/CraftItemPriorityTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.speedrun.beatgame.prioritytask.tasks;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.Debug;
import com.mineagent.sbe.altoclef.tasks.CraftInInventoryTask;
import com.mineagent.sbe.altoclef.tasks.container.CraftInTableTask;
import com.mineagent.sbe.altoclef.tasks.speedrun.beatgame.BeatMinecraftTask;
import com.mineagent.sbe.altoclef.tasksystem.Task;
import com.mineagent.sbe.altoclef.util.RecipeTarget;
import com.mineagent.sbe.altoclef.util.helpers.CraftingHelper;

import java.util.function.Function;

public class CraftItemPriorityTask extends PriorityTask {
   public final double priority;
   public final RecipeTarget recipeTarget;
   private boolean satisfied = false;

   public CraftItemPriorityTask(double priority, RecipeTarget toCraft) {
      this(priority, toCraft, mod -> true);
   }

   public CraftItemPriorityTask(double priority, RecipeTarget toCraft, Function<AltoClefController, Boolean> canCall) {
      this(priority, toCraft, canCall, false, true, true);
   }

   public CraftItemPriorityTask(double priority, RecipeTarget toCraft, boolean shouldForce, boolean canCache, boolean bypassForceCooldown) {
      this(priority, toCraft, mod -> true, shouldForce, canCache, bypassForceCooldown);
   }

   public CraftItemPriorityTask(
      double priority, RecipeTarget toCraft, Function<AltoClefController, Boolean> canCall, boolean shouldForce, boolean canCache, boolean bypassForceCooldown
   ) {
      super(canCall, shouldForce, canCache, bypassForceCooldown);
      this.priority = priority;
      this.recipeTarget = toCraft;
   }

   @Override
   public Task getTask(AltoClefController mod) {
      return (Task)(this.recipeTarget.getRecipe().isBig() ? new CraftInTableTask(this.recipeTarget) : new CraftInInventoryTask(this.recipeTarget));
   }

   @Override
   public String getDebugString() {
      return "Crafting " + this.recipeTarget;
   }

   @Override
   protected double getPriority(AltoClefController mod) {
      if (BeatMinecraftTask.hasItem(mod, this.recipeTarget.getOutputItem())) {
         Debug.logInternal("THIS IS SATISFIED " + this.recipeTarget.getOutputItem());
         this.satisfied = true;
      }

      Debug.logInternal("NOT SATISFIED");
      return this.satisfied ? Double.NEGATIVE_INFINITY : this.priority;
   }

   @Override
   public boolean needCraftingOnStart(AltoClefController mod) {
      return CraftingHelper.canCraftItemNow(mod, this.recipeTarget.getOutputItem());
   }

   public boolean isSatisfied() {
      return this.satisfied;
   }
}
