// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/chains/PlayerInteractionFixChain.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.chains;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.Debug;
import com.mineagent.sbe.altoclef.tasksystem.TaskChain;
import com.mineagent.sbe.altoclef.tasksystem.TaskRunner;
import com.mineagent.sbe.altoclef.util.helpers.ItemHelper;
import com.mineagent.sbe.altoclef.util.helpers.StorageHelper;
import com.mineagent.sbe.altoclef.util.slots.PlayerSlot;
import com.mineagent.sbe.altoclef.util.slots.Slot;
import com.mineagent.sbe.altoclef.util.time.TimerGame;
import com.mineagent.sbe.automatone.api.utils.Rotation;
import com.mineagent.sbe.automatone.api.utils.input.Input;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import java.util.Optional;

public class PlayerInteractionFixChain extends TaskChain {
   private final TimerGame stackHeldTimeout = new TimerGame(1.0);
   private final TimerGame generalDuctTapeSwapTimeout = new TimerGame(30.0);
   private final TimerGame shiftDepressTimeout = new TimerGame(10.0);
   private final TimerGame betterToolTimer = new TimerGame(0.0);
   private final TimerGame mouseMovingButScreenOpenTimeout = new TimerGame(1.0);
   private ItemStack lastHandStack = null;
   private Rotation lastLookRotation;

   public PlayerInteractionFixChain(TaskRunner runner) {
      super(runner);
   }

   @Override
   protected void onStop() {
   }

   @Override
   public void onInterrupt(TaskChain other) {
   }

   @Override
   protected void onTick() {
   }

   @Override
   public float getPriority() {
      if (!AltoClefController.inGame()) {
         return Float.NEGATIVE_INFINITY;
      } else {
         AltoClefController mod = this.controller;
         if (mod.getUserTaskChain().isActive() && this.betterToolTimer.elapsed()) {
            this.betterToolTimer.reset();
            if (mod.getControllerExtras().isBreakingBlock()) {
               BlockState state = mod.getWorld().getBlockState(mod.getControllerExtras().getBreakingBlockPos());
               Optional<Slot> bestToolSlot = StorageHelper.getBestToolSlot(mod, state);
               Slot currentEquipped = PlayerSlot.getEquipSlot(this.controller.getInventory());
               if (bestToolSlot.isPresent()
                  && !bestToolSlot.get().equals(currentEquipped)
                  && StorageHelper.getItemStackInSlot(currentEquipped).getItem() != StorageHelper.getItemStackInSlot(bestToolSlot.get()).getItem()) {
                  boolean isAllowedToManage = (!mod.getBaritone().getPathingBehavior().isPathing() || bestToolSlot.get().getInventorySlot() >= 9)
                     && !mod.getFoodChain().isTryingToEat();
                  if (isAllowedToManage) {
                     Debug.logMessage("Found better tool in inventory, equipping.");
                     ItemStack bestToolItemStack = StorageHelper.getItemStackInSlot(bestToolSlot.get());
                     Item bestToolItem = bestToolItemStack.getItem();
                     mod.getSlotHandler().forceEquipItem(bestToolItem);
                  }
               }
            }
         }

         if (mod.getInputControls().isHeldDown(Input.SNEAK)) {
            if (this.shiftDepressTimeout.elapsed()) {
               mod.getInputControls().release(Input.SNEAK);
            }
         } else {
            this.shiftDepressTimeout.reset();
         }

         if (this.generalDuctTapeSwapTimeout.elapsed() && !mod.getControllerExtras().isBreakingBlock()) {
            Debug.logMessage("Refreshed inventory...");
            mod.getSlotHandler().refreshInventory();
            this.generalDuctTapeSwapTimeout.reset();
            return Float.NEGATIVE_INFINITY;
         } else {
            ItemStack currentStack = StorageHelper.getItemStackInCursorSlot(this.controller);
            if (currentStack == null || currentStack.isEmpty()) {
               this.stackHeldTimeout.reset();
               this.lastHandStack = null;
            } else if (this.lastHandStack == null || !ItemStack.matches(currentStack, this.lastHandStack)) {
               this.stackHeldTimeout.reset();
               this.lastHandStack = currentStack.copy();
            }

            if (this.lastHandStack != null && this.stackHeldTimeout.elapsed()) {
               Optional<Slot> moveTo = mod.getItemStorage().getSlotThatCanFitInPlayerInventory(this.lastHandStack, false);
               if (moveTo.isPresent()) {
                  mod.getSlotHandler().clickSlot(moveTo.get(), 0, ClickType.PICKUP);
                  return Float.NEGATIVE_INFINITY;
               } else if (ItemHelper.canThrowAwayStack(mod, StorageHelper.getItemStackInCursorSlot(this.controller))) {
                  mod.getSlotHandler().clickSlot(Slot.UNDEFINED, 0, ClickType.PICKUP);
                  return Float.NEGATIVE_INFINITY;
               } else {
                  Optional<Slot> garbage = StorageHelper.getGarbageSlot(mod);
                  if (garbage.isPresent()) {
                     mod.getSlotHandler().clickSlot(garbage.get(), 0, ClickType.PICKUP);
                     return Float.NEGATIVE_INFINITY;
                  } else {
                     mod.getSlotHandler().clickSlot(Slot.UNDEFINED, 0, ClickType.PICKUP);
                     return Float.NEGATIVE_INFINITY;
                  }
               }
            } else {
               return Float.NEGATIVE_INFINITY;
            }
         }
      }
   }

   @Override
   public boolean isActive() {
      return true;
   }

   @Override
   public String getName() {
      return "Hand Stack Fix Chain";
   }
}
