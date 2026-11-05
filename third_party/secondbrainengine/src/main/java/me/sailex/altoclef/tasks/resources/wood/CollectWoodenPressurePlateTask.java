package me.sailex.altoclef.tasks.resources.wood;

import me.sailex.altoclef.TaskCatalogue;
import me.sailex.altoclef.tasks.resources.CraftWithMatchingPlanksTask;
import me.sailex.altoclef.util.CraftingRecipe;
import me.sailex.altoclef.util.ItemTarget;
import me.sailex.altoclef.util.helpers.ItemHelper;
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
