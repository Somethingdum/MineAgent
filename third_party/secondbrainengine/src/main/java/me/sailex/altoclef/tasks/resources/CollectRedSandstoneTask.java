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

public class CollectRedSandstoneTask extends ResourceTask {
   private final int count;

   public CollectRedSandstoneTask(int targetCount) {
      super(Items.RED_SANDSTONE, targetCount);
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
      if (mod.getItemStorage().getItemCount(Items.RED_SAND) >= 4) {
         int target = mod.getItemStorage().getItemCount(Items.RED_SANDSTONE) + 1;
         ItemTarget s = new ItemTarget(Items.RED_SAND, 1);
         return new CraftInInventoryTask(
            new RecipeTarget(Items.RED_SANDSTONE, target, CraftingRecipe.newShapedRecipe("red_sandstone", new ItemTarget[]{s, s, s, s}, 1))
         );
      } else {
         return new MineAndCollectTask(
               new ItemTarget(Items.RED_SANDSTONE, Items.RED_SAND), new Block[]{Blocks.RED_SANDSTONE, Blocks.RED_SAND}, MiningRequirement.WOOD
            )
            .forceDimension(Dimension.OVERWORLD);
      }
   }

   @Override
   protected void onResourceStop(AltoClefController mod, Task interruptTask) {
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return other instanceof CollectRedSandstoneTask;
   }

   @Override
   protected String toDebugStringName() {
      return "Collecting " + this.count + " red sandstone.";
   }
}
