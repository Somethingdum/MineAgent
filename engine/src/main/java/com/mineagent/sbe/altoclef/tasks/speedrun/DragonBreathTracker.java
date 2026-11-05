// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/speedrun/DragonBreathTracker.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.speedrun;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.BotBehaviour;
import com.mineagent.sbe.altoclef.tasks.movement.CustomBaritoneGoalTask;
import com.mineagent.sbe.altoclef.tasksystem.Task;
import com.mineagent.sbe.altoclef.util.helpers.WorldHelper;
import com.mineagent.sbe.altoclef.util.progresscheck.MovementProgressChecker;
import com.mineagent.sbe.automatone.api.pathing.goals.Goal;
import com.mineagent.sbe.automatone.api.pathing.goals.GoalRunAway;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.AreaEffectCloud;
import java.util.HashSet;

public class DragonBreathTracker {
   private final HashSet<BlockPos> breathBlocks = new HashSet<>();

   public void updateBreath(AltoClefController mod) {
      this.breathBlocks.clear();

      for (AreaEffectCloud cloud : mod.getEntityTracker().getTrackedEntities(AreaEffectCloud.class)) {
         for (BlockPos bad : WorldHelper.getBlocksTouchingBox(cloud.getBoundingBox())) {
            this.breathBlocks.add(bad);
         }
      }
   }

   public boolean isTouchingDragonBreath(BlockPos pos) {
      return this.breathBlocks.contains(pos);
   }

   public Task getRunAwayTask() {
      return new RunAwayFromDragonsBreathTask();
   }

   private class RunAwayFromDragonsBreathTask extends CustomBaritoneGoalTask {
      @Override
      protected void onStart() {
         super.onStart();
         BotBehaviour botBehaviour = this.controller.getBehaviour();
         botBehaviour.push();
         botBehaviour.setBlockPlacePenalty(Double.POSITIVE_INFINITY);
         this.checker = new MovementProgressChecker(Integer.MAX_VALUE);
      }

      @Override
      protected void onStop(Task interruptTask) {
         super.onStop(interruptTask);
         this.controller.getBehaviour().pop();
      }

      @Override
      protected Goal newGoal(AltoClefController mod) {
         return new GoalRunAway(10.0, DragonBreathTracker.this.breathBlocks.toArray(BlockPos[]::new));
      }

      @Override
      protected boolean isEqual(Task other) {
         return other instanceof RunAwayFromDragonsBreathTask;
      }

      @Override
      protected String toDebugString() {
         return "ESCAPE Dragons Breath";
      }
   }
}
