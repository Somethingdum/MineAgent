package me.sailex.altoclef.tasks.movement;

import me.sailex.altoclef.AltoClefController;
import me.sailex.altoclef.tasksystem.Task;
import me.sailex.altoclef.util.baritone.GoalChunk;
import me.sailex.altoclef.util.progresscheck.MovementProgressChecker;
import me.sailex.automatone.api.pathing.goals.Goal;
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
