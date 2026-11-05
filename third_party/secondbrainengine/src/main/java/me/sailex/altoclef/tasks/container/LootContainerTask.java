package me.sailex.altoclef.tasks.container;

import me.sailex.altoclef.Debug;
import me.sailex.altoclef.multiversion.EntityVer;
import me.sailex.altoclef.tasks.movement.GetToBlockTask;
import me.sailex.altoclef.tasks.slot.EnsureFreeInventorySlotTask;
import me.sailex.altoclef.tasksystem.Task;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

public class LootContainerTask extends Task {
   private final BlockPos containerPos;
   private final List<Item> targets;
   private final Predicate<ItemStack> check;
   private boolean finished = false;

   public LootContainerTask(BlockPos chestPos, List<Item> items, Predicate<ItemStack> pred) {
      this.containerPos = chestPos;
      this.targets = items;
      this.check = pred;
   }

   public LootContainerTask(BlockPos chestPos, List<Item> items) {
      this(chestPos, items, itemStack -> true);
   }

   @Override
   protected void onStart() {
      this.controller.getBehaviour().push();
      this.controller.getBehaviour().addProtectedItems(this.targets.toArray(new Item[0]));
   }

   @Override
   protected Task onTick() {
      Vec3 pos = EntityVer.getPos(controller.getEntity());
      if (this.finished) {
         return null;
      } else if (!this.containerPos
         .closerThan(
            new Vec3i(
               (int)pos.x, (int)pos.y, (int)pos.z
            ),
            4.5
         )) {
         this.setDebugState("Going to container");
         return new GetToBlockTask(this.containerPos);
      } else if (this.controller.getWorld().getBlockEntity(this.containerPos) instanceof RandomizableContainerBlockEntity container) {
         RandomizableContainerBlockEntity containerInventory = container;
         Inventory playerInventory = this.controller.getInventory();
         this.controller.getItemStorage().containers.WritableCache(this.controller, this.containerPos);
         boolean somethingToLoot = false;
         this.setDebugState("Looting items: " + this.targets);

         for (int i = 0; i < containerInventory.getContainerSize(); i++) {
            ItemStack stack = containerInventory.getItem(i);
            if (!stack.isEmpty() && this.targets.contains(stack.getItem()) && this.check.test(stack)) {
               somethingToLoot = true;
               if (!playerInventory.add(new ItemStack(stack.getItem()))) {
                  this.setDebugState("Inventory is full, ensuring space.");
                  return new EnsureFreeInventorySlotTask();
               }

               if (playerInventory.add(stack.copy())) {
                  containerInventory.setItem(i, ItemStack.EMPTY);
                  container.setChanged();
                  this.controller.getItemStorage().registerSlotAction();
                  return null;
               }

               Debug.logWarning("Failed to insert stack even after checking for space.");
            }
         }

         if (!somethingToLoot) {
            this.setDebugState("Container empty or has no desired items.");
            this.finished = true;
         }

         return null;
      } else {
         Debug.logWarning("Block at " + this.containerPos + " is not a lootable container. Stopping.");
         this.finished = true;
         return null;
      }
   }

   @Override
   public boolean isFinished() {
      return this.finished
         || !this.controller.getChunkTracker().isChunkLoaded(this.containerPos)
         || !(this.controller.getWorld().getBlockEntity(this.containerPos) instanceof RandomizableContainerBlockEntity);
   }

   @Override
   protected void onStop(Task interruptTask) {
      this.controller.getBehaviour().pop();
   }

   @Override
   protected boolean isEqual(Task other) {
      return !(other instanceof LootContainerTask task)
         ? false
         : Objects.equals(task.containerPos, this.containerPos) && new ArrayList<>(task.targets).equals(new ArrayList<>(this.targets));
   }

   @Override
   protected String toDebugString() {
      return "Looting container at " + this.containerPos.toShortString();
   }
}
