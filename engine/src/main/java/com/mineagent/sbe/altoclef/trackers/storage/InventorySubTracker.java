// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/trackers/storage/InventorySubTracker.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.trackers.storage;

import com.mineagent.sbe.altoclef.multiversion.PlayerInventoryVer;
import com.mineagent.sbe.altoclef.trackers.Tracker;
import com.mineagent.sbe.altoclef.trackers.TrackerManager;
import com.mineagent.sbe.altoclef.util.helpers.ItemHelper;
import com.mineagent.sbe.altoclef.util.slots.Slot;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InventorySubTracker extends Tracker {
   private final Map<Item, List<Integer>> itemToSlotPlayer = new HashMap<>();
   private final Map<Item, Integer> itemCountsPlayer = new HashMap<>();

   public InventorySubTracker(TrackerManager manager) {
      super(manager);
   }

   public int getItemCount(Item... items) {
      this.ensureUpdated();
      int result = 0;
      ItemStack cursorStack = this.mod.getSlotHandler().getCursorStack();

      for (Item item : items) {
         if (cursorStack.is(item)) {
            result += cursorStack.getCount();
         }

         result += this.itemCountsPlayer.getOrDefault(item, 0);
      }

      return result;
   }

   public boolean hasItem(Item... items) {
      this.ensureUpdated();
      ItemStack cursorStack = this.mod.getSlotHandler().getCursorStack();

      for (Item item : items) {
         if (cursorStack.is(item)) {
            return true;
         }

         if (this.itemCountsPlayer.containsKey(item)) {
            return true;
         }
      }

      return false;
   }

   public List<Slot> getSlotsWithItemsPlayerInventory(boolean includeArmor, Item... items) {
      this.ensureUpdated();
      List<Slot> result = new ArrayList<>();
      Inventory inventory = this.mod.getInventory();
      NonNullList<ItemStack> main = PlayerInventoryVer.getMainInventory(inventory);
      for (Item item : items) {
         if (this.itemToSlotPlayer.containsKey(item)) {
            for (Integer index : this.itemToSlotPlayer.get(item)) {
               result.add(new Slot(main, index));
            }
         }
      }

      if (includeArmor) {
         NonNullList<ItemStack> armorStacks = PlayerInventoryVer.getArmorSlots(inventory);
         for (int i = 0; i < armorStacks.size(); i++) {
            ItemStack stack = armorStacks.get(i);
            if (Arrays.stream(items).anyMatch(stack::is)) {
               result.add(new Slot(armorStacks, i));
            }
         }
      }

      NonNullList<ItemStack> offHand = PlayerInventoryVer.getOffHandStack(inventory);
      ItemStack offhandStack = offHand.get(0);
      if (Arrays.stream(items).anyMatch(offhandStack::is)) {
         result.add(new Slot(offHand, 0));
      }

      return result;
   }

   public List<ItemStack> getInventoryStacks() {
      this.ensureUpdated();
      Inventory inventory = this.mod.getInventory();
      List<ItemStack> stacks = new ArrayList<>();
      stacks.addAll(PlayerInventoryVer.getMainInventory(inventory));
      stacks.addAll(PlayerInventoryVer.getArmorSlots(inventory));
      stacks.addAll(PlayerInventoryVer.getOffHandStack(inventory));
      return stacks;
   }

   public List<Slot> getSlotsThatCanFit(ItemStack item, boolean acceptPartial) {
      this.ensureUpdated();
      List<Slot> result = new ArrayList<>();
      Inventory inventory = this.mod.getInventory();
      NonNullList<ItemStack> main = PlayerInventoryVer.getMainInventory(inventory);
      if (item.isStackable()) {
         for (int i = 0; i < main.size(); i++) {
            ItemStack stackInSlot = main.get(i);
            if (ItemHelper.canStackTogether(item, stackInSlot)) {
               int roomLeft = stackInSlot.getMaxStackSize() - stackInSlot.getCount();
               if (acceptPartial || roomLeft >= item.getCount()) {
                  result.add(new Slot(main, i));
               }
            }
         }
      }

      for (int ix = 0; ix < main.size(); ix++) {
         if (main.get(ix).isEmpty()) {
            result.add(new Slot(main, ix));
         }
      }

      return result;
   }

   public boolean hasEmptySlot() {
      this.ensureUpdated();
      return this.itemCountsPlayer.getOrDefault(Items.AIR, 0) > 0;
   }

   @Override
   protected void updateState() {
      this.reset();
      Inventory inventory = this.mod.getInventory();
      if (inventory != null) {
         NonNullList<ItemStack> main = PlayerInventoryVer.getMainInventory(inventory);
         for (int i = 0; i < main.size(); i++) {
            ItemStack stack = main.get(i);
            this.registerItem(stack, i, main);
         }

         NonNullList<ItemStack> armorStacks = PlayerInventoryVer.getArmorSlots(inventory);
         for (int i = 0; i < armorStacks.size(); i++) {
            ItemStack stack = armorStacks.get(i);
            this.registerItem(stack, i, armorStacks);
         }

         NonNullList<ItemStack> offHand = PlayerInventoryVer.getOffHandStack(inventory);
         for (int i = 0; i < offHand.size(); i++) {
            ItemStack stack = offHand.get(i);
            this.registerItem(stack, i, offHand);
         }
      }
   }

   private void registerItem(ItemStack stack, int index, NonNullList<ItemStack> inventory) {
      Item item = stack.isEmpty() ? Items.AIR : stack.getItem();
      int count = stack.getCount();
      this.itemCountsPlayer.put(item, this.itemCountsPlayer.getOrDefault(item, 0) + count);
      if (inventory instanceof NonNullList) {
         this.itemToSlotPlayer.computeIfAbsent(item, k -> new ArrayList<>()).add(index);
      }
   }

   @Override
   protected void reset() {
      this.itemToSlotPlayer.clear();
      this.itemCountsPlayer.clear();
   }
}
