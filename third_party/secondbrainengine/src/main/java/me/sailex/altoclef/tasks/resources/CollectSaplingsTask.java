package me.sailex.altoclef.tasks.resources;

import me.sailex.altoclef.util.ItemTarget;
import me.sailex.altoclef.util.MiningRequirement;
import me.sailex.altoclef.util.helpers.ItemHelper;

public class CollectSaplingsTask extends MineAndCollectTask {
   public CollectSaplingsTask(int count) {
      super(new ItemTarget(ItemHelper.SAPLINGS, count), ItemHelper.SAPLING_SOURCES, MiningRequirement.HAND);
   }
}
