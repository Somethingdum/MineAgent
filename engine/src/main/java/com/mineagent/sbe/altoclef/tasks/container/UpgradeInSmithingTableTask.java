// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/container/UpgradeInSmithingTableTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.container;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.TaskCatalogue;
import com.mineagent.sbe.altoclef.multiversion.EntityVer;
import com.mineagent.sbe.altoclef.tasks.ResourceTask;
import com.mineagent.sbe.altoclef.tasks.construction.PlaceBlockNearbyTask;
import com.mineagent.sbe.altoclef.tasks.misc.EquipArmorTask;
import com.mineagent.sbe.altoclef.tasks.movement.GetToBlockTask;
import com.mineagent.sbe.altoclef.tasks.squashed.CataloguedResourceTask;
import com.mineagent.sbe.altoclef.tasksystem.Task;
import com.mineagent.sbe.altoclef.util.ItemTarget;
import com.mineagent.sbe.altoclef.util.helpers.StorageHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import java.util.Optional;

public class UpgradeInSmithingTableTask extends ResourceTask {
   private final ItemTarget tool;
   private final ItemTarget template;
   private final ItemTarget material;
   private final ItemTarget output;
   private BlockPos tablePos = null;

   public UpgradeInSmithingTableTask(ItemTarget tool, ItemTarget material, ItemTarget output) {
      super(output);
      this.tool = new ItemTarget(tool, output.getTargetCount());
      this.material = new ItemTarget(material, output.getTargetCount());
      this.template = new ItemTarget(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, output.getTargetCount());
      this.output = output;
   }

   @Override
   protected boolean shouldAvoidPickingUp(AltoClefController controller) {
      return false;
   }

   @Override
   protected void onResourceStart(AltoClefController controller) {
      controller.getBehaviour().addProtectedItems(this.tool.getMatches());
      controller.getBehaviour().addProtectedItems(this.material.getMatches());
      controller.getBehaviour().addProtectedItems(this.template.getMatches());
      controller.getBehaviour().addProtectedItems(Items.SMITHING_TABLE);
   }

   @Override
   protected Task onResourceTick(AltoClefController controller) {
      int desiredOutputCount = this.output.getTargetCount();
      int currentOutputCount = controller.getItemStorage().getItemCount(this.output);
      if (currentOutputCount >= desiredOutputCount) {
         return null;
      } else {
         int needed = desiredOutputCount - currentOutputCount;
         if (controller.getItemStorage().getItemCount(this.tool) < needed
            || controller.getItemStorage().getItemCount(this.material) < needed
            || controller.getItemStorage().getItemCount(this.template) < needed) {
            this.setDebugState("Getting materials for upgrade");
            return new CataloguedResourceTask(new ItemTarget(this.tool, needed), new ItemTarget(this.material, needed), new ItemTarget(this.template, needed));
         } else if (StorageHelper.isArmorEquipped(controller, this.tool.getMatches())) {
            this.setDebugState("Unequipping armor before upgrading.");
            return new EquipArmorTask(new ItemTarget[0]);
         } else {
            if (this.tablePos == null || !controller.getWorld().getBlockState(this.tablePos).is(Blocks.SMITHING_TABLE)) {
               Optional<BlockPos> nearestTable = controller.getBlockScanner().getNearestBlock(Blocks.SMITHING_TABLE);
               if (!nearestTable.isPresent()) {
                  if (controller.getItemStorage().hasItem(Items.SMITHING_TABLE)) {
                     this.setDebugState("Placing smithing table.");
                     return new PlaceBlockNearbyTask(Blocks.SMITHING_TABLE);
                  }

                  this.setDebugState("Obtaining smithing table.");
                  return TaskCatalogue.getItemTask(Items.SMITHING_TABLE, 1);
               }

               this.tablePos = nearestTable.get();
            }

            if (!this.tablePos
                  .closerThan(
                        new Vec3i((int) EntityVer.getPos(controller.getEntity()).x,
                              (int) EntityVer.getPos(controller.getEntity()).y,
                              (int) EntityVer.getPos(controller.getEntity()).z),
                        4.5)) {
               this.setDebugState("Going to smithing table.");
               return new GetToBlockTask(this.tablePos);
            } else {
               this.setDebugState("Upgrading item...");
               Inventory inventory = this.controller.getInventory();
               inventory.clearOrCountMatchingItems(stack -> this.template.matches(stack.getItem()), 1, inventory);
               inventory.clearOrCountMatchingItems(stack -> this.tool.matches(stack.getItem()), 1, inventory);
               inventory.clearOrCountMatchingItems(stack -> this.material.matches(stack.getItem()), 1, inventory);
               inventory.add(new ItemStack(this.output.getMatches()[0], 1));
               controller.getItemStorage().registerSlotAction();
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
      return !(other instanceof UpgradeInSmithingTableTask task)
         ? false
         : task.tool.equals(this.tool) && task.output.equals(this.output) && task.material.equals(this.material);
   }

   @Override
   protected String toDebugStringName() {
      return "Upgrading in Smithing Table";
   }

   public ItemTarget getMaterials() {
      return this.material;
   }

   public ItemTarget getTools() {
      return this.tool;
   }

   public ItemTarget getTemplate() {
      return this.template;
   }
}
