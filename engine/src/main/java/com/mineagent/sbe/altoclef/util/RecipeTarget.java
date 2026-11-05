// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/util/RecipeTarget.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.util;

import java.util.Objects;
import net.minecraft.world.item.Item;

public class RecipeTarget {
   private final CraftingRecipe recipe;
   private final Item item;
   private final int targetCount;

   public RecipeTarget(Item item, int targetCount, CraftingRecipe recipe) {
      this.item = item;
      this.targetCount = targetCount;
      this.recipe = recipe;
   }

   public CraftingRecipe getRecipe() {
      return this.recipe;
   }

   public Item getOutputItem() {
      return this.item;
   }

   public int getTargetCount() {
      return this.targetCount;
   }

   @Override
   public String toString() {
      return this.targetCount == 1 ? "Recipe{" + this.item + "}" : "Recipe{" + this.item + " x " + this.targetCount + "}";
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) {
         return true;
      } else if (o != null && this.getClass() == o.getClass()) {
         RecipeTarget that = (RecipeTarget)o;
         return this.targetCount == that.targetCount && this.recipe.equals(that.recipe) && Objects.equals(this.item, that.item);
      } else {
         return false;
      }
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.recipe, this.item);
   }
}
