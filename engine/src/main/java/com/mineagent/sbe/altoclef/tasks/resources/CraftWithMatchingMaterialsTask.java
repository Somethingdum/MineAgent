// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/resources/CraftWithMatchingMaterialsTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.resources;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.Debug;
import com.mineagent.sbe.altoclef.TaskCatalogue;
import com.mineagent.sbe.altoclef.tasks.CraftInInventoryTask;
import com.mineagent.sbe.altoclef.tasks.ResourceTask;
import com.mineagent.sbe.altoclef.tasks.container.CraftInTableTask;
import com.mineagent.sbe.altoclef.tasksystem.Task;
import com.mineagent.sbe.altoclef.util.CraftingRecipe;
import com.mineagent.sbe.altoclef.util.ItemTarget;
import com.mineagent.sbe.altoclef.util.RecipeTarget;
import net.minecraft.world.item.Item;

public abstract class CraftWithMatchingMaterialsTask extends ResourceTask {
   private final ItemTarget target;
   private final CraftingRecipe recipe;
   private final boolean[] sameMask;
   private final ItemTarget sameResourceTarget;
   private final int sameResourceRequiredCount;
   private final int sameResourcePerRecipe;

   public CraftWithMatchingMaterialsTask(ItemTarget target, CraftingRecipe recipe, boolean[] sameMask) {
      super(target);
      this.target = target;
      this.recipe = recipe;
      this.sameMask = sameMask;
      int sameResourceRequiredCount = 0;
      ItemTarget sameResourceTarget = null;
      if (recipe.getSlotCount() != sameMask.length) {
         Debug.logError("Invalid CraftWithMatchingMaterialsTask constructor parameters: Recipe size must equal \"sameMask\" size.");
      }

      for (int i = 0; i < recipe.getSlotCount(); i++) {
         if (sameMask[i]) {
            sameResourceRequiredCount++;
            sameResourceTarget = recipe.getSlot(i);
         }
      }

      this.sameResourceTarget = sameResourceTarget;
      int craftsNeeded = (int)(1.0 + Math.floor(target.getTargetCount() / recipe.outputCount() - 0.001));
      this.sameResourcePerRecipe = sameResourceRequiredCount;
      this.sameResourceRequiredCount = sameResourceRequiredCount * craftsNeeded;
   }

   private static CraftingRecipe generateSameRecipe(CraftingRecipe diverseRecipe, Item sameItem, boolean[] sameMask) {
      ItemTarget[] result = new ItemTarget[diverseRecipe.getSlotCount()];

      for (int i = 0; i < result.length; i++) {
         if (sameMask[i]) {
            result[i] = new ItemTarget(sameItem, 1);
         } else {
            result[i] = diverseRecipe.getSlot(i);
         }
      }

      return CraftingRecipe.newShapedRecipe(result, diverseRecipe.outputCount());
   }

   @Override
   protected void onResourceStart(AltoClefController mod) {
   }

   @Override
   protected Task onResourceTick(AltoClefController mod) {
      int canCraftTotal = 0;
      int majorityCraftCount = 0;
      Item majorityCraftItem = null;

      for (Item sameCheck : this.sameResourceTarget.getMatches()) {
         int count = this.getExpectedTotalCountOfSameItem(mod, sameCheck);
         int canCraft = count / this.sameResourcePerRecipe * this.recipe.outputCount();
         canCraftTotal += canCraft;
         if (canCraft > majorityCraftCount) {
            majorityCraftCount = canCraft;
            majorityCraftItem = sameCheck;
         }
      }

      int currentTargetCount = mod.getItemStorage().getItemCount(this.target);
      int currentTargetsRequired = this.target.getTargetCount() - currentTargetCount;
      if (canCraftTotal < currentTargetsRequired) {
         return this.getAllSameResourcesTask(mod);
      } else {
         int trueCanCraftTotal = 0;

         for (Item sameCheckx : this.sameResourceTarget.getMatches()) {
            int trueCount = mod.getItemStorage().getItemCount(sameCheckx);
            int trueCanCraft = trueCount / this.sameResourcePerRecipe * this.recipe.outputCount();
            trueCanCraftTotal += trueCanCraft;
         }

         if (trueCanCraftTotal < currentTargetsRequired) {
            return this.getSpecificSameResourceTask(mod, this.sameResourceTarget.getMatches());
         } else {
            CraftingRecipe sameRecipe = generateSameRecipe(this.recipe, majorityCraftItem, this.sameMask);
            int var20 = Math.min(majorityCraftCount, this.target.getTargetCount());
            Item output = this.getSpecificItemCorrespondingToMajorityResource(majorityCraftItem);
            var20 = Math.min(this.target.getTargetCount(), var20 + mod.getItemStorage().getItemCount(output));
            RecipeTarget recipeTarget = new RecipeTarget(output, var20, sameRecipe);
            return (Task)(this.recipe.isBig() ? new CraftInTableTask(recipeTarget) : new CraftInInventoryTask(recipeTarget));
         }
      }
   }

   @Override
   protected void onResourceStop(AltoClefController mod, Task interruptTask) {
   }

   protected Task getAllSameResourcesTask(AltoClefController mod) {
      ItemTarget infinityVersion = new ItemTarget(this.sameResourceTarget, 999999);
      return TaskCatalogue.getItemTask(infinityVersion);
   }

   protected int getExpectedTotalCountOfSameItem(AltoClefController mod, Item sameItem) {
      return mod.getItemStorage().getItemCount(sameItem);
   }

   protected Task getSpecificSameResourceTask(AltoClefController mod, Item[] toGet) {
      Debug.logError("Uh oh!!! getSpecificSameResourceTask should be implemented!!!! Now we're stuck.");
      return null;
   }

   protected abstract Item getSpecificItemCorrespondingToMajorityResource(Item var1);
}
