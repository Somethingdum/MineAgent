package me.sailex.altoclef.tasks.resources;

import me.sailex.altoclef.AltoClefController;
import me.sailex.altoclef.TaskCatalogue;
import me.sailex.altoclef.tasks.DoToClosestBlockTask;
import me.sailex.altoclef.tasks.InteractWithBlockTask;
import me.sailex.altoclef.tasks.ResourceTask;
import me.sailex.altoclef.tasks.construction.DestroyBlockTask;
import me.sailex.altoclef.tasks.construction.PlaceBlockNearbyTask;
import me.sailex.altoclef.tasksystem.Task;
import me.sailex.altoclef.util.ItemTarget;
import me.sailex.altoclef.util.helpers.StorageHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import java.util.Arrays;

public class CarveThenCollectTask extends ResourceTask {
   private final ItemTarget target;
   private final Block[] targetBlocks;
   private final ItemTarget toCarve;
   private final Block[] toCarveBlocks;
   private final ItemTarget carveWith;

   public CarveThenCollectTask(ItemTarget target, Block[] targetBlocks, ItemTarget toCarve, Block[] toCarveBlocks, ItemTarget carveWith) {
      super(target);
      this.target = target;
      this.targetBlocks = targetBlocks;
      this.toCarve = toCarve;
      this.toCarveBlocks = toCarveBlocks;
      this.carveWith = carveWith;
   }

   public CarveThenCollectTask(Item target, int targetCount, Block targetBlock, Item toCarve, Block toCarveBlock, Item carveWith) {
      this(
         new ItemTarget(target, targetCount),
         new Block[]{targetBlock},
         new ItemTarget(toCarve, targetCount),
         new Block[]{toCarveBlock},
         new ItemTarget(carveWith, 1)
      );
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
      if (mod.getBlockScanner().anyFound(this.targetBlocks)) {
         this.setDebugState("Breaking carved/target block");
         return new DoToClosestBlockTask(DestroyBlockTask::new, this.targetBlocks);
      } else if (!StorageHelper.itemTargetsMetInventory(mod, this.carveWith)) {
         this.setDebugState("Collect our carve tool");
         return TaskCatalogue.getItemTask(this.carveWith);
      } else if (mod.getBlockScanner().anyFound(this.toCarveBlocks)) {
         this.setDebugState("Carving block");
         return new DoToClosestBlockTask(blockPos -> new InteractWithBlockTask(this.carveWith, blockPos, false), this.toCarveBlocks);
      } else {
         int neededCarveItems = this.target.getTargetCount() - mod.getItemStorage().getItemCount(this.target);
         int currentCarveItems = mod.getItemStorage().getItemCount(this.toCarve);
         if (neededCarveItems > currentCarveItems) {
            this.setDebugState("Collecting more blocks to carve");
            return TaskCatalogue.getItemTask(this.toCarve);
         } else {
            this.setDebugState("Placing blocks to carve down");
            return new PlaceBlockNearbyTask(this.toCarveBlocks);
         }
      }
   }

   @Override
   protected void onResourceStop(AltoClefController mod, Task interruptTask) {
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return !(other instanceof CarveThenCollectTask task)
         ? false
         : task.target.equals(this.target)
            && task.toCarve.equals(this.toCarve)
            && Arrays.equals((Object[])task.targetBlocks, (Object[])this.targetBlocks)
            && Arrays.equals((Object[])task.toCarveBlocks, (Object[])this.toCarveBlocks);
   }

   @Override
   protected String toDebugStringName() {
      return "Getting after carving: " + this.target;
   }
}
