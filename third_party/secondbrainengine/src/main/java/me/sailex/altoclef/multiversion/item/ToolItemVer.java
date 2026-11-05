package me.sailex.altoclef.multiversion.item;

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
