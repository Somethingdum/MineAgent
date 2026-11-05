// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasksystem/TaskRunner.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasksystem;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.Debug;

import java.util.ArrayList;

public class TaskRunner {
   private final ArrayList<TaskChain> chains = new ArrayList<>();
   private final AltoClefController mod;
   private boolean active;
   private TaskChain cachedCurrentTaskChain = null;
   public String statusReport = " (no chain running) ";

   public TaskRunner(AltoClefController mod) {
      this.mod = mod;
      this.active = false;
   }

   public void tick() {
      if (this.active && AltoClefController.inGame()) {
         TaskChain maxChain = null;
         float maxPriority = Float.NEGATIVE_INFINITY;

         for (TaskChain chain : this.chains) {
            if (chain.isActive()) {
               float priority = chain.getPriority();
               if (priority > maxPriority) {
                  maxPriority = priority;
                  maxChain = chain;
               }
            }
         }

         if (this.cachedCurrentTaskChain != null && maxChain != this.cachedCurrentTaskChain) {
            this.cachedCurrentTaskChain.onInterrupt(maxChain);
         }

         this.cachedCurrentTaskChain = maxChain;
         if (maxChain != null) {
            this.statusReport = "Chain: " + maxChain.getName() + ", priority: " + maxPriority;
            maxChain.tick();
         } else {
            this.statusReport = " (no chain running) ";
         }
      } else {
         this.statusReport = " (no chain running) ";
      }
   }

   public void addTaskChain(TaskChain chain) {
      this.chains.add(chain);
   }

   public void enable() {
      if (!this.active) {
         this.mod.getBehaviour().push();
         this.mod.getBehaviour().setPauseOnLostFocus(false);
      }

      this.active = true;
   }

   public void disable() {
      if (this.active) {
         this.mod.getBehaviour().pop();
      }

      for (TaskChain chain : this.chains) {
         chain.stop();
      }

      this.active = false;
      Debug.logMessage("Stopped");
   }

   public boolean isActive() {
      return this.active;
   }

   public TaskChain getCurrentTaskChain() {
      return this.cachedCurrentTaskChain;
   }

   public AltoClefController getMod() {
      return this.mod;
   }
}
