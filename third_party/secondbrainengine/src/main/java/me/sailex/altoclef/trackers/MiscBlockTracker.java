package me.sailex.altoclef.trackers;

import me.sailex.altoclef.AltoClefController;
import me.sailex.altoclef.util.Dimension;
import me.sailex.altoclef.util.helpers.WorldHelper;
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
