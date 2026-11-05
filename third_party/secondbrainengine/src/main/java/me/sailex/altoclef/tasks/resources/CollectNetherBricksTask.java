package me.sailex.altoclef.tasks.resources;

import me.sailex.altoclef.AltoClefController;
import me.sailex.altoclef.tasks.CraftInInventoryTask;
import me.sailex.altoclef.tasks.ResourceTask;
import me.sailex.altoclef.tasksystem.Task;
import me.sailex.altoclef.util.CraftingRecipe;
import me.sailex.altoclef.util.ItemTarget;
import me.sailex.altoclef.util.MiningRequirement;
import me.sailex.altoclef.util.RecipeTarget;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class CollectNetherBricksTask extends ResourceTask {
   private final int count;

   public CollectNetherBricksTask(int count) {
      super(Items.NETHER_BRICKS, count);
      this.count = count;
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
      if (mod.getBlockScanner().anyFound(Blocks.NETHER_BRICKS)) {
         return new MineAndCollectTask(Items.NETHER_BRICKS, this.count, new Block[]{Blocks.NETHER_BRICKS}, MiningRequirement.WOOD);
      } else {
         ItemTarget b = new ItemTarget(Items.NETHER_BRICK, 1);
         return new CraftInInventoryTask(
            new RecipeTarget(Items.NETHER_BRICK, this.count, CraftingRecipe.newShapedRecipe("nether_brick", new ItemTarget[]{b, b, b, b}, 1))
         );
      }
   }

   @Override
   protected void onResourceStop(AltoClefController mod, Task interruptTask) {
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return other instanceof CollectNetherBricksTask;
   }

   @Override
   protected String toDebugStringName() {
      return "Collecting " + this.count + " nether bricks.";
   }
}
