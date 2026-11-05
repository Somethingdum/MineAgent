// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/movement/GetToBlockTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.movement;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.Debug;
import com.mineagent.sbe.altoclef.tasksystem.ITaskRequiresGrounded;
import com.mineagent.sbe.altoclef.tasksystem.Task;
import com.mineagent.sbe.altoclef.util.Dimension;
import com.mineagent.sbe.altoclef.util.helpers.WorldHelper;
import com.mineagent.sbe.altoclef.util.time.TimerGame;
import com.mineagent.sbe.automatone.api.pathing.goals.Goal;
import com.mineagent.sbe.automatone.api.pathing.goals.GoalBlock;
import net.minecraft.core.BlockPos;

public class GetToBlockTask extends CustomBaritoneGoalTask implements ITaskRequiresGrounded {
   private final BlockPos position;
   private final boolean preferStairs;
   private final Dimension dimension;
   private int finishedTicks = 0;
   private final TimerGame wanderTimer = new TimerGame(2.0);

   public GetToBlockTask(BlockPos position, boolean preferStairs) {
      this(position, preferStairs, null);
   }

   public GetToBlockTask(BlockPos position, Dimension dimension) {
      this(position, false, dimension);
   }

   public GetToBlockTask(BlockPos position, boolean preferStairs, Dimension dimension) {
      this.dimension = dimension;
      this.position = position;
      this.preferStairs = preferStairs;
   }

   public GetToBlockTask(BlockPos position) {
      this(position, false);
   }

   @Override
   protected Task onTick() {
      if (this.dimension != null && WorldHelper.getCurrentDimension(this.controller) != this.dimension) {
         return new DefaultGoToDimensionTask(this.dimension);
      } else {
         if (this.isFinished()) {
            this.finishedTicks++;
         } else {
            this.finishedTicks = 0;
         }

         if (this.finishedTicks > 200) {
            this.wanderTimer.reset();
            Debug.logWarning("GetToBlock was finished for 10 seconds yet is still being called, wandering");
            this.finishedTicks = 0;
            return new TimeoutWanderTask();
         } else {
            return (Task)(!this.wanderTimer.elapsed() ? new TimeoutWanderTask() : super.onTick());
         }
      }
   }

   @Override
   protected void onStart() {
      super.onStart();
      if (this.preferStairs) {
         this.controller.getBehaviour().push();
         this.controller.getBehaviour().setPreferredStairs(true);
      }
   }

   @Override
   protected void onStop(Task interruptTask) {
      super.onStop(interruptTask);
      if (this.preferStairs) {
         this.controller.getBehaviour().pop();
      }
   }

   @Override
   protected boolean isEqual(Task other) {
      return !(other instanceof GetToBlockTask task)
         ? false
         : task.position.equals(this.position) && task.preferStairs == this.preferStairs && task.dimension == this.dimension;
   }

   @Override
   public boolean isFinished() {
      return super.isFinished() && (this.dimension == null || this.dimension == WorldHelper.getCurrentDimension(this.controller));
   }

   @Override
   protected String toDebugString() {
      return "Getting to block " + this.position + (this.dimension != null ? " in dimension " + this.dimension : "");
   }

   @Override
   protected Goal newGoal(AltoClefController mod) {
      return new GoalBlock(this.position);
   }

   @Override
   protected void onWander(AltoClefController mod) {
      super.onWander(mod);
      mod.getBlockScanner().requestBlockUnreachable(this.position);
   }
}
