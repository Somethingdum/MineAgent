// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/container/PickupFromContainerTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.container;

import com.mineagent.sbe.altoclef.Debug;
import com.mineagent.sbe.altoclef.multiversion.EntityVer;
import com.mineagent.sbe.altoclef.tasks.movement.GetToBlockTask;
import com.mineagent.sbe.altoclef.tasks.slot.EnsureFreeInventorySlotTask;
import com.mineagent.sbe.altoclef.tasksystem.Task;
import com.mineagent.sbe.altoclef.util.ItemTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import java.util.Arrays;
import java.util.Objects;

public class PickupFromContainerTask extends Task {
   private final BlockPos containerPos;
   private final ItemTarget[] targets;

   public PickupFromContainerTask(BlockPos targetContainer, ItemTarget... targets) {
      this.containerPos = targetContainer;
      this.targets = targets;
   }

   @Override
   protected void onStart() {
   }

   @Override
   protected Task onTick() {
      if (this.isFinished()) {
         return null;
      } else if (!this.containerPos
            .closerThan(
                  new Vec3i(
                        (int) EntityVer.getPos(this.controller.getEntity()).x,
                        (int) EntityVer.getPos(this.controller.getEntity()).y,
                        (int) EntityVer.getPos(this.controller.getEntity()).z),
                  4.5)) {
         return new GetToBlockTask(this.containerPos);
      } else if (!(this.controller.getWorld()
            .getBlockEntity(this.containerPos) instanceof RandomizableContainerBlockEntity container)) {
         Debug.logWarning("Block at " + this.containerPos + " is not a lootable container. Stopping.");
         return null;
      } else {
         RandomizableContainerBlockEntity containerInventory = container;
         Inventory playerInventory = this.controller.getInventory();

         for (ItemTarget target : this.targets) {
            int needed = target.getTargetCount() - this.controller.getItemStorage().getItemCount(target);
            if (needed > 0) {
               for (int i = 0; i < containerInventory.getContainerSize(); i++) {
                  ItemStack stack = containerInventory.getItem(i);
                  if (target.matches(stack.getItem())) {
                     this.setDebugState("Looting " + target);
                     if (!playerInventory.add(new ItemStack(stack.getItem()))) {
                        return new EnsureFreeInventorySlotTask();
                     }

                     ItemStack toMove = stack.copy();
                     int moveAmount = Math.min(toMove.getCount(), needed);
                     toMove.setCount(moveAmount);
                     if (playerInventory.add(toMove)) {
                        stack.shrink(moveAmount);
                        containerInventory.setItem(i, stack);
                        container.setChanged();
                        this.controller.getItemStorage().registerSlotAction();
                     }

                     return null;
                  }
               }
            }
         }

         this.setDebugState("Waiting for items to appear in container or finishing.");
         return null;
      }
   }

   @Override
   protected void onStop(Task interruptTask) {
   }

   @Override
   public boolean isFinished() {
      return Arrays.stream(this.targets).allMatch(target -> this.controller.getItemStorage().getItemCount(target) >= target.getTargetCount());
   }

   @Override
   protected boolean isEqual(Task other) {
      return !(other instanceof PickupFromContainerTask task)
         ? false
         : Objects.equals(task.containerPos, this.containerPos) && Arrays.equals((Object[])task.targets, (Object[])this.targets);
   }

   @Override
   protected String toDebugString() {
      return "Picking up from container at (" + this.containerPos.toShortString() + "): " + Arrays.toString((Object[])this.targets);
   }
}
