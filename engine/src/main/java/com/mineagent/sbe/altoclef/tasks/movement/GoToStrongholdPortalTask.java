// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/movement/GoToStrongholdPortalTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.movement;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.multiversion.EntityVer;
import com.mineagent.sbe.altoclef.tasksystem.Task;
import com.mineagent.sbe.altoclef.util.helpers.WorldHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

public class GoToStrongholdPortalTask extends Task {
   private LocateStrongholdCoordinatesTask locateCoordsTask;
   private final int targetEyes;
   private final int MINIMUM_EYES = 12;
   private BlockPos strongholdCoordinates;

   public GoToStrongholdPortalTask(int targetEyes) {
      this.targetEyes = targetEyes;
      this.strongholdCoordinates = null;
      this.locateCoordsTask = new LocateStrongholdCoordinatesTask(targetEyes);
   }

   @Override
   protected void onStart() {
   }

   @Override
   protected Task onTick() {
      AltoClefController mod = this.controller;
      if (this.strongholdCoordinates == null) {
         this.strongholdCoordinates = this.locateCoordsTask.getStrongholdCoordinates().orElse(null);
         if (this.strongholdCoordinates == null) {
            if (mod.getItemStorage().getItemCount(Items.ENDER_EYE) < 12 && mod.getEntityTracker().itemDropped(Items.ENDER_EYE)) {
               this.setDebugState("Picking up dropped eye");
               return new PickupDroppedItemTask(Items.ENDER_EYE, 12);
            }

            this.setDebugState("Triangulating stronghold...");
            return this.locateCoordsTask;
         }
      }

      if (EntityVer.getPos(mod.getPlayer()).distanceTo(WorldHelper.toVec3d(this.strongholdCoordinates)) < 10.0
            && !mod.getBlockScanner().anyFound(Blocks.END_PORTAL_FRAME)) {
         mod.log(
               "Something went wrong whilst triangulating the stronghold... either the action got disrupted or the second eye went to a different stronghold");
         mod.log("We will try to triangulate again now...");
         this.strongholdCoordinates = null;
         this.locateCoordsTask = new LocateStrongholdCoordinatesTask(this.targetEyes);
         return null;
      } else {
         this.setDebugState("Searching for Stronghold...");
         return new FastTravelTask(this.strongholdCoordinates, 300, true);
      }
   }

   @Override
   protected void onStop(Task interruptTask) {
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof GoToStrongholdPortalTask;
   }

   @Override
   protected String toDebugString() {
      return "Locating Stronghold";
   }
}
