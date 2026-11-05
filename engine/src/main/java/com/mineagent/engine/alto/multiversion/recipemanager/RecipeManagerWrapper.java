// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/multiversion/recipemanager/RecipeManagerWrapper.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.multiversion.recipemanager;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.world.item.crafting.RecipeManager;
import java.util.Collection;

public class RecipeManagerWrapper {

    private final RecipeManager recipeManager;

    public static RecipeManagerWrapper of(RecipeManager recipeManager) {
        if (recipeManager == null) return null;

        return new RecipeManagerWrapper(recipeManager);
    }


    private RecipeManagerWrapper(RecipeManager recipeManager) {
        this.recipeManager = recipeManager;
    }

    public Collection<WrappedRecipeEntry> values() {
        return recipeManager.getRecipes().stream().map(r -> new WrappedRecipeEntry(r.id(),r.value())).collect(Collectors.toSet());
    }



}
