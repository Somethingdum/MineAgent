package me.sailex.altoclef.tasks.resources;

import me.sailex.altoclef.AltoClefController;
import me.sailex.altoclef.Debug;
import me.sailex.altoclef.TaskCatalogue;
import me.sailex.altoclef.multiversion.EntityVer;
import me.sailex.altoclef.multiversion.PlayerInventoryVer;
import me.sailex.altoclef.tasks.ResourceTask;
import me.sailex.altoclef.tasks.entity.AbstractDoToEntityTask;
import me.sailex.altoclef.tasksystem.Task;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.Optional;

public class CollectMilkTask extends ResourceTask {
   private final int count;

   public CollectMilkTask(int targetCount) {
      super(Items.MILK_BUCKET, targetCount);
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
      if (!mod.getItemStorage().hasItem(Items.BUCKET)) {
         return TaskCatalogue.getItemTask(Items.BUCKET, 1);
      } else {
         return (Task)(!mod.getEntityTracker().entityFound(Cow.class) && this.isInWrongDimension(mod)
            ? this.getToCorrectDimensionTask(mod)
            : new MilkCowTask());
      }
   }

   @Override
   protected void onResourceStop(AltoClefController mod, Task interruptTask) {
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return other instanceof CollectMilkTask;
   }

   @Override
   protected String toDebugStringName() {
      return "Collecting " + this.count + " milk buckets.";
   }

   static class MilkCowTask extends AbstractDoToEntityTask {
      public MilkCowTask() {
         super(0.0, -1.0, -1.0);
      }

      @Override
      protected boolean isSubEqual(AbstractDoToEntityTask other) {
         return other instanceof MilkCowTask;
      }

      @Override
      protected Task onEntityInteract(AltoClefController mod, Entity entity) {
         if (!mod.getItemStorage().hasItem(Items.BUCKET)) {
            Debug.logWarning("Failed to milk cow because you have no bucket.");
            return null;
         } else {
            if (mod.getSlotHandler().forceEquipItem(Items.BUCKET)) {
               mod.getInventory().setItem(PlayerInventoryVer.getSelectedSlot(mod.getInventory()), new ItemStack(Items.MILK_BUCKET));
            }

            return null;
         }
      }

      @Override
      protected Optional<Entity> getEntityTarget(AltoClefController mod) {
         return mod.getEntityTracker().getClosestEntity(EntityVer.getPos(mod.getPlayer()), Cow.class);
      }

      @Override
      protected String toDebugString() {
         return "Milking Cow";
      }
   }
}
