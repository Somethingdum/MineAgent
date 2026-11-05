// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/multiversion/item/ToolItemVer.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.multiversion.item;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TieredItem;


public class ToolItemVer {

    public static boolean isToolItem(final Item item) {
        
        return item instanceof TieredItem;
         
    }

    public static float getAttackDamage(Item toolItem) {
        
        
        return ((TieredItem) toolItem).getTier().getAttackDamageBonus();
        
    }

    public static boolean isArmorItem(Item item) {
        
        return item instanceof ArmorItem;
        
    }

    public static EquipmentSlot getArmorEquipmentSlot(Item armorItem) {
        
        return ((ArmorItem) armorItem).getType().getSlot();
        
    }

    public static boolean isSwordItem(Item item) {
        
        return item instanceof SwordItem;
         
    }

}
