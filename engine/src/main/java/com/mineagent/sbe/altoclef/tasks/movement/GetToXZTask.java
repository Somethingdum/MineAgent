// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/movement/GetToXZTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.movement;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.tasksystem.Task;
import com.mineagent.sbe.altoclef.util.Dimension;
import com.mineagent.sbe.altoclef.util.helpers.WorldHelper;
import com.mineagent.sbe.automatone.api.pathing.goals.Goal;
import com.mineagent.sbe.automatone.api.pathing.goals.GoalXZ;
import net.minecraft.core.BlockPos;

public class GetToXZTask extends CustomBaritoneGoalTask {
   private final int x;
   private final int z;
   private final Dimension dimension;

   public GetToXZTask(int x, int z) {
      this(x, z, null);
   }

   public GetToXZTask(int x, int z, Dimension dimension) {
      this.x = x;
      this.z = z;
      this.dimension = dimension;
   }

   @Override
   protected Task onTick() {
      return (Task)(this.dimension != null && WorldHelper.getCurrentDimension(this.controller) != this.dimension
         ? new DefaultGoToDimensionTask(this.dimension)
         : super.onTick());
   }

   @Override
   protected Goal newGoal(AltoClefController mod) {
      return new GoalXZ(this.x, this.z);
   }

   @Override
   protected boolean isEqual(Task other) {
      return !(other instanceof GetToXZTask task) ? false : task.x == this.x && task.z == this.z && task.dimension == this.dimension;
   }

   @Override
   public boolean isFinished() {
      BlockPos cur = this.controller.getPlayer().blockPosition();
      return cur.getX() == this.x && cur.getZ() == this.z && (this.dimension == null || this.dimension == WorldHelper.getCurrentDimension(this.controller));
   }

   @Override
   protected String toDebugString() {
      return "Getting to (" + this.x + "," + this.z + ")" + (this.dimension != null ? " in dimension " + this.dimension : "");
   }
}
