package me.sailex.altoclef.tasks.resources.wood;

import me.sailex.altoclef.TaskCatalogue;
import me.sailex.altoclef.tasks.resources.CraftWithMatchingPlanksTask;
import me.sailex.altoclef.util.CraftingRecipe;
import me.sailex.altoclef.util.ItemTarget;
import me.sailex.altoclef.util.helpers.ItemHelper;
import net.minecraft.world.item.Item;

public class CollectSignTask extends CraftWithMatchingPlanksTask {
   public CollectSignTask(Item[] targets, ItemTarget planks, int count) {
      super(targets, woodItems -> woodItems.sign, createRecipe(planks), new boolean[]{true, true, true, true, true, true, false, false, false}, count);
   }

   public CollectSignTask(Item target, String plankCatalogueName, int count) {
      this(new Item[]{target}, new ItemTarget(plankCatalogueName, 1), count);
   }

   public CollectSignTask(int count) {
      this(ItemHelper.WOOD_SIGN, TaskCatalogue.getItemTarget("planks", 1), count);
   }

   private static CraftingRecipe createRecipe(ItemTarget planks) {
      ItemTarget stick = TaskCatalogue.getItemTarget("stick", 1);
      return CraftingRecipe.newShapedRecipe(new ItemTarget[]{planks, planks, planks, planks, planks, planks, null, stick, null}, 3);
   }
}
