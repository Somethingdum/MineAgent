package me.sailex.altoclef.util.slots;

import me.sailex.altoclef.multiversion.PlayerInventoryVer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;

public final class PlayerSlot {
   public static final int ARMOR_BOOTS_SLOT_INDEX = 0;
   public static final int ARMOR_LEGGINGS_SLOT_INDEX = 1;
   public static final int ARMOR_CHESTPLATE_SLOT_INDEX = 2;
   public static final int ARMOR_HELMET_SLOT_INDEX = 3;
   public static final int[] ARMOR_SLOTS_INDICES = new int[]{0, 1, 2, 3};
   public static final int OFFHAND_SLOT_INDEX = 0;

   public static Slot getMainSlot(Inventory inventory, int index) {
      return new Slot(PlayerInventoryVer.getMainInventory(inventory), index);
   }

   public static Slot getArmorSlot(Inventory inventory, int armorIndex) {
      return new Slot(PlayerInventoryVer.getArmorSlots(inventory), armorIndex);
   }

   public static Slot getOffhandSlot(Inventory inventory) {
      return new Slot(PlayerInventoryVer.getOffHandStack(inventory), 0);
   }

   public static Slot getEquipSlot(Inventory inventory, EquipmentSlot equipSlot) {
      switch (equipSlot.getType()) {
         case HAND:
            if (equipSlot == EquipmentSlot.MAINHAND) {
               return getMainSlot(inventory, PlayerInventoryVer.getSelectedSlot(inventory));
            }

            return getOffhandSlot(inventory);
          
          case HUMANOID_ARMOR:
          case ANIMAL_ARMOR:
          
            return getArmorSlot(inventory, equipSlot.getIndex());
         default:
            return Slot.UNDEFINED;
      }
   }

   public static Slot getEquipSlot(Inventory inventory) {
      return getEquipSlot(inventory, EquipmentSlot.MAINHAND);
   }
}
