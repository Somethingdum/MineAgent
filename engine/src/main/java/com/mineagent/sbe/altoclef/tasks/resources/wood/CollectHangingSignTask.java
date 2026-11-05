// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/resources/wood/CollectHangingSignTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.resources.wood;

import com.mineagent.sbe.altoclef.TaskCatalogue;
import com.mineagent.sbe.altoclef.tasks.resources.CraftWithMatchingStrippedLogsTask;
import com.mineagent.sbe.altoclef.util.CraftingRecipe;
import com.mineagent.sbe.altoclef.util.ItemTarget;
import com.mineagent.sbe.altoclef.util.helpers.ItemHelper;
import net.minecraft.world.item.Item;

public class CollectHangingSignTask extends CraftWithMatchingStrippedLogsTask {
   public CollectHangingSignTask(Item[] targets, ItemTarget strippedLogs, int count) {
      super(
         targets, woodItems -> woodItems.hangingSign, createRecipe(strippedLogs), new boolean[]{false, false, false, true, true, true, true, true, true}, count
      );
   }

   public CollectHangingSignTask(Item target, String strippedLogCatalogueName, int count) {
      this(new Item[]{target}, new ItemTarget(strippedLogCatalogueName, 1), count);
   }

   public CollectHangingSignTask(int count) {
      this(ItemHelper.WOOD_HANGING_SIGN, TaskCatalogue.getItemTarget("stripped_logs", 1), count);
   }

   private static CraftingRecipe createRecipe(ItemTarget strippedLogs) {
      ItemTarget chain = TaskCatalogue.getItemTarget("chain", 1);
      return CraftingRecipe.newShapedRecipe(
         new ItemTarget[]{chain, null, chain, strippedLogs, strippedLogs, strippedLogs, strippedLogs, strippedLogs, strippedLogs}, 6
      );
   }
}
