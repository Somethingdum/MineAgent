package me.sailex.altoclef.tasks.resources.wood;

import me.sailex.altoclef.TaskCatalogue;
import me.sailex.altoclef.tasks.resources.CraftWithMatchingStrippedLogsTask;
import me.sailex.altoclef.util.CraftingRecipe;
import me.sailex.altoclef.util.ItemTarget;
import me.sailex.altoclef.util.helpers.ItemHelper;
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
