// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/trackers/MiscBlockTracker.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.trackers;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.util.Dimension;
import com.mineagent.sbe.altoclef.util.helpers.WorldHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class MiscBlockTracker {
   private final AltoClefController mod;
   private final Map<Dimension, BlockPos> lastNetherPortalsUsed = new HashMap<>();
   private Dimension lastDimension;
   private boolean newDimensionTriggered;

   public MiscBlockTracker(AltoClefController mod) {
      this.mod = mod;
   }

   public void tick() {
      if (WorldHelper.getCurrentDimension(this.mod) != this.lastDimension) {
         this.lastDimension = WorldHelper.getCurrentDimension(this.mod);
         this.newDimensionTriggered = true;
      }

      if (AltoClefController.inGame() && this.newDimensionTriggered) {
         for (BlockPos check : WorldHelper.scanRegion(
            this.mod.getPlayer().blockPosition().offset(-1, -1, -1), this.mod.getPlayer().blockPosition().offset(1, 1, 1)
         )) {
            Block currentBlock = this.mod.getWorld().getBlockState(check).getBlock();
            if (currentBlock == Blocks.NETHER_PORTAL) {
               while (check.getY() > 0 && this.mod.getWorld().getBlockState(check.below()).getBlock() == Blocks.NETHER_PORTAL) {
                  check = check.below();
               }

               BlockPos below = check.below();
               if (WorldHelper.isSolidBlock(this.mod, below)) {
                  this.lastNetherPortalsUsed.put(WorldHelper.getCurrentDimension(this.mod), check);
                  this.newDimensionTriggered = false;
               }
               break;
            }
         }
      }
   }

   public void reset() {
      this.lastNetherPortalsUsed.clear();
   }

   public Optional<BlockPos> getLastUsedNetherPortal(Dimension dimension) {
      if (this.lastNetherPortalsUsed.containsKey(dimension)) {
         BlockPos portalPos = this.lastNetherPortalsUsed.get(dimension);
         if (this.mod.getChunkTracker().isChunkLoaded(portalPos) && !this.mod.getBlockScanner().isBlockAtPosition(portalPos, Blocks.NETHER_PORTAL)) {
            this.lastNetherPortalsUsed.remove(dimension);
            return Optional.empty();
         } else {
            return Optional.ofNullable(portalPos);
         }
      } else {
         return Optional.empty();
      }
   }
}
