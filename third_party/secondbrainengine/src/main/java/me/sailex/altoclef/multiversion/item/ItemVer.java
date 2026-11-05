package me.sailex.altoclef.multiversion.item;

import me.sailex.altoclef.multiversion.FoodComponentWrapper;
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
