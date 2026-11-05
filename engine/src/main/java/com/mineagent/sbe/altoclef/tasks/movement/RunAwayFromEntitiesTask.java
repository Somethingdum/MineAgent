// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/movement/RunAwayFromEntitiesTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.movement;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.util.baritone.GoalRunAwayFromEntities;
import com.mineagent.sbe.automatone.api.pathing.goals.Goal;
import net.minecraft.world.entity.Entity;
import java.util.List;
import java.util.function.Supplier;

public abstract class RunAwayFromEntitiesTask extends CustomBaritoneGoalTask {
   private final Supplier<List<Entity>> runAwaySupplier;
   private final double distanceToRun;
   private final boolean xz;
   private final double penalty;

   public RunAwayFromEntitiesTask(Supplier<List<Entity>> toRunAwayFrom, double distanceToRun, boolean xz, double penalty) {
      this.runAwaySupplier = toRunAwayFrom;
      this.distanceToRun = distanceToRun;
      this.xz = xz;
      this.penalty = penalty;
   }

   public RunAwayFromEntitiesTask(Supplier<List<Entity>> toRunAwayFrom, double distanceToRun, double penalty) {
      this(toRunAwayFrom, distanceToRun, false, penalty);
   }

   @Override
   protected Goal newGoal(AltoClefController mod) {
      return new GoalRunAwayStuff(mod, this.distanceToRun, this.xz);
   }

   private class GoalRunAwayStuff extends GoalRunAwayFromEntities {
      public GoalRunAwayStuff(AltoClefController mod, double distance, boolean xz) {
         super(mod, distance, xz, RunAwayFromEntitiesTask.this.penalty);
      }

      @Override
      protected List<Entity> getEntities(AltoClefController mod) {
         return RunAwayFromEntitiesTask.this.runAwaySupplier.get();
      }
   }
}
