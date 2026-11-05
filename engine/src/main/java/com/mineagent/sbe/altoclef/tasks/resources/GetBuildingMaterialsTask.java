// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/resources/GetBuildingMaterialsTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.resources;

import com.mineagent.sbe.altoclef.tasksystem.Task;
import com.mineagent.sbe.altoclef.util.ItemTarget;
import com.mineagent.sbe.altoclef.util.MiningRequirement;
import com.mineagent.sbe.altoclef.util.helpers.StorageHelper;
import net.minecraft.world.item.Item;

public class GetBuildingMaterialsTask extends Task {
   private final int count;

   public GetBuildingMaterialsTask(int count) {
      this.count = count;
   }

   @Override
   protected void onStart() {
   }

   @Override
   protected Task onTick() {
      Item[] throwaways = this.controller.getModSettings().getThrowawayItems(this.controller, true);
      return new MineAndCollectTask(new ItemTarget[]{new ItemTarget(throwaways, this.count)}, MiningRequirement.WOOD);
   }

   @Override
   protected void onStop(Task interruptTask) {
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof GetBuildingMaterialsTask task ? task.count == this.count : false;
   }

   @Override
   public boolean isFinished() {
      return StorageHelper.getBuildingMaterialCount(this.controller) >= this.count;
   }

   @Override
   protected String toDebugString() {
      return "Collecting " + this.count + " building materials.";
   }
}
