// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/movement/SearchChunkForBlockTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.movement;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.tasksystem.Task;
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
