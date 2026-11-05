package me.sailex.altoclef.multiversion.recipemanager;

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
