// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/resources/CraftWithMatchingStrippedLogsTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.resources;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.Debug;
import com.mineagent.sbe.altoclef.TaskCatalogue;
import com.mineagent.sbe.altoclef.tasks.ResourceTask;
import com.mineagent.sbe.altoclef.tasksystem.Task;
import com.mineagent.sbe.altoclef.util.CraftingRecipe;
import com.mineagent.sbe.altoclef.util.ItemTarget;
import com.mineagent.sbe.altoclef.util.helpers.ItemHelper;
import net.minecraft.world.item.Item;
import java.util.function.Function;

public class CraftWithMatchingStrippedLogsTask extends CraftWithMatchingMaterialsTask {
   private final ItemTarget visualTarget;
   private final Function<ItemHelper.WoodItems, Item> getTargetItem;

   public CraftWithMatchingStrippedLogsTask(
      Item[] validTargets, Function<ItemHelper.WoodItems, Item> getTargetItem, CraftingRecipe recipe, boolean[] sameMask, int count
   ) {
      super(new ItemTarget(validTargets, count), recipe, sameMask);
      this.getTargetItem = getTargetItem;
      this.visualTarget = new ItemTarget(validTargets, count);
   }

   @Override
   protected Task getSpecificSameResourceTask(AltoClefController mod, Item[] toGet) {
      for (Item strippedLogToGet : toGet) {
         Item log = ItemHelper.strippedToLogs(strippedLogToGet);
         if (mod.getItemStorage().getItemCount(log) >= 1) {
            return TaskCatalogue.getItemTask(strippedLogToGet, 1);
         }
      }

      Debug.logError("CraftWithMatchingStrippedLogs: Should never happen!");
      return null;
   }

   @Override
   protected Item getSpecificItemCorrespondingToMajorityResource(Item majority) {
      for (ItemHelper.WoodItems woodItems : ItemHelper.getWoodItems()) {
         if (woodItems.strippedLog == majority) {
            return this.getTargetItem.apply(woodItems);
         }
      }

      return null;
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return other instanceof CraftWithMatchingStrippedLogsTask task ? task.visualTarget.equals(this.visualTarget) : false;
   }

   @Override
   protected String toDebugStringName() {
      return "Getting: " + this.visualTarget;
   }

   @Override
   protected boolean shouldAvoidPickingUp(AltoClefController mod) {
      return false;
   }
}
