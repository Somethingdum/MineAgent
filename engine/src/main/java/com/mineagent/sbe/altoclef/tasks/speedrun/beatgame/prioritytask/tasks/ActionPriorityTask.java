// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/speedrun/beatgame/prioritytask/tasks/ActionPriorityTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.speedrun.beatgame.prioritytask.tasks;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.tasks.speedrun.beatgame.prioritytask.prioritycalculators.PriorityCalculator;
import com.mineagent.sbe.altoclef.tasksystem.Task;
import com.mineagent.sbe.altoclef.util.Pair;

import java.util.function.Function;

public class ActionPriorityTask extends PriorityTask {
   private final TaskAndPriorityProvider taskAndPriorityProvider;
   private Task lastTask = null;

   public ActionPriorityTask(TaskProvider taskProvider, PriorityCalculator priorityCalculator) {
      this(taskProvider, priorityCalculator, a -> true, false, true, false);
   }

   public ActionPriorityTask(TaskProvider taskProvider, PriorityCalculator priorityCalculator, Function<AltoClefController, Boolean> canCall) {
      this(mod -> new Pair<>(taskProvider.getTask(mod), priorityCalculator.getPriority()), canCall);
   }

   public ActionPriorityTask(TaskAndPriorityProvider taskAndPriorityProvider) {
      this(taskAndPriorityProvider, a -> true);
   }

   public ActionPriorityTask(TaskAndPriorityProvider taskAndPriorityProvider, Function<AltoClefController, Boolean> canCall) {
      this(taskAndPriorityProvider, canCall, false, true, false);
   }

   public ActionPriorityTask(
      TaskProvider taskProvider,
      PriorityCalculator priorityCalculator,
      Function<AltoClefController, Boolean> canCall,
      boolean shouldForce,
      boolean canCache,
      boolean bypassForceCooldown
   ) {
      this(mod -> new Pair<>(taskProvider.getTask(mod), priorityCalculator.getPriority()), canCall, shouldForce, canCache, bypassForceCooldown);
   }

   public ActionPriorityTask(
      TaskAndPriorityProvider taskAndPriorityProvider,
      Function<AltoClefController, Boolean> canCall,
      boolean shouldForce,
      boolean canCache,
      boolean bypassForceCooldown
   ) {
      super(canCall, shouldForce, canCache, bypassForceCooldown);
      this.taskAndPriorityProvider = taskAndPriorityProvider;
   }

   @Override
   public Task getTask(AltoClefController mod) {
      this.lastTask = this.getTaskAndPriority(mod).getLeft();
      return this.lastTask;
   }

   @Override
   public String getDebugString() {
      return "Performing an action: " + this.lastTask;
   }

   @Override
   protected double getPriority(AltoClefController mod) {
      return this.getTaskAndPriority(mod).getRight();
   }

   private Pair<Task, Double> getTaskAndPriority(AltoClefController mod) {
      Pair<Task, Double> pair = this.taskAndPriorityProvider.getTaskAndPriority(mod);
      if (pair == null) {
         pair = new Pair<>(null, 0.0);
      }

      if (pair.getRight() <= 0.0 || pair.getLeft() == null) {
         pair.setLeft(null);
         pair.setRight(Double.NEGATIVE_INFINITY);
      }

      return pair;
   }

   public interface TaskAndPriorityProvider {
      Pair<Task, Double> getTaskAndPriority(AltoClefController var1);
   }

   public interface TaskProvider {
      Task getTask(AltoClefController var1);
   }
}
