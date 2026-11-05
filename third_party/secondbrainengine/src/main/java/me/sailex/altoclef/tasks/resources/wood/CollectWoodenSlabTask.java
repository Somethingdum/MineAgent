package me.sailex.altoclef.tasks.resources.wood;

import me.sailex.altoclef.TaskCatalogue;
import me.sailex.altoclef.tasks.resources.CraftWithMatchingPlanksTask;
import me.sailex.altoclef.util.CraftingRecipe;
import me.sailex.altoclef.util.ItemTarget;
import me.sailex.altoclef.util.helpers.ItemHelper;
import net.minecraft.world.item.Item;

public class CollectWoodenSlabTask extends CraftWithMatchingPlanksTask {
   public CollectWoodenSlabTask(Item[] targets, ItemTarget planks, int count) {
      super(targets, woodItems -> woodItems.slab, createRecipe(planks), new boolean[]{true, true, true, false, false, false, false, false, false}, count);
   }

   public CollectWoodenSlabTask(Item target, String plankCatalogueName, int count) {
      this(new Item[]{target}, new ItemTarget(plankCatalogueName, 1), count);
   }

   public CollectWoodenSlabTask(int count) {
      this(ItemHelper.WOOD_SLAB, TaskCatalogue.getItemTarget("planks", 1), count);
   }

   private static CraftingRecipe createRecipe(ItemTarget planks) {
      ItemTarget o = null;
      return CraftingRecipe.newShapedRecipe(new ItemTarget[]{planks, planks, planks, o, o, o, o, o, o}, 6);
   }
}
