// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/movement/DodgeProjectilesTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.movement;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.tasksystem.Task;
import com.mineagent.sbe.altoclef.util.baritone.GoalDodgeProjectiles;
import com.mineagent.sbe.automatone.api.pathing.goals.Goal;

public class DodgeProjectilesTask extends CustomBaritoneGoalTask {
   private final double distanceHorizontal;
   private final double distanceVertical;

   public DodgeProjectilesTask(double distanceHorizontal, double distanceVertical) {
      this.distanceHorizontal = distanceHorizontal;
      this.distanceVertical = distanceVertical;
   }

   @Override
   protected Task onTick() {
      if (this.cachedGoal != null) {
         GoalDodgeProjectiles var1 = (GoalDodgeProjectiles)this.cachedGoal;
      }

      return super.onTick();
   }

   @Override
   protected boolean isEqual(Task other) {
      if (other instanceof DodgeProjectilesTask task) {
         return Math.abs(task.distanceHorizontal - this.distanceHorizontal) > 1.0 ? false : !(Math.abs(task.distanceVertical - this.distanceVertical) > 1.0);
      } else {
         return false;
      }
   }

   @Override
   protected String toDebugString() {
      return "Dodge arrows at " + this.distanceHorizontal + " blocks away";
   }

   @Override
   protected Goal newGoal(AltoClefController mod) {
      return new GoalDodgeProjectiles(mod, this.distanceHorizontal, this.distanceVertical);
   }
}
