// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/movement/GetToYTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.movement;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.tasksystem.Task;
import com.mineagent.sbe.altoclef.util.Dimension;
import com.mineagent.sbe.altoclef.util.helpers.WorldHelper;
import com.mineagent.sbe.automatone.api.pathing.goals.Goal;
import com.mineagent.sbe.automatone.api.pathing.goals.GoalYLevel;

public class GetToYTask extends CustomBaritoneGoalTask {
   private final int yLevel;
   private final Dimension dimension;

   public GetToYTask(int ylevel, Dimension dimension) {
      this.yLevel = ylevel;
      this.dimension = dimension;
   }

   public GetToYTask(int ylevel) {
      this(ylevel, null);
   }

   @Override
   protected Task onTick() {
      return (Task)(this.dimension != null && WorldHelper.getCurrentDimension(this.controller) != this.dimension
         ? new DefaultGoToDimensionTask(this.dimension)
         : super.onTick());
   }

   @Override
   protected Goal newGoal(AltoClefController mod) {
      return new GoalYLevel(this.yLevel);
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof GetToYTask task ? task.yLevel == this.yLevel : false;
   }

   @Override
   protected String toDebugString() {
      return "Going to y=" + this.yLevel + (this.dimension != null ? "in dimension" + this.dimension : "");
   }
}
