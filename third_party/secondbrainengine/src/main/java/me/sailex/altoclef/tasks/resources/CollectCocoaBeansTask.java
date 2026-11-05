package me.sailex.altoclef.tasks.resources;

import me.sailex.altoclef.AltoClefController;
import me.sailex.altoclef.tasks.DoToClosestBlockTask;
import me.sailex.altoclef.tasks.ResourceTask;
import me.sailex.altoclef.tasks.construction.DestroyBlockTask;
import me.sailex.altoclef.tasks.movement.SearchWithinBiomeTask;
import me.sailex.altoclef.tasksystem.Task;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.state.BlockState;
import java.util.HashSet;
import java.util.function.Predicate;

public class CollectCocoaBeansTask extends ResourceTask {
   private final int count;
   private final HashSet<BlockPos> wasFullyGrown = new HashSet<>();

   public CollectCocoaBeansTask(int targetCount) {
      super(Items.COCOA_BEANS, targetCount);
      this.count = targetCount;
   }

   @Override
   protected boolean shouldAvoidPickingUp(AltoClefController mod) {
      return false;
   }

   @Override
   protected void onResourceStart(AltoClefController mod) {
   }

   @Override
   protected Task onResourceTick(AltoClefController mod) {
      Predicate<BlockPos> validCocoa = blockPos -> {
         if (!mod.getChunkTracker().isChunkLoaded(blockPos)) {
            return this.wasFullyGrown.contains(blockPos);
         } else {
            BlockState s = mod.getWorld().getBlockState(blockPos);
            boolean mature = (Integer)s.getValue(CocoaBlock.AGE) == 2;
            if (this.wasFullyGrown.contains(blockPos)) {
               if (!mature) {
                  this.wasFullyGrown.remove(blockPos);
               }
            } else if (mature) {
               this.wasFullyGrown.add(blockPos);
            }

            return mature;
         }
      };
      if (mod.getBlockScanner().anyFound(validCocoa, Blocks.COCOA)) {
         this.setDebugState("Breaking cocoa blocks");
         return new DoToClosestBlockTask(DestroyBlockTask::new, validCocoa, Blocks.COCOA);
      } else if (this.isInWrongDimension(mod)) {
         return this.getToCorrectDimensionTask(mod);
      } else {
         this.setDebugState("Exploring around jungles");
         return new SearchWithinBiomeTask(Biomes.JUNGLE);
      }
   }

   @Override
   protected void onResourceStop(AltoClefController mod, Task interruptTask) {
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return other instanceof CollectCocoaBeansTask;
   }

   @Override
   protected String toDebugStringName() {
      return "Collecting " + this.count + " cocoa beans.";
   }
}
