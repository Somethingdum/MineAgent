// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/multiversion/recipemanager/RecipeManagerWrapper.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.multiversion.recipemanager;




import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeManager;

public class RecipeManagerWrapper {

    
    
    private final RecipeManager recipeManager;

    public static RecipeManagerWrapper of(RecipeManager recipeManager) {
      return recipeManager == null ? null : new RecipeManagerWrapper(recipeManager);
    }

    private RecipeManagerWrapper(RecipeManager recipeManager) {
      this.recipeManager = recipeManager;
    }
    

   public Collection<WrappedRecipeEntry> values() {
      List<WrappedRecipeEntry> result = new ArrayList<>();

       
       
       for (ResourceLocation id : this.recipeManager.getRecipeIds().toList()) {
         result.add(new WrappedRecipeEntry(id, this.recipeManager.byKey(id).get().value()));
       }
       

      return result;
   }
}
