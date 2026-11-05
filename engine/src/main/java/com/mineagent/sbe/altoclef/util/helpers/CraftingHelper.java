// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/util/helpers/CraftingHelper.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.util.helpers;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.util.CraftingRecipe;
import com.mineagent.sbe.altoclef.util.ItemTarget;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class CraftingHelper {
   public static boolean canCraftItemNow(AltoClefController mod, Item item) {
      List<ItemStack> inventoryItems = new ArrayList<>();

      for (ItemStack stack : mod.getItemStorage().getItemStacksPlayerInventory(true)) {
         inventoryItems.add(new ItemStack(stack.getItem(), stack.getCount()));
      }

      for (CraftingRecipe recipe : mod.getCraftingRecipeTracker().getRecipeForItem(item)) {
         if (canCraftItemNow(mod, new ArrayList<>(inventoryItems), recipe, new HashSet<>())) {
            return true;
         }
      }

      return false;
   }

   private static boolean canCraftItemNow(AltoClefController mod, List<ItemStack> inventoryStacks, CraftingRecipe recipe, HashSet<Item> alreadyChecked) {
      Item recipeResult = mod.getCraftingRecipeTracker().getRecipeResult(recipe).getItem();
      if (alreadyChecked.contains(recipeResult)) {
         return false;
      } else {
         alreadyChecked.add(recipeResult);
         ItemTarget[] targets = recipe.getSlots();
         ItemTarget[] arrayOfItemTarget1 = targets;
         int i = targets.length;
         byte b = 0;

         label64:
         while (b < i) {
            ItemTarget itemTarget = arrayOfItemTarget1[b];
            if (itemTarget == ItemTarget.EMPTY) {
               b++;
            } else {
               for (Item item : itemTarget.getMatches()) {
                  for (ItemStack inventoryStack : inventoryStacks) {
                     if (inventoryStack.getItem() == item && inventoryStack.getCount() >= itemTarget.getTargetCount()) {
                        inventoryStack.setCount(inventoryStack.getCount() - itemTarget.getTargetCount());
                        continue label64;
                     }
                  }
               }

               for (Item item : itemTarget.getMatches()) {
                  if (mod.getCraftingRecipeTracker().hasRecipeForItem(item)) {
                     for (CraftingRecipe newRecipe : mod.getCraftingRecipeTracker().getRecipeForItem(item)) {
                        List<ItemStack> inventoryStacksCopy = new ArrayList<>(inventoryStacks);
                        if (canCraftItemNow(mod, inventoryStacksCopy, newRecipe, new HashSet<>(alreadyChecked))) {
                           inventoryStacks = inventoryStacksCopy;
                           ItemStack result = mod.getCraftingRecipeTracker().getRecipeResult(newRecipe);
                           result.setCount(result.getCount() - 1);
                           inventoryStacksCopy.add(result);
                           continue label64;
                        }
                     }
                  }
               }

               return false;
            }
         }

         return true;
      }
   }
}
