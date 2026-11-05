// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/movement/RunAwayFromPositionTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.movement;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.tasksystem.Task;
import com.mineagent.sbe.automatone.api.pathing.goals.Goal;
import com.mineagent.sbe.automatone.api.pathing.goals.GoalRunAway;
import net.minecraft.core.BlockPos;
import java.util.Arrays;

public class RunAwayFromPositionTask extends CustomBaritoneGoalTask {
   private final BlockPos[] dangerBlocks;
   private final double distance;
   private final Integer maintainY;

   public RunAwayFromPositionTask(double distance, BlockPos... toRunAwayFrom) {
      this(distance, null, toRunAwayFrom);
   }

   public RunAwayFromPositionTask(double distance, Integer maintainY, BlockPos... toRunAwayFrom) {
      this.distance = distance;
      this.dangerBlocks = toRunAwayFrom;
      this.maintainY = maintainY;
   }

   @Override
   protected Goal newGoal(AltoClefController mod) {
      return new GoalRunAway(this.distance, this.maintainY, this.dangerBlocks);
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof RunAwayFromPositionTask task ? Arrays.equals((Object[])task.dangerBlocks, (Object[])this.dangerBlocks) : false;
   }

   @Override
   protected String toDebugString() {
      return "Running away from " + Arrays.toString((Object[])this.dangerBlocks);
   }
}
