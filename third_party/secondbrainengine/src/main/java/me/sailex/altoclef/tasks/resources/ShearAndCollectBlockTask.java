package me.sailex.altoclef.tasks.resources;

import me.sailex.altoclef.AltoClefController;
import me.sailex.altoclef.BotBehaviour;
import me.sailex.altoclef.TaskCatalogue;
import me.sailex.altoclef.tasksystem.Task;
import me.sailex.altoclef.util.ItemTarget;
import me.sailex.altoclef.util.MiningRequirement;
import me.sailex.altoclef.util.helpers.ItemHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

public class ShearAndCollectBlockTask extends MineAndCollectTask {
   public ShearAndCollectBlockTask(ItemTarget[] itemTargets, Block... blocksToMine) {
      super(itemTargets, blocksToMine, MiningRequirement.HAND);
   }

   public ShearAndCollectBlockTask(Item[] items, int count, Block... blocksToMine) {
      this(new ItemTarget[]{new ItemTarget(items, count)}, blocksToMine);
   }

   public ShearAndCollectBlockTask(Item item, int count, Block... blocksToMine) {
      this(new Item[]{item}, count, blocksToMine);
   }

   @Override
   protected void onStart() {
      BotBehaviour botBehaviour = this.controller.getBehaviour();
      botBehaviour.push();
      botBehaviour.forceUseTool((blockState, itemStack) -> itemStack.getItem() == Items.SHEARS && ItemHelper.areShearsEffective(blockState.getBlock()));
      super.onStart();
   }

   @Override
   protected void onStop(Task interruptTask) {
      this.controller.getBehaviour().pop();
      super.onStop(interruptTask);
   }

   @Override
   protected Task onResourceTick(AltoClefController mod) {
      return (Task)(!mod.getItemStorage().hasItem(Items.SHEARS) ? TaskCatalogue.getItemTask(Items.SHEARS, 1) : super.onResourceTick(mod));
   }
}
