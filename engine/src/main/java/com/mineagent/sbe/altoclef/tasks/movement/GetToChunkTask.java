// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/movement/GetToChunkTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.movement;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.tasksystem.Task;
import com.mineagent.sbe.altoclef.util.baritone.GoalChunk;
import com.mineagent.sbe.altoclef.util.progresscheck.MovementProgressChecker;
import com.mineagent.sbe.automatone.api.pathing.goals.Goal;
import net.minecraft.world.level.ChunkPos;

public class GetToChunkTask extends CustomBaritoneGoalTask {
   private final ChunkPos pos;

   public GetToChunkTask(ChunkPos pos) {
      this.checker = new MovementProgressChecker();
      this.pos = pos;
   }

   @Override
   protected Goal newGoal(AltoClefController mod) {
      return new GoalChunk(this.pos);
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof GetToChunkTask task ? task.pos.equals(this.pos) : false;
   }

   @Override
   protected String toDebugString() {
      return "Get to chunk: " + this.pos.toString();
   }
}
