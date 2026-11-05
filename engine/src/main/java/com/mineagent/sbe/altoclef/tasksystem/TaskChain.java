// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasksystem/TaskChain.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasksystem;

import com.mineagent.sbe.altoclef.AltoClefController;

import java.util.ArrayList;
import java.util.List;

public abstract class TaskChain {
   protected AltoClefController controller;
   private final List<Task> cachedTaskChain = new ArrayList<>();

   public TaskChain(TaskRunner runner) {
      runner.addTaskChain(this);
      this.controller = runner.getMod();
   }

   public void tick() {
      this.cachedTaskChain.clear();
      this.onTick();
   }

   public void stop() {
      this.cachedTaskChain.clear();
      this.onStop();
   }

   protected abstract void onStop();

   public abstract void onInterrupt(TaskChain var1);

   protected abstract void onTick();

   public abstract float getPriority();

   public abstract boolean isActive();

   public abstract String getName();

   public List<Task> getTasks() {
      return this.cachedTaskChain;
   }

   void addTaskToChain(Task task) {
      this.cachedTaskChain.add(task);
   }

   @Override
   public String toString() {
      return this.getName();
   }
}
