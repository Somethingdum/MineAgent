package me.sailex.altoclef.tasks.resources;

import me.sailex.altoclef.AltoClefController;
import me.sailex.altoclef.tasks.ResourceTask;
import me.sailex.altoclef.tasks.movement.DefaultGoToDimensionTask;
import me.sailex.altoclef.tasksystem.Task;
import me.sailex.altoclef.util.Dimension;
import me.sailex.altoclef.util.ItemTarget;
import me.sailex.altoclef.util.MiningRequirement;
import me.sailex.altoclef.util.helpers.WorldHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class CollectQuartzTask extends ResourceTask {
   private final int count;

   public CollectQuartzTask(int count) {
      super(Items.QUARTZ, count);
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
      if (WorldHelper.getCurrentDimension(mod) != Dimension.NETHER) {
         this.setDebugState("Going to nether");
         return new DefaultGoToDimensionTask(Dimension.NETHER);
      } else {
         this.setDebugState("Mining");
         return new MineAndCollectTask(new ItemTarget(Items.QUARTZ, this.count), new Block[]{Blocks.NETHER_QUARTZ_ORE}, MiningRequirement.WOOD);
      }
   }

   @Override
   protected void onResourceStop(AltoClefController mod, Task interruptTask) {
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return other instanceof CollectQuartzTask;
   }

   @Override
   protected String toDebugStringName() {
      return "Collecting " + this.count + " quartz";
   }
}
