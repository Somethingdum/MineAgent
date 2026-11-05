package me.sailex.altoclef.tasks.resources;

import me.sailex.altoclef.AltoClefController;
import me.sailex.altoclef.Debug;
import me.sailex.altoclef.tasks.CraftInInventoryTask;
import me.sailex.altoclef.tasks.ResourceTask;
import me.sailex.altoclef.tasksystem.Task;
import me.sailex.altoclef.trackers.storage.ItemStorageTracker;
import me.sailex.altoclef.util.CraftingRecipe;
import me.sailex.altoclef.util.Dimension;
import me.sailex.altoclef.util.ItemTarget;
import me.sailex.altoclef.util.MiningRequirement;
import me.sailex.altoclef.util.RecipeTarget;
import me.sailex.altoclef.util.helpers.ItemHelper;
import net.minecraft.world.item.Item;
import java.util.ArrayList;
import java.util.Arrays;

public class CollectPlanksTask extends ResourceTask {
   private final Item[] planks;
   private final Item[] logs;
   private final int targetCount;
   private boolean logsInNether;

   public CollectPlanksTask(Item[] planks, Item[] logs, int count, boolean logsInNether) {
      super(new ItemTarget(planks, count));
      this.planks = planks;
      this.logs = logs;
      this.targetCount = count;
      this.logsInNether = logsInNether;
   }

   public CollectPlanksTask(int count) {
      this(ItemHelper.PLANKS, ItemHelper.LOG, count, false);
   }

   public CollectPlanksTask(Item plank, Item log, int count) {
      this(new Item[]{plank}, new Item[]{log}, count, false);
   }

   public CollectPlanksTask(Item plank, int count) {
      this(plank, ItemHelper.planksToLog(plank), count);
   }

   private static CraftingRecipe generatePlankRecipe(Item[] logs) {
      return CraftingRecipe.newShapedRecipe("planks", new Item[][]{logs, null, null, null}, 4);
   }

   @Override
   protected double getPickupRange(AltoClefController mod) {
      ItemStorageTracker storage = mod.getItemStorage();
      return storage.getItemCount(ItemHelper.LOG) * 4 > this.targetCount ? 10.0 : 50.0;
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
      int totalInventoryPlankCount = mod.getItemStorage().getItemCount(this.planks);
      int potentialPlanks = totalInventoryPlankCount + mod.getItemStorage().getItemCount(this.logs) * 4;
      if (potentialPlanks >= this.targetCount) {
         for (Item logCheck : this.logs) {
            int count = mod.getItemStorage().getItemCount(logCheck);
            if (count > 0) {
               Item plankCheck = ItemHelper.logToPlanks(logCheck);
               if (plankCheck == null) {
                  Debug.logError("Invalid/Un-convertable log: " + logCheck + " (failed to find corresponding plank)");
               }

               int plankCount = mod.getItemStorage().getItemCount(plankCheck);
               int otherPlankCount = totalInventoryPlankCount - plankCount;
               int targetTotalPlanks = Math.min(count * 4 + plankCount, this.targetCount - otherPlankCount);
               this.setDebugState("We have " + logCheck + ", crafting " + targetTotalPlanks + " planks.");
               return new CraftInInventoryTask(new RecipeTarget(plankCheck, targetTotalPlanks, generatePlankRecipe(this.logs)));
            }
         }
      }

      ArrayList<ItemTarget> blocksTomine = new ArrayList<>(2);
      blocksTomine.add(new ItemTarget(this.logs, mod.getItemStorage().getItemCount(this.logs) + 1));
      if (!mod.getBehaviour().exclusivelyMineLogs()) {
      }

      MineAndCollectTask mineAndCollectTask = new MineAndCollectTask(blocksTomine.toArray(ItemTarget[]::new), MiningRequirement.HAND);
      if (this.logsInNether) {
         mineAndCollectTask.forceDimension(Dimension.NETHER);
      }

      return mineAndCollectTask;
   }

   @Override
   protected void onResourceStop(AltoClefController mod, Task interruptTask) {
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return other instanceof CollectPlanksTask;
   }

   @Override
   protected String toDebugStringName() {
      return "Crafting " + this.targetCount + " planks " + Arrays.toString((Object[])this.planks);
   }

   public CollectPlanksTask logsInNether() {
      this.logsInNether = true;
      return this;
   }
}
