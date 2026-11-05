package me.sailex.altoclef.tasks.resources;

import me.sailex.altoclef.AltoClefController;
import me.sailex.altoclef.tasks.CraftInInventoryTask;
import me.sailex.altoclef.tasks.ResourceTask;
import me.sailex.altoclef.tasksystem.Task;
import me.sailex.altoclef.util.CraftingRecipe;
import me.sailex.altoclef.util.Dimension;
import me.sailex.altoclef.util.ItemTarget;
import me.sailex.altoclef.util.MiningRequirement;
import me.sailex.altoclef.util.RecipeTarget;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class CollectDripstoneBlockTask extends ResourceTask {
   private final int count;

   public CollectDripstoneBlockTask(int targetCount) {
      super(Items.DRIPSTONE_BLOCK, targetCount);
      this.count = targetCount;
   }

   @Override
   protected boolean shouldAvoidPickingUp(AltoClefController mod) {
      return false;
   }

   @Override
   protected void onResourceStart(AltoClefController mod) {
   }

   @Override
   protected Task onResourceTick(AltoClefController mod) {
      if (mod.getItemStorage().getItemCount(Items.POINTED_DRIPSTONE) >= 4) {
         int target = mod.getItemStorage().getItemCount(Items.DRIPSTONE_BLOCK) + 1;
         ItemTarget s = new ItemTarget(Items.POINTED_DRIPSTONE, 1);
         return new CraftInInventoryTask(
            new RecipeTarget(Items.DRIPSTONE_BLOCK, target, CraftingRecipe.newShapedRecipe("dri", new ItemTarget[]{s, s, s, s}, 1))
         );
      } else {
         return new MineAndCollectTask(
               new ItemTarget(Items.DRIPSTONE_BLOCK, Items.POINTED_DRIPSTONE),
               new Block[]{Blocks.DRIPSTONE_BLOCK, Blocks.POINTED_DRIPSTONE},
               MiningRequirement.WOOD
            )
            .forceDimension(Dimension.OVERWORLD);
      }
   }

   @Override
   protected void onResourceStop(AltoClefController mod, Task interruptTask) {
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return other instanceof CollectDripstoneBlockTask;
   }

   @Override
   protected String toDebugStringName() {
      return "Collecting " + this.count + " Dripstone Blocks.";
   }
}
