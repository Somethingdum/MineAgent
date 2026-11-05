package me.sailex.altoclef.multiversion;

import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class PlayerInventoryVer {

    public static NonNullList<ItemStack> getOffHandStack(Inventory inv) {
        
        return inv.offhand;
         
    }

    public static NonNullList<ItemStack> getMainInventory(Inventory inv) {
        
        return inv.items;
         
    }

    public static ItemStack getMainHandStack(Inventory inv) {
        
        return inv.getSelected();
         
    }

    public static int getSelectedSlot(Inventory inv) {
        
        return inv.selected;
         
    }

    public static void setSelectedSlot(Inventory inv, int slot) {
        
        inv.selected = slot;
         
    }

    public static NonNullList<ItemStack> getArmorSlots(Inventory inv) {
        
        return inv.armor;
         
    }

}
