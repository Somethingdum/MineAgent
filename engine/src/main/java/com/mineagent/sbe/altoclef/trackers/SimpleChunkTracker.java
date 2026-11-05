// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/trackers/SimpleChunkTracker.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.trackers;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.Debug;
import com.mineagent.sbe.altoclef.multiversion.world.HeightLimitViewVer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class SimpleChunkTracker {
   private final AltoClefController mod;
   private final Set<ChunkPos> loaded = new HashSet<>();

   public SimpleChunkTracker(AltoClefController mod) {
      this.mod = mod;
      // SMH: the loader's chunk events through ServerHooks; and the chunks already loaded round it when it starts.
      com.mineagent.sbe.common.ServerHooks.onChunkLoad((level, pos) -> this.onLoad(pos));
      com.mineagent.sbe.common.ServerHooks.onChunkUnload((level, pos) -> this.onUnload(pos));
      this.loaded.addAll(com.mineagent.sbe.common.ServerHooks.loadedChunksAround(mod.getPlayer()));
   }

   private void onLoad(ChunkPos pos) {
      this.loaded.add(pos);
   }

   private void onUnload(ChunkPos pos) {
      this.loaded.remove(pos);
   }

   public boolean isChunkLoaded(ChunkPos pos) {
      // SMH: a server's getChunk loads (or makes) the chunk asked for; a client's gives an empty one. Asked without loading.
      return this.mod.getWorld().getChunkSource().getChunkNow(pos.x, pos.z) != null;
   }

   public boolean isChunkLoaded(BlockPos pos) {
      return this.isChunkLoaded(new ChunkPos(pos));
   }

   public List<ChunkPos> getLoadedChunks() {
      List<ChunkPos> result = new ArrayList<>(this.loaded);
      return result.stream().filter(this::isChunkLoaded).distinct().collect(Collectors.toList());
   }

   public boolean scanChunk(ChunkPos chunk, Predicate<BlockPos> onBlockStop) {
      if (!this.isChunkLoaded(chunk)) {
         return false;
      } else {
         int bottomY = this.mod.getWorld().getMinBuildHeight();
         int topY = HeightLimitViewVer.getTopY(this.mod.getWorld());

         for (int xx = chunk.getMinBlockX(); xx <= chunk.getMaxBlockX(); xx++) {
            for (int yy = bottomY; yy <= topY; yy++) {
               for (int zz = chunk.getMinBlockZ(); zz <= chunk.getMaxBlockZ(); zz++) {
                  if (onBlockStop.test(new BlockPos(xx, yy, zz))) {
                     return true;
                  }
               }
            }
         }

         return false;
      }
   }

   public void scanChunk(ChunkPos chunk, Consumer<BlockPos> onBlock) {
      this.scanChunk(chunk, block -> {
         onBlock.accept(block);
         return false;
      });
   }

   public void reset(AltoClefController mod) {
      Debug.logInternal("CHUNKS RESET");
      this.loaded.clear();
   }
}
