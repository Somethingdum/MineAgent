package me.sailex.altoclef.tasks.movement;

import me.sailex.altoclef.AltoClefController;
import me.sailex.altoclef.tasksystem.Task;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import org.apache.commons.lang3.ArrayUtils;

import java.util.Arrays;
import java.util.HashSet;

public class SearchChunkForBlockTask extends SearchChunksExploreTask {
   private final HashSet<Block> toSearchFor = new HashSet<>();

   public SearchChunkForBlockTask(Block... blocks) {
      this.toSearchFor.addAll(Arrays.asList(blocks));
   }

   @Override
   protected boolean isChunkWithinSearchSpace(AltoClefController mod, ChunkPos pos) {
      return mod.getChunkTracker().scanChunk(pos, block -> {return this.toSearchFor.contains(mod.getWorld().getBlockState(block).getBlock());});
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof SearchChunkForBlockTask blockTask
         ? Arrays.equals(blockTask.toSearchFor.toArray(Block[]::new), this.toSearchFor.toArray(Block[]::new))
         : false;
   }

   @Override
   protected String toDebugString() {
      return "Searching chunk for blocks " + ArrayUtils.toString(this.toSearchFor.toArray(Block[]::new));
   }
}
