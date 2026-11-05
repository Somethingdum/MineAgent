// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/resources/KillAndLootTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.resources;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.tasks.ResourceTask;
import com.mineagent.sbe.altoclef.tasks.entity.KillEntitiesTask;
import com.mineagent.sbe.altoclef.tasks.movement.TimeoutWanderTask;
import com.mineagent.sbe.altoclef.tasksystem.Task;
import com.mineagent.sbe.altoclef.util.ItemTarget;
import net.minecraft.world.entity.Entity;
import java.util.function.Predicate;

public class KillAndLootTask extends ResourceTask {
   private final Class<?> toKill;
   private final Task killTask;

   public KillAndLootTask(Class<?> toKill, Predicate<Entity> shouldKill, ItemTarget... itemTargets) {
      super((ItemTarget[])itemTargets.clone());
      this.toKill = toKill;
      this.killTask = new KillEntitiesTask(shouldKill, this.toKill);
   }

   public KillAndLootTask(Class<?> toKill, ItemTarget... itemTargets) {
      super((ItemTarget[])itemTargets.clone());
      this.toKill = toKill;
      this.killTask = new KillEntitiesTask(this.toKill);
   }

   @Override
   protected boolean shouldAvoidPickingUp(AltoClefController mod) {
      return false;
   }

   @Override
   protected void onResourceStart(AltoClefController mod) {
   }

   @Override
   protected Task onResourceTick(AltoClefController mod) {
      if (!mod.getEntityTracker().entityFound(this.toKill)) {
         if (this.isInWrongDimension(mod)) {
            this.setDebugState("Going to correct dimension.");
            return this.getToCorrectDimensionTask(mod);
         } else {
            this.setDebugState("Searching for mob...");
            return new TimeoutWanderTask();
         }
      } else {
         return this.killTask;
      }
   }

   @Override
   protected void onResourceStop(AltoClefController mod, Task interruptTask) {
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return other instanceof KillAndLootTask task ? task.toKill.equals(this.toKill) : false;
   }

   @Override
   protected String toDebugStringName() {
      return "Collect items from " + this.toKill.toGenericString();
   }
}
