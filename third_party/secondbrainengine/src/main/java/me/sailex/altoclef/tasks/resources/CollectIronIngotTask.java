package me.sailex.altoclef.tasks.resources;

import me.sailex.altoclef.AltoClefController;
import me.sailex.altoclef.tasks.ResourceTask;
import me.sailex.altoclef.tasks.container.SmeltInFurnaceTask;
import me.sailex.altoclef.tasksystem.Task;
import me.sailex.altoclef.util.ItemTarget;
import me.sailex.altoclef.util.SmeltTarget;
import net.minecraft.world.item.Items;

public class CollectIronIngotTask extends ResourceTask {
   private final int count;

   public CollectIronIngotTask(int count) {
      super(Items.IRON_INGOT, count);
      this.count = count;
   }

   @Override
   protected boolean shouldAvoidPickingUp(AltoClefController mod) {
      return false;
   }

   @Override
   protected void onResourceStart(AltoClefController mod) {
      mod.getBehaviour().push();
   }

   @Override
   protected Task onResourceTick(AltoClefController mod) {
      return new SmeltInFurnaceTask(new SmeltTarget(new ItemTarget(Items.IRON_INGOT, this.count), new ItemTarget(Items.RAW_IRON, this.count)));
   }

   @Override
   protected void onResourceStop(AltoClefController mod, Task interruptTask) {
      mod.getBehaviour().pop();
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return other instanceof CollectIronIngotTask same && same.count == this.count;
   }

   @Override
   protected String toDebugStringName() {
      return "Collecting " + this.count + " iron.";
   }
}
