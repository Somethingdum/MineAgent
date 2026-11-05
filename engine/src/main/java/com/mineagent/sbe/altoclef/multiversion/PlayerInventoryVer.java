// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/multiversion/PlayerInventoryVer.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.multiversion;

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
