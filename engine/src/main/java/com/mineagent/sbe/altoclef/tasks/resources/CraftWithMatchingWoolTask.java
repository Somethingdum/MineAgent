// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/resources/CraftWithMatchingWoolTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.resources;

import com.mineagent.sbe.altoclef.util.CraftingRecipe;
import com.mineagent.sbe.altoclef.util.ItemTarget;
import com.mineagent.sbe.altoclef.util.helpers.ItemHelper;
import net.minecraft.world.item.Item;
import java.util.function.Function;

public abstract class CraftWithMatchingWoolTask extends CraftWithMatchingMaterialsTask {
   private final Function<ItemHelper.ColorfulItems, Item> getMajorityMaterial;
   private final Function<ItemHelper.ColorfulItems, Item> getTargetItem;

   public CraftWithMatchingWoolTask(
      ItemTarget target,
      Function<ItemHelper.ColorfulItems, Item> getMajorityMaterial,
      Function<ItemHelper.ColorfulItems, Item> getTargetItem,
      CraftingRecipe recipe,
      boolean[] sameMask
   ) {
      super(target, recipe, sameMask);
      this.getMajorityMaterial = getMajorityMaterial;
      this.getTargetItem = getTargetItem;
   }

   @Override
   protected Item getSpecificItemCorrespondingToMajorityResource(Item majority) {
      for (ItemHelper.ColorfulItems colorfulItem : ItemHelper.getColorfulItems()) {
         if (this.getMajorityMaterial.apply(colorfulItem) == majority) {
            return this.getTargetItem.apply(colorfulItem);
         }
      }

      return null;
   }
}
