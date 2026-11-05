// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/util/JankCraftingRecipeMapping.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.util;

import com.mineagent.engine.alto.multiversion.RecipeVer;
import com.mineagent.engine.alto.multiversion.recipemanager.RecipeManagerWrapper;
import com.mineagent.engine.alto.multiversion.recipemanager.WrappedRecipeEntry;
import java.util.*;
import java.util.stream.Collectors;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;

/**
 * For crafting table/inventory recipe book crafting, we need to figure out identifiers given a recipe.
 */
public class JankCraftingRecipeMapping {
    private static final HashMap<Item, List<WrappedRecipeEntry>> recipeMapping = new HashMap<>();

    /**
     * Reloads the recipe mapping.
     */
    private static void reloadRecipeMapping() {
        // SMH: the server's own recipes (a client has them from its connection).
        com.mineagent.engine.alto.AltoClef host = com.mineagent.engine.alto.AltoClef.getInstance();

        // Check if the network handler is available
        if (host != null) {
            RecipeManagerWrapper recipes = RecipeManagerWrapper.of(host.getWorld().getRecipeManager());
            ServerLevel world = host.getWorld();

            // Check if the recipe manager is available
            if (recipes != null) {
                for (WrappedRecipeEntry recipe : recipes.values()) {
                    assert world != null;
                    Recipe<?> value = recipe.value();
                    Item output = RecipeVer.getOutput(value,world).getItem();
                    recipeMapping.computeIfAbsent(output, k -> new ArrayList<>()).add(recipe);
                }
            }
        }
    }

    /**
     * Retrieves the mapped recipe for a given output item from the Minecraft crafting recipe.
     *
     * @param recipe The crafting recipe to check against.
     * @param output The output item of the recipe.
     * @return An Optional containing the mapped recipe entry if found, or an empty Optional if not found.
     */
    public static Optional<WrappedRecipeEntry> getMinecraftMappedRecipe(CraftingRecipe recipe, Item output) {
        reloadRecipeMapping();
        // Check if the output item is present in the recipe mapping
        if (recipeMapping.containsKey(output)) {
            // Iterate through all the recipes mapped to the output item
            for (WrappedRecipeEntry checkRecipe : recipeMapping.get(output)) {
                // Create a list of item targets to satisfy
                List<ItemTarget> toSatisfy = Arrays.stream(recipe.getSlots())
                        .filter(itemTarget -> itemTarget != null && !itemTarget.isEmpty())
                        .collect(Collectors.toList());
                // Check if the recipe has ingredients
                if (!checkRecipe.value().getIngredients().isEmpty()) {
                    // Iterate through the ingredients of the recipe
                    for (Ingredient ingredient : checkRecipe.value().getIngredients()) {
                        // Skip empty ingredients
                        if (ingredient.isEmpty()) {
                            continue;
                        }
                        // Iterate through the items to satisfy
                        outer:
                        for (int i = 0; i < toSatisfy.size(); ++i) {
                            ItemTarget target = toSatisfy.get(i);
                            // Check if any of the ingredient's matching stacks matches the item target
                            for (ItemStack stack : ingredient.getItems()) {
                                if (target.matches(stack.getItem())) {
                                    toSatisfy.remove(i);
                                    break outer;
                                }
                            }
                        }
                    }
                }
                // Check if all the item targets have been satisfied
                if (toSatisfy.isEmpty()) {
                    return Optional.of(checkRecipe);
                }
            }
        }
        return Optional.empty();
    }
}
