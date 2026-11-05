// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/resources/CollectEggsTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.resources;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.tasks.ResourceTask;
import com.mineagent.sbe.altoclef.tasks.entity.DoToClosestEntityTask;
import com.mineagent.sbe.altoclef.tasks.movement.DefaultGoToDimensionTask;
import com.mineagent.sbe.altoclef.tasks.movement.GetToEntityTask;
import com.mineagent.sbe.altoclef.tasksystem.Task;
import com.mineagent.sbe.altoclef.util.Dimension;
import com.mineagent.sbe.altoclef.util.helpers.WorldHelper;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.item.Items;

public class CollectEggsTask extends ResourceTask {
   private final int count;
   private final DoToClosestEntityTask waitNearChickens;
   private AltoClefController mod;

   public CollectEggsTask(int targetCount) {
      super(Items.EGG, targetCount);
      this.count = targetCount;
      this.waitNearChickens = new DoToClosestEntityTask(chicken -> new GetToEntityTask(chicken, 5.0), Chicken.class);
   }

   @Override
   protected boolean shouldAvoidPickingUp(AltoClefController mod) {
      return false;
   }

   @Override
   protected void onResourceStart(AltoClefController mod) {
      this.mod = mod;
   }

   @Override
   protected Task onResourceTick(AltoClefController mod) {
      if (this.waitNearChickens.wasWandering() && WorldHelper.getCurrentDimension(this.controller) != Dimension.OVERWORLD) {
         this.setDebugState("Going to right dimension.");
         return new DefaultGoToDimensionTask(Dimension.OVERWORLD);
      } else {
         this.setDebugState("Waiting around chickens. Yes.");
         return this.waitNearChickens;
      }
   }

   @Override
   protected void onResourceStop(AltoClefController mod, Task interruptTask) {
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return other instanceof CollectEggsTask;
   }

   @Override
   protected String toDebugStringName() {
      return "Collecting " + this.count + " eggs.";
   }
}
