// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/control/SlotHandler.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.control;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.Debug;
import com.mineagent.sbe.altoclef.multiversion.PlayerInventoryVer;
import com.mineagent.sbe.altoclef.multiversion.ServerPlayerEntityVer;
import com.mineagent.sbe.altoclef.multiversion.item.ToolItemVer;
import com.mineagent.sbe.altoclef.util.ItemTarget;
import com.mineagent.sbe.altoclef.util.slots.PlayerSlot;
import com.mineagent.sbe.altoclef.util.slots.Slot;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.EmptyMapItem;
import net.minecraft.world.item.EnderEyeItem;
import net.minecraft.world.item.FireworkRocketItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.FoodOnAStickItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.SpawnEggItem;
import java.util.Arrays;
import java.util.function.Predicate;

public class SlotHandler {
   private final AltoClefController controller;
   private ItemStack cursorStack = ItemStack.EMPTY;

   public SlotHandler(AltoClefController controller) {
      this.controller = controller;
   }

   public ItemStack getCursorStack() {
      return this.cursorStack;
   }

   public void setCursorStack(ItemStack stack) {
      this.cursorStack = stack != null && !stack.isEmpty() ? stack : ItemStack.EMPTY;
   }

   public boolean canDoSlotAction() {
      return true;
   }

   public void registerSlotAction() {
      this.controller.getItemStorage().registerSlotAction();
   }

   public void clickSlot(Slot slot, int mouseButton, ClickType type) {
      if (slot != null && !slot.equals(Slot.UNDEFINED)) {
         NonNullList<ItemStack> inventory = slot.getInventory();
         int index = slot.getIndex();
         if (inventory == null) {
            Debug.logWarning("Attempt to click a slot without an inventory: " + slot);
         } else {
            ItemStack slotStack = (ItemStack)inventory.get(index);
            switch (type) {
               case PICKUP:
                  ItemStack temp = this.cursorStack;
                  this.setCursorStack(slotStack);
                  inventory.set(index, temp);
                  break;
               case QUICK_MOVE:
                  Debug.logError("QUICK_MOVE is NYI.");
                  break;
               default:
                  Debug.logWarning("Unsupported SlotActionType: " + type);
            }

            this.registerSlotAction();
         }
      } else {
         if (!this.cursorStack.isEmpty()) {
            ServerPlayerEntityVer.dropStack(this.controller.getEntity(), this.cursorStack.copy());
            this.setCursorStack(ItemStack.EMPTY);
            this.registerSlotAction();
         }
      }
   }

   public void forceEquipItemToOffhand(Item toEquip) {
      Inventory inventory = this.controller.getEntity().getInventory();
      ItemStack offhandStack = inventory.getItem(0);
      if (!offhandStack.is(toEquip)) {
         for (int i = 0; i < PlayerInventoryVer.getMainInventory(inventory).size(); i++) {
            ItemStack potential = PlayerInventoryVer.getMainInventory(inventory).get(i);
            if (potential.is(toEquip)) {
               inventory.setItem(0, potential);
                PlayerInventoryVer.getMainInventory(inventory).set(i, offhandStack);
               this.registerSlotAction();
               return;
            }
         }
      }
   }

   public boolean forceEquipItem(Item[] toEquip) {
      Inventory inventory = this.controller.getEntity().getInventory();
      if (Arrays.stream(toEquip).allMatch(ix -> ix == PlayerInventoryVer.getMainHandStack(inventory).getItem())) {
         return true;
      } else {
         for (int i = 0; i < 9; i++) {
            int finalI = i;
            if (Arrays.stream(toEquip).allMatch(it -> it == inventory.getItem(finalI).getItem())) {
                PlayerInventoryVer.setSelectedSlot(inventory, i);
               this.registerSlotAction();
               return true;
            }
         }

         for (int ix = 9; ix < PlayerInventoryVer.getMainInventory(inventory).size(); ix++) {
            int finalI = ix;
            if (Arrays.stream(toEquip).allMatch(it -> it == inventory.getItem(finalI).getItem())) {
               ItemStack handStack = PlayerInventoryVer.getMainHandStack(inventory);
               inventory.setItem(PlayerInventoryVer.getSelectedSlot(inventory), inventory.getItem(ix));
               inventory.setItem(ix, handStack);
               this.registerSlotAction();
               return true;
            }
         }

         return false;
      }
   }

   public boolean forceEquipItem(Item toEquip) {
      Inventory inventory = this.controller.getEntity().getInventory();
      if (PlayerInventoryVer.getMainHandStack(inventory).is(toEquip)) {
         return true;
      } else {
         for (int i = 0; i < 9; i++) {
            if (inventory.getItem(i).is(toEquip)) {
               PlayerInventoryVer.setSelectedSlot(inventory, i);
               this.registerSlotAction();
               return true;
            }
         }

         for (int ix = 9; ix < PlayerInventoryVer.getMainInventory(inventory).size(); ix++) {
            if (inventory.getItem(ix).is(toEquip)) {
               ItemStack handStack = PlayerInventoryVer.getMainHandStack(inventory);
               inventory.setItem(PlayerInventoryVer.getSelectedSlot(inventory), inventory.getItem(ix));
               inventory.setItem(ix, handStack);
               this.registerSlotAction();
               return true;
            }
         }

         return false;
      }
   }

   public boolean forceDeequip(Predicate<ItemStack> isBad) {
      Inventory inventory = this.controller.getEntity().getInventory();
      ItemStack equip = PlayerInventoryVer.getMainHandStack(inventory);
      if (isBad.test(equip)) {
         int emptySlot = inventory.getFreeSlot();
         if (emptySlot != -1) {
            if (Inventory.isHotbarSlot(emptySlot)) {
                PlayerInventoryVer.setSelectedSlot(inventory, emptySlot);
            } else {
               inventory.setItem(emptySlot, equip);
               inventory.setItem(PlayerInventoryVer.getSelectedSlot(inventory), ItemStack.EMPTY);
            }

            this.registerSlotAction();
            return true;
         } else {
            return false;
         }
      } else {
         return true;
      }
   }

   public boolean forceDeequipHitTool() {
      return this.forceDeequip(stack -> ToolItemVer.isToolItem(stack.getItem()));
   }

   public boolean forceEquipItem(ItemTarget toEquip, boolean unInterruptable) {
      if (toEquip != null && !toEquip.isEmpty()) {
         if (this.controller.getFoodChain().needsToEat() && !unInterruptable) {
            return false;
         } else {
            for (Item item : toEquip.getMatches()) {
               if (this.forceEquipItem(item)) {
                  return true;
               }
            }

            return false;
         }
      } else {
         return this.forceDeequip(stack -> !stack.isEmpty());
      }
   }

   public void refreshInventory() {
   }

   public void forceDeequipRightClickableItem() {
      this.forceDeequip(
         stack -> {
            Item item = stack.getItem();
            return item instanceof BucketItem
               || item instanceof EnderEyeItem
               || item == Items.BOW
               || item == Items.CROSSBOW
               || item == Items.FLINT_AND_STEEL
               || item == Items.FIRE_CHARGE
               || item == Items.ENDER_PEARL
               || item instanceof FireworkRocketItem
               || item instanceof SpawnEggItem
               || item == Items.END_CRYSTAL
               || item == Items.EXPERIENCE_BOTTLE
               || item instanceof PotionItem
               || item == Items.TRIDENT
               || item == Items.WRITABLE_BOOK
               || item == Items.WRITTEN_BOOK
               || item instanceof FishingRodItem
               || item instanceof FoodOnAStickItem
               || item == Items.COMPASS
               || item instanceof EmptyMapItem
               || ToolItemVer.isArmorItem(item)
               || item == Items.LEAD
               || item == Items.SHIELD;
         }
      );
   }

   private void swapSlots(Slot slot, Slot target) {
      ItemStack stack = slot.getStack();
      ItemStack targetStack = target.getStack();
      target.getInventory().set(target.getIndex(), stack);
      slot.getInventory().set(slot.getIndex(), targetStack);
   }

   public void forceEquipSlot(AltoClefController controller, Slot slot) {
      Slot target = PlayerSlot.getEquipSlot(controller.getInventory());
      this.swapSlots(slot, target);
   }

   public void forceEquipArmor(AltoClefController controller, ItemTarget target) {
      Inventory inventory = this.controller.getEntity().getInventory();

      for (Item item : target.getMatches()) {
         if (ToolItemVer.isArmorItem(item)) {
            EquipmentSlot slotType = ToolItemVer.getArmorEquipmentSlot(item);
            if (!controller.getEntity().getItemBySlot(slotType).is(item)) {
               for (int i = 0; i < inventory.getContainerSize(); i++) {
                  ItemStack stackInSlot = inventory.getItem(i);
                  if (stackInSlot.is(item)) {
                     ItemStack currentlyEquipped = controller.getEntity().getItemBySlot(slotType).copy();
                     controller.getEntity().setItemSlot(slotType, stackInSlot.copy());
                     inventory.setItem(i, currentlyEquipped);
                     this.registerSlotAction();
                     break;
                  }
               }
            }
         }
      }
   }
}
