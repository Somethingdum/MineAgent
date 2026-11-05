// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/resources/wood/CollectWoodenPressurePlateTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.resources.wood;

import com.mineagent.sbe.altoclef.TaskCatalogue;
import com.mineagent.sbe.altoclef.tasks.resources.CraftWithMatchingPlanksTask;
import com.mineagent.sbe.altoclef.util.CraftingRecipe;
import com.mineagent.sbe.altoclef.util.ItemTarget;
import com.mineagent.sbe.altoclef.util.helpers.ItemHelper;
import net.minecraft.world.item.Item;

public class CollectWoodenPressurePlateTask extends CraftWithMatchingPlanksTask {
   public CollectWoodenPressurePlateTask(Item[] targets, ItemTarget planks, int count) {
      super(targets, woodItems -> woodItems.pressurePlate, createRecipe(planks), new boolean[]{true, true, false, false}, count);
   }

   public CollectWoodenPressurePlateTask(Item target, String plankCatalogueName, int count) {
      this(new Item[]{target}, new ItemTarget(plankCatalogueName, 1), count);
   }

   public CollectWoodenPressurePlateTask(int count) {
      this(ItemHelper.WOOD_PRESSURE_PLATE, TaskCatalogue.getItemTarget("planks", 1), count);
   }

   private static CraftingRecipe createRecipe(ItemTarget planks) {
      return CraftingRecipe.newShapedRecipe(new ItemTarget[]{planks, planks, null, null}, 1);
   }
}
