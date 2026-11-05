package me.sailex.altoclef.tasks.resources.wood;

import me.sailex.altoclef.TaskCatalogue;
import me.sailex.altoclef.tasks.resources.CraftWithMatchingPlanksTask;
import me.sailex.altoclef.util.CraftingRecipe;
import me.sailex.altoclef.util.ItemTarget;
import me.sailex.altoclef.util.helpers.ItemHelper;
import net.minecraft.world.item.Item;

public class CollectFenceTask extends CraftWithMatchingPlanksTask {
   public CollectFenceTask(Item[] targets, ItemTarget planks, int count) {
      super(targets, woodItems -> woodItems.fence, createRecipe(planks), new boolean[]{true, false, true, true, false, true, false, false, false}, count);
   }

   public CollectFenceTask(Item target, String plankCatalogueName, int count) {
      this(new Item[]{target}, new ItemTarget(plankCatalogueName, 1), count);
   }

   public CollectFenceTask(int count) {
      this(ItemHelper.WOOD_FENCE, TaskCatalogue.getItemTarget("planks", 1), count);
   }

   private static CraftingRecipe createRecipe(ItemTarget planks) {
      ItemTarget s = TaskCatalogue.getItemTarget("stick", 1);
      return CraftingRecipe.newShapedRecipe(new ItemTarget[]{planks, s, planks, planks, s, planks, null, null, null}, 3);
   }
}
