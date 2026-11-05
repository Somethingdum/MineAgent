// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/resources/SatisfyMiningRequirementTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.resources;

import com.mineagent.sbe.altoclef.TaskCatalogue;
import com.mineagent.sbe.altoclef.tasksystem.Task;
import com.mineagent.sbe.altoclef.util.MiningRequirement;
import com.mineagent.sbe.altoclef.util.helpers.StorageHelper;
import net.minecraft.world.item.Items;

public class SatisfyMiningRequirementTask extends Task {
   private final MiningRequirement requirement;

   public SatisfyMiningRequirementTask(MiningRequirement requirement) {
      this.requirement = requirement;
   }

   @Override
   protected void onStart() {
   }

   @Override
   protected Task onTick() {
      switch (this.requirement) {
         case HAND:
         default:
            return null;
         case WOOD:
            return TaskCatalogue.getItemTask(Items.WOODEN_PICKAXE, 1);
         case STONE:
            return TaskCatalogue.getItemTask(Items.STONE_PICKAXE, 1);
         case IRON:
            return TaskCatalogue.getItemTask(Items.IRON_PICKAXE, 1);
         case DIAMOND:
            return TaskCatalogue.getItemTask(Items.DIAMOND_PICKAXE, 1);
      }
   }

   @Override
   protected void onStop(Task interruptTask) {
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof SatisfyMiningRequirementTask task ? task.requirement == this.requirement : false;
   }

   @Override
   protected String toDebugString() {
      return "Satisfy Mining Req: " + this.requirement;
   }

   @Override
   public boolean isFinished() {
      return StorageHelper.miningRequirementMetInventory(this.controller, this.requirement);
   }
}
