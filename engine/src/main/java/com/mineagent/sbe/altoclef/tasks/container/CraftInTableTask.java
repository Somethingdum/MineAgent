// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/container/CraftInTableTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.container;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.Debug;
import com.mineagent.sbe.altoclef.TaskCatalogue;
import com.mineagent.sbe.altoclef.multiversion.EntityVer;
import com.mineagent.sbe.altoclef.tasks.ResourceTask;
import com.mineagent.sbe.altoclef.tasks.construction.PlaceBlockNearbyTask;
import com.mineagent.sbe.altoclef.tasks.movement.GetCloseToBlockTask;
import com.mineagent.sbe.altoclef.tasks.resources.CollectRecipeCataloguedResourcesTask;
import com.mineagent.sbe.altoclef.tasksystem.Task;
import com.mineagent.sbe.altoclef.util.ItemTarget;
import com.mineagent.sbe.altoclef.util.RecipeTarget;
import com.mineagent.sbe.altoclef.util.helpers.StorageHelper;
import com.mineagent.sbe.altoclef.util.time.TimerGame;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import java.util.Arrays;
import java.util.Optional;

public class CraftInTableTask extends ResourceTask {
   private final RecipeTarget[] targets;
   private BlockPos craftingTablePos = null;
   private final TimerGame craftTimer = new TimerGame(2.0);
   private boolean isCrafting = false;

   public CraftInTableTask(RecipeTarget[] targets) {
      super(extractItemTargets(targets));
      this.targets = targets;
   }

   public CraftInTableTask(RecipeTarget target) {
      this(new RecipeTarget[]{target});
   }

   private static ItemTarget[] extractItemTargets(RecipeTarget[] recipeTargets) {
      return Arrays.stream(recipeTargets).map(t -> new ItemTarget(t.getOutputItem(), t.getTargetCount())).toArray(ItemTarget[]::new);
   }

   @Override
   protected boolean shouldAvoidPickingUp(AltoClefController controller) {
      return false;
   }

   @Override
   protected void onResourceStart(AltoClefController controller) {
      controller.getBehaviour().push();

      for (RecipeTarget target : this.targets) {
         for (ItemTarget ingredient : target.getRecipe().getSlots()) {
            if (ingredient != null && !ingredient.isEmpty()) {
               controller.getBehaviour().addProtectedItems(ingredient.getMatches());
            }
         }
      }
   }

   @Override
   protected Task onResourceTick(AltoClefController controller) {
      boolean allDone = Arrays.stream(this.targets)
         .allMatch(targetx -> controller.getItemStorage().getItemCount(targetx.getOutputItem()) >= targetx.getTargetCount());
      if (allDone) {
         return null;
      } else if (!StorageHelper.hasRecipeMaterialsOrTarget(controller, this.targets)) {
         this.setDebugState("Collecting ingredients");
         return new CollectRecipeCataloguedResourcesTask(false, this.targets);
      } else {
         if (this.craftingTablePos == null || !controller.getWorld().getBlockState(this.craftingTablePos).is(Blocks.CRAFTING_TABLE)) {
            // SMH: AltoClef's own rule (DoStuffInContainerTask, CraftInTableTask.getCostToMakeNew).
            net.minecraft.world.phys.Vec3 here = EntityVer.getPos(controller.getEntity());
            double makeNew = controller.getItemStorage().hasItem(com.mineagent.sbe.altoclef.util.helpers.ItemHelper.LOG)
                    || controller.getItemStorage().getItemCount(com.mineagent.sbe.altoclef.util.helpers.ItemHelper.PLANKS) >= 4 ? 10 : 100;
            Optional<BlockPos> nearestTable = controller.getBlockScanner().getNearestBlock(Blocks.CRAFTING_TABLE)
                    .filter(t -> t.closerToCenterThan(here, 40)
                            || com.mineagent.sbe.altoclef.util.helpers.BaritoneHelper.calculateGenericHeuristic(here,
                                    net.minecraft.world.phys.Vec3.atCenterOf(t)) <= makeNew);
            if (nearestTable.isPresent()) {
               this.craftingTablePos = nearestTable.get();
               this.setDebugState("Found crafting table: " + this.craftingTablePos.toShortString());
            } else {
               this.craftingTablePos = null;
               this.setDebugState("Crafting table not found.");
            }
         }

         if (this.craftingTablePos == null) {
            if (controller.getItemStorage().hasItem(Items.CRAFTING_TABLE)) {
               this.setDebugState("Placing crafting table.");
               return new PlaceBlockNearbyTask(Blocks.CRAFTING_TABLE);
            } else {
               this.setDebugState("Obtaining crafting table.");
               return TaskCatalogue.getItemTask(Items.CRAFTING_TABLE, 1);
            }
         } else if (!this.craftingTablePos
            .closerThan(
               new Vec3i((int) EntityVer.getPos(controller.getEntity()).x, (int)EntityVer.getPos(controller.getEntity()).y, (int)EntityVer.getPos(controller.getEntity()).z), 3.5
            )) {
            this.setDebugState("Going to crafting table at: " + this.craftingTablePos.toShortString());
            return new GetCloseToBlockTask(this.craftingTablePos);
         } else {
            this.setDebugState("Crafting...");
            if (!this.isCrafting) {
               this.craftTimer.reset();
               this.isCrafting = true;
            }

            if (!this.craftTimer.elapsed()) {
               return null;
            } else {
               for (RecipeTarget target : this.targets) {
                  int currentAmount = controller.getItemStorage().getItemCount(target.getOutputItem());
                  if (currentAmount < target.getTargetCount()) {
                     int craftsNeeded = (int)Math.ceil((double)(target.getTargetCount() - currentAmount) / target.getRecipe().outputCount());

                     for (int i = 0; i < craftsNeeded; i++) {
                        if (!StorageHelper.hasRecipeMaterialsOrTarget(
                           controller, new RecipeTarget(target.getOutputItem(), target.getRecipe().outputCount(), target.getRecipe())
                        )) {
                           Debug.logWarning("Not enough ingredients to craft, even though the check passed. Aborting.");
                           return new CollectRecipeCataloguedResourcesTask(false, this.targets);
                        }

                        Inventory inventory = this.controller.getInventory();

                        for (ItemTarget ingredient : target.getRecipe().getSlots()) {
                           if (ingredient != null && !ingredient.isEmpty()) {
                              inventory.clearOrCountMatchingItems(stack -> ingredient.matches(stack.getItem()), ingredient.getTargetCount(), inventory);
                           }
                        }

                        ItemStack result = new ItemStack(target.getOutputItem(), target.getRecipe().outputCount());
                        inventory.add(result);
                        controller.getItemStorage().registerSlotAction();
                     }
                  }
               }

               controller.getEntity().swing(InteractionHand.MAIN_HAND);
               return null;
            }
         }
      }
   }

   @Override
   protected void onResourceStop(AltoClefController controller, Task interruptTask) {
      controller.getBehaviour().pop();
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return other instanceof CraftInTableTask task ? Arrays.equals((Object[])task.targets, (Object[])this.targets) : false;
   }

   @Override
   protected String toDebugStringName() {
      return "Craft on table: " + Arrays.toString(Arrays.stream(this.targets).map(t -> t.getOutputItem().getDescription().getString()).toArray());
   }

   public RecipeTarget[] getRecipeTargets() {
      return this.targets;
   }
}
