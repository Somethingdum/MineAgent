// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/movement/LocateDesertTempleTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.movement;

import com.mineagent.sbe.altoclef.tasksystem.Task;
import com.mineagent.sbe.altoclef.util.helpers.WorldHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biomes;

public class LocateDesertTempleTask extends Task {
   private BlockPos finalPos;

   @Override
   protected void onStart() {
   }

   @Override
   protected Task onTick() {
      BlockPos desertTemplePos = WorldHelper.getADesertTemple(this.controller);
      if (desertTemplePos != null) {
         this.finalPos = desertTemplePos.above(14);
      }

      if (this.finalPos != null) {
         this.setDebugState("Going to found desert temple");
         return new GetToBlockTask(this.finalPos, false);
      } else {
         return new SearchWithinBiomeTask(Biomes.DESERT);
      }
   }

   @Override
   protected void onStop(Task interruptTask) {
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof LocateDesertTempleTask;
   }

   @Override
   protected String toDebugString() {
      return "Searchin' for temples";
   }

   @Override
   public boolean isFinished() {
      return this.controller.getPlayer().blockPosition().equals(this.finalPos);
   }
}
