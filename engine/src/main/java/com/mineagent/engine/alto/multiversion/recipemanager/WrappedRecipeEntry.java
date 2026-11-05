// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/multiversion/recipemanager/WrappedRecipeEntry.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.multiversion.recipemanager;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

public record WrappedRecipeEntry(ResourceLocation id, Recipe<?> value) {

    public RecipeHolder<?> asRecipe() {
        return new RecipeHolder<Recipe<?>>(id, value);
    }

}
