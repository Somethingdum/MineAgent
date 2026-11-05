// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/multiversion/item/ItemVer.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.multiversion.item;

import com.mineagent.sbe.altoclef.multiversion.FoodComponentWrapper;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;


public class ItemVer {
   public static FoodComponentWrapper getFoodComponent(Item item) {
       
       return FoodComponentWrapper.of(item.components().get(DataComponents.FOOD));
       
   }

   public static boolean isFood(ItemStack stack) {
      return isFood(stack.getItem());
   }

   public static boolean hasCustomName(ItemStack stack) {
       
       return stack.get(DataComponents.CUSTOM_NAME) != null;
       
   }

   public static boolean isFood(Item item) {
       
       return FoodComponentWrapper.of(item.components().get(DataComponents.FOOD)) != null;
       
   }

//   private static boolean isSuitableFor(Item item, BlockState state) {
//      return item.isSuitableFor(state);
//   }

   private static Item RAW_GOLD() {
      return Items.RAW_GOLD;
   }

   private static Item RAW_IRON() {
      return Items.RAW_IRON;
   }
}
