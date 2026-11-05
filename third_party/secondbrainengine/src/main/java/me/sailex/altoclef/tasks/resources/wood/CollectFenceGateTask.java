package me.sailex.altoclef.tasks.resources.wood;

import me.sailex.altoclef.TaskCatalogue;
import me.sailex.altoclef.tasks.resources.CraftWithMatchingPlanksTask;
import me.sailex.altoclef.util.CraftingRecipe;
import me.sailex.altoclef.util.ItemTarget;
import me.sailex.altoclef.util.helpers.ItemHelper;
import net.minecraft.world.item.Item;

public class CollectFenceGateTask extends CraftWithMatchingPlanksTask {
   public CollectFenceGateTask(Item[] targets, ItemTarget planks, int count) {
      super(targets, woodItems -> woodItems.fenceGate, createRecipe(planks), new boolean[]{false, true, false, false, true, false, false, false, false}, count);
   }

   public CollectFenceGateTask(Item target, String plankCatalogueName, int count) {
      this(new Item[]{target}, new ItemTarget(plankCatalogueName, 1), count);
   }

   public CollectFenceGateTask(int count) {
      this(ItemHelper.WOOD_FENCE_GATE, TaskCatalogue.getItemTarget("planks", 1), count);
   }

   private static CraftingRecipe createRecipe(ItemTarget planks) {
      ItemTarget s = TaskCatalogue.getItemTarget("stick", 1);
      return CraftingRecipe.newShapedRecipe(new ItemTarget[]{s, planks, s, s, planks, s, null, null, null}, 1);
   }
}
