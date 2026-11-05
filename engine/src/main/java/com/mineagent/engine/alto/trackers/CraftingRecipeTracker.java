// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/trackers/CraftingRecipeTracker.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.trackers;

import com.mineagent.engine.alto.AltoClef;
import com.mineagent.engine.alto.multiversion.recipemanager.RecipeManagerWrapper;
import com.mineagent.engine.alto.multiversion.recipemanager.WrappedRecipeEntry;
import com.mineagent.engine.alto.util.RecipeTarget;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

// TODO remove those ugly "ensureUpdate" statements, realistically we only need to update only upon joining a world
public class CraftingRecipeTracker extends Tracker{


    private final HashMap<Item, List<com.mineagent.engine.alto.util.CraftingRecipe>> itemRecipeMap = new HashMap<>();
    private final HashMap<com.mineagent.engine.alto.util.CraftingRecipe, ItemStack> recipeResultMap = new HashMap<>();

    private boolean shouldRebuild;

    public CraftingRecipeTracker(TrackerManager manager) {
        super(manager);
        shouldRebuild = true;
    }

    public List<com.mineagent.engine.alto.util.CraftingRecipe> getRecipeForItem(Item item) {
        ensureUpdated();

        if (!hasRecipeForItem(item)) {
            mod.logWarning("trying to access recipe for unknown item: "+item);
            return null;
        }

        return itemRecipeMap.get(item);
    }

    public com.mineagent.engine.alto.util.CraftingRecipe getFirstRecipeForItem(Item item) {
        ensureUpdated();

        if (!hasRecipeForItem(item)) {
            mod.logWarning("trying to access recipe for unknown item: "+item);
            return null;
        }

        return itemRecipeMap.get(item).get(0);
    }

    public List<RecipeTarget> getRecipeTarget(Item item, int targetCount) {
        ensureUpdated();

        List<RecipeTarget> targets = new ArrayList<>();
        for (com.mineagent.engine.alto.util.CraftingRecipe recipe : getRecipeForItem(item)) {
            targets.add(new RecipeTarget(item, targetCount, recipe));
        }

        return targets;
    }

    public RecipeTarget getFirstRecipeTarget(Item item, int targetCount) {
        ensureUpdated();

        return new RecipeTarget(item, targetCount, getFirstRecipeForItem(item));
    }

    public boolean hasRecipeForItem(Item item) {
        ensureUpdated();
        return itemRecipeMap.containsKey(item);
    }

    public ItemStack getRecipeResult(com.mineagent.engine.alto.util.CraftingRecipe recipe) {
        ensureUpdated();

        if (!hasRecipe(recipe)) {
            mod.logWarning("Trying to get result for unknown recipe: "+recipe);
            return null;
        }
        ItemStack result = recipeResultMap.get(recipe);

        return new ItemStack(result.getItem(), result.getCount());
    }

    public boolean hasRecipe(com.mineagent.engine.alto.util.CraftingRecipe recipe) {
        ensureUpdated();
        return recipeResultMap.containsKey(recipe);
    }


    @Override
    protected void updateState() {
        if (!shouldRebuild) return;

        // rebuild once we are in game
        if (!AltoClef.inGame()) return;

        // SMH: the server's own recipes (a client has them from its connection).
        RecipeManagerWrapper recipeManager = RecipeManagerWrapper.of(AltoClef.getInstance().getWorld().getRecipeManager());

        for (WrappedRecipeEntry recipe : recipeManager.values()) {
            if (!(recipe.value() instanceof net.minecraft.world.item.crafting.CraftingRecipe craftingRecipe)) continue;

            // not implemented for now because it isn't needed (I hope xd)
            if (craftingRecipe instanceof CustomRecipe) continue;

            // the arguments shouldn't be used, we can just pass null
            ItemStack result = new ItemStack(craftingRecipe.getResultItem(null).getItem(), craftingRecipe.getResultItem(null).getCount());

            Item[][] altoclefRecipeItems = getShapedCraftingRecipe(craftingRecipe.getIngredients());

            com.mineagent.engine.alto.util.CraftingRecipe altoclefRecipe = com.mineagent.engine.alto.util.CraftingRecipe.newShapedRecipe(altoclefRecipeItems, result.getCount());

            if (itemRecipeMap.containsKey(result.getItem())) {
                itemRecipeMap.get(result.getItem()).add(altoclefRecipe);
            } else {
                List<com.mineagent.engine.alto.util.CraftingRecipe> recipes = new ArrayList<>();
                recipes.add(altoclefRecipe);

                itemRecipeMap.put(result.getItem(), recipes);
            }

            recipeResultMap.put(altoclefRecipe, result);
        }

        itemRecipeMap.replaceAll((k,v) -> Collections.unmodifiableList(v));

        shouldRebuild = false;
    }

    // TODO adjust for small recipes
    // it is always shaped, but that doesn't matter for shapeless
    // the second dimension of the array is for different types of items (eq. logs)
    private static Item[][] getShapedCraftingRecipe(List<Ingredient> ingredients) {
        Item[][] result = new Item[9][];
        int x = 0;

        for (Ingredient ingredient : ingredients) {
            ItemStack[] stacks = ingredient.getItems();
            Item[] items = new Item[stacks.length];

            for (int i = 0; i < stacks.length; i++) {
                ItemStack stack = stacks[i];
                if (stack.getCount() > 1) {
                    throw new IllegalStateException("recipe needs more then one item on a slot... well... shit (ingredients: " + ingredient + ")");
                }

                items[i] = stack.getItem();
            }

            if (stacks.length != 0) {
                // FIXME this is so stupid, but TaskCatalogue is kinda setup this way, so it would require a rewrite to allow for multiple resource :')
                result[x] = new Item[]{items[0]};
            } else {
                result[x] = null;
            }

            x++;
        }


        return result;
    }

    @Override
    protected void reset() {
       shouldRebuild = true;
       itemRecipeMap.clear();
       recipeResultMap.clear();
    }

    @Override
    protected boolean isDirty() {
        return shouldRebuild;
    }
}
