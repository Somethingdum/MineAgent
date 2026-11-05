package me.sailex.altoclef.tasks.resources.wood;

import me.sailex.altoclef.TaskCatalogue;
import me.sailex.altoclef.tasks.resources.CraftWithMatchingPlanksTask;
import me.sailex.altoclef.util.CraftingRecipe;
import me.sailex.altoclef.util.ItemTarget;
import me.sailex.altoclef.util.helpers.ItemHelper;
import net.minecraft.world.item.Item;

public class CollectWoodenDoorTask extends CraftWithMatchingPlanksTask {
   public CollectWoodenDoorTask(Item[] targets, ItemTarget planks, int count) {
      super(targets, woodItems -> woodItems.door, createRecipe(planks), new boolean[]{true, true, false, true, true, false, true, true, false}, count);
   }

   public CollectWoodenDoorTask(Item target, String plankCatalogueName, int count) {
      this(new Item[]{target}, new ItemTarget(plankCatalogueName, 1), count);
   }

   public CollectWoodenDoorTask(int count) {
      this(ItemHelper.WOOD_DOOR, TaskCatalogue.getItemTarget("planks", 1), count);
   }

   private static CraftingRecipe createRecipe(ItemTarget planks) {
      ItemTarget o = null;
      return CraftingRecipe.newShapedRecipe(new ItemTarget[]{planks, planks, o, planks, planks, o, planks, planks, o}, 3);
   }
}
