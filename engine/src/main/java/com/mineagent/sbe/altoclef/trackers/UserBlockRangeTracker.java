// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/trackers/UserBlockRangeTracker.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.trackers;

import com.mineagent.sbe.altoclef.util.helpers.BaritoneHelper;
import com.mineagent.sbe.altoclef.util.helpers.ItemHelper;
import com.mineagent.sbe.altoclef.util.helpers.WorldHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import com.google.common.collect.Streams;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class UserBlockRangeTracker extends Tracker {
   final int AVOID_BREAKING_RANGE = 16;
   final Block[] USER_INDICATOR_BLOCKS = Streams.concat(Arrays.stream(ItemHelper.itemsToBlocks(ItemHelper.BED))).toArray(Block[]::new);
   final Block[] USER_BLOCKS_TO_AVOID_BREAKING = Streams.concat(
         Arrays.asList(Blocks.COBBLESTONE).stream(), Arrays.stream(ItemHelper.itemsToBlocks(ItemHelper.LOG))
      )
      .toArray(Block[]::new);
   private final Set<BlockPos> dontBreakBlocks = new HashSet<>();

   public UserBlockRangeTracker(TrackerManager manager) {
      super(manager);
   }

   public boolean isNearUserTrackedBlock(BlockPos pos) {
      this.ensureUpdated();
      synchronized (BaritoneHelper.MINECRAFT_LOCK) {
         return this.dontBreakBlocks.contains(pos);
      }
   }

   @Override
   protected void updateState() {
      this.dontBreakBlocks.clear();
      List<BlockPos> userBlocks = this.mod.getBlockScanner().getKnownLocationsIncludeUnreachable(this.USER_INDICATOR_BLOCKS);
      Set<Block> userIndicatorBlocks = new HashSet<>(Arrays.asList(this.USER_INDICATOR_BLOCKS));
      Set<Block> userBlocksToAvoidMining = new HashSet<>(Arrays.asList(this.USER_BLOCKS_TO_AVOID_BREAKING));
      userBlocks.removeIf(bpos -> {
         Block bx = this.mod.getWorld().getBlockState(bpos).getBlock();
         return !userIndicatorBlocks.contains(bx);
      });

      for (BlockPos userBlockPos : userBlocks) {
         BlockPos min = userBlockPos.offset(-16, -16, -16);
         BlockPos max = userBlockPos.offset(16, 16, 16);

         for (BlockPos possible : WorldHelper.scanRegion(min, max)) {
            Block b = this.mod.getWorld().getBlockState(possible).getBlock();
            if (userBlocksToAvoidMining.contains(b)) {
               this.dontBreakBlocks.add(possible);
            }
         }
      }
   }

   @Override
   protected void reset() {
      this.dontBreakBlocks.clear();
   }
}
