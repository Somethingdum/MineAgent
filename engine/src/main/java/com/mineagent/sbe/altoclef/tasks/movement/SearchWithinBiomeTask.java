// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/movement/SearchWithinBiomeTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.movement;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.multiversion.world.WorldVer;
import com.mineagent.sbe.altoclef.tasksystem.Task;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;

public class SearchWithinBiomeTask extends SearchChunksExploreTask {
   private final ResourceKey<Biome> toSearch;

   public SearchWithinBiomeTask(ResourceKey<Biome> toSearch) {
      this.toSearch = toSearch;
   }

   @Override
   protected boolean isChunkWithinSearchSpace(AltoClefController mod, ChunkPos pos) {
      return WorldVer.isBiomeAtPos(mod.getWorld(), this.toSearch, pos.getWorldPosition().offset(1, 1, 1));
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof SearchWithinBiomeTask task ? task.toSearch == this.toSearch : false;
   }

   @Override
   protected String toDebugString() {
      return "Searching for+within biome: " + this.toSearch;
   }
}
