package me.sailex.altoclef.tasks.resources;

import me.sailex.altoclef.AltoClefController;
import me.sailex.altoclef.Debug;
import me.sailex.altoclef.TaskCatalogue;
import me.sailex.altoclef.tasks.ResourceTask;
import me.sailex.altoclef.tasksystem.Task;
import me.sailex.altoclef.util.CraftingRecipe;
import me.sailex.altoclef.util.ItemTarget;
import me.sailex.altoclef.util.helpers.ItemHelper;
import net.minecraft.world.item.Item;
import java.util.function.Function;

public class CraftWithMatchingPlanksTask extends CraftWithMatchingMaterialsTask {
   private final ItemTarget visualTarget;
   private final Function<ItemHelper.WoodItems, Item> getTargetItem;

   public CraftWithMatchingPlanksTask(
      Item[] validTargets, Function<ItemHelper.WoodItems, Item> getTargetItem, CraftingRecipe recipe, boolean[] sameMask, int count
   ) {
      super(new ItemTarget(validTargets, count), recipe, sameMask);
      this.getTargetItem = getTargetItem;
      this.visualTarget = new ItemTarget(validTargets, count);
   }

   @Override
   protected int getExpectedTotalCountOfSameItem(AltoClefController mod, Item sameItem) {
      return mod.getItemStorage().getItemCount(sameItem) + mod.getItemStorage().getItemCount(ItemHelper.planksToLog(sameItem)) * 4;
   }

   @Override
   protected Task getSpecificSameResourceTask(AltoClefController mod, Item[] toGet) {
      for (Item plankToGet : toGet) {
         Item log = ItemHelper.planksToLog(plankToGet);
         if (mod.getItemStorage().getItemCount(log) >= 1) {
            return TaskCatalogue.getItemTask(plankToGet, 1);
         }
      }

      Debug.logError("CraftWithMatchingPlanks: Should never happen!");
      return null;
   }

   @Override
   protected Item getSpecificItemCorrespondingToMajorityResource(Item majority) {
      for (ItemHelper.WoodItems woodItems : ItemHelper.getWoodItems()) {
         if (woodItems.planks == majority) {
            return this.getTargetItem.apply(woodItems);
         }
      }

      return null;
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return other instanceof CraftWithMatchingPlanksTask task ? task.visualTarget.equals(this.visualTarget) : false;
   }

   @Override
   protected String toDebugStringName() {
      return "Crafting: " + this.visualTarget;
   }

   @Override
   protected boolean shouldAvoidPickingUp(AltoClefController mod) {
      return false;
   }
}
