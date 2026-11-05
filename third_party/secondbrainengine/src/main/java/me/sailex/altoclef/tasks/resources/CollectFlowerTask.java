package me.sailex.altoclef.tasks.resources;

import me.sailex.altoclef.util.ItemTarget;
import me.sailex.altoclef.util.MiningRequirement;
import me.sailex.altoclef.util.helpers.ItemHelper;

public class CollectFlowerTask extends MineAndCollectTask {
   public CollectFlowerTask(int count) {
      super(new ItemTarget(ItemHelper.FLOWER, count), ItemHelper.itemsToBlocks(ItemHelper.FLOWER), MiningRequirement.HAND);
   }
}
