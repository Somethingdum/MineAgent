// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/multiversion/recipemanager/CraftingRecipeVer.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.multiversion.recipemanager;

import java.util.Arrays;
import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;

public class CraftingRecipeVer {

    public static ItemStack getResult(CraftingRecipe recipe) {
        
        return recipe.assemble(null, null);
        
    }

    public static List<Ingredient> getIngredients(CraftingRecipe recipe) {
        
        
        return recipe.getIngredients();
         
    }

    public static Item[] getMatchingStacks(Ingredient ingredient) {
        
        
        return Arrays.stream(ingredient.getItems()).map(ItemStack::getItem).toArray(Item[]::new);
         

    }

}
