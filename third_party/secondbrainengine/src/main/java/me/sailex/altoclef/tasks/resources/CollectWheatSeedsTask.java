package me.sailex.altoclef.tasks.resources;

import me.sailex.altoclef.AltoClefController;
import me.sailex.altoclef.tasks.ResourceTask;
import me.sailex.altoclef.tasksystem.Task;
import me.sailex.altoclef.util.MiningRequirement;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class CollectWheatSeedsTask extends ResourceTask {
   private final int count;

   public CollectWheatSeedsTask(int count) {
      super(Items.WHEAT_SEEDS, count);
      this.count = count;
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
      return (Task)(mod.getBlockScanner().anyFound(Blocks.WHEAT)
         ? new CollectCropTask(Items.AIR, 999, Blocks.WHEAT, Items.WHEAT_SEEDS)
         : new MineAndCollectTask(Items.WHEAT_SEEDS, this.count, new Block[]{ Blocks.SHORT_GRASS , Blocks.TALL_GRASS}, MiningRequirement.HAND));
   }

   @Override
   protected void onResourceStop(AltoClefController mod, Task interruptTask) {
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return other instanceof CollectWheatSeedsTask;
   }

   @Override
   protected String toDebugStringName() {
      return "Collecting " + this.count + " wheat seeds.";
   }
}
