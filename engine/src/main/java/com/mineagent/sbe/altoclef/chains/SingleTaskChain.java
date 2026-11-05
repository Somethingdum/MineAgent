// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/chains/SingleTaskChain.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.chains;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.Debug;
import com.mineagent.sbe.altoclef.tasksystem.Task;
import com.mineagent.sbe.altoclef.tasksystem.TaskChain;
import com.mineagent.sbe.altoclef.tasksystem.TaskRunner;

public abstract class SingleTaskChain extends TaskChain {
   protected Task mainTask = null;
   private boolean interrupted = false;
   private final AltoClefController mod;

   public SingleTaskChain(TaskRunner runner) {
      super(runner);
      this.mod = runner.getMod();
   }

   @Override
   protected void onTick() {
      if (this.isActive()) {
         if (this.interrupted) {
            this.interrupted = false;
            if (this.mainTask != null) {
               this.mainTask.reset();
            }
         }

         if (this.mainTask != null) {
            if (this.mainTask.controller == null) {
               this.mainTask.controller = this.controller;
            }

            if (!this.mainTask.isFinished() && !this.mainTask.stopped()) {
               this.mainTask.tick(this);
            } else {
               this.onTaskFinish(this.mod);
            }
         }
      }
   }

   @Override
   protected void onStop() {
      if (this.isActive() && this.mainTask != null) {
         this.mainTask.stop();
         this.mainTask = null;
      }
   }

   public void setTask(Task task) {
      if (this.mainTask == null || !this.mainTask.equals(task)) {
         if (this.mainTask != null) {
            this.mainTask.stop(task);
         }

         this.mainTask = task;
         if (task != null) {
            task.reset();
         }
      }
   }

   @Override
   public boolean isActive() {
      return this.mainTask != null;
   }

   protected abstract void onTaskFinish(AltoClefController var1);

   @Override
   public void onInterrupt(TaskChain other) {
      if (other != null) {
         Debug.logInternal("Chain Interrupted: " + this + " by " + other);
      }

      this.interrupted = true;
      if (this.mainTask != null && this.mainTask.isActive()) {
         this.mainTask.interrupt(null);
      }
   }

   protected boolean isCurrentlyRunning(AltoClefController mod) {
      return !this.interrupted && this.mainTask.isActive() && !this.mainTask.isFinished();
   }

   public Task getCurrentTask() {
      return this.mainTask;
   }
}
