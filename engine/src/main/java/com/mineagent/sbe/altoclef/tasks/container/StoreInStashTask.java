// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/container/StoreInStashTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.container;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.TaskCatalogue;
import com.mineagent.sbe.altoclef.tasks.movement.GetToXZTask;
import com.mineagent.sbe.altoclef.tasksystem.Task;
import com.mineagent.sbe.altoclef.trackers.storage.ContainerCache;
import com.mineagent.sbe.altoclef.util.BlockRange;
import com.mineagent.sbe.altoclef.util.ItemTarget;
import net.minecraft.core.BlockPos;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Predicate;

public class StoreInStashTask extends Task {
   private final ItemTarget[] toStore;
   private final boolean getIfNotPresent;
   private final BlockRange stashRange;

   public StoreInStashTask(boolean getIfNotPresent, BlockRange stashRange, ItemTarget... toStore) {
      this.getIfNotPresent = getIfNotPresent;
      this.stashRange = stashRange;
      this.toStore = toStore;
   }

   @Override
   protected void onStart() {
   }

   @Override
   protected Task onTick() {
      ItemTarget[] itemsToStore = this.getItemsToStore(this.controller);
      if (itemsToStore.length == 0) {
         return null;
      } else {
         if (this.getIfNotPresent) {
            for (ItemTarget target : this.toStore) {
               if (this.controller.getItemStorage().getItemCount(target) < target.getTargetCount()) {
                  this.setDebugState("Collecting " + target + " before stashing.");
                  return TaskCatalogue.getItemTask(target);
               }
            }
         }

         Optional<BlockPos> closestContainer = this.controller.getBlockScanner().getNearestBlock((Predicate<BlockPos>)(pos -> {
            if (!this.stashRange.contains(this.controller, pos)) {
               return false;
            } else {
               Optional<ContainerCache> cache = this.controller.getItemStorage().getContainerAtPosition(pos);
               return cache.<Boolean>map(containerCache -> !containerCache.isFull()).orElse(true);
            }
         }), StoreInContainerTask.CONTAINER_BLOCKS);
         if (closestContainer.isPresent()) {
            this.setDebugState("Storing in closest stash container.");
            return new StoreInContainerTask(closestContainer.get(), false, itemsToStore);
         } else if (!this.stashRange.contains(this.controller, this.controller.getEntity().blockPosition())) {
            this.setDebugState("Traveling to stash area.");
            BlockPos centerStash = this.stashRange.getCenter();
            return new GetToXZTask(centerStash.getX(), centerStash.getZ());
         } else {
            this.setDebugState("Inside stash, but no non-full containers found. Cannot store items.");
            return null;
         }
      }
   }

   @Override
   public boolean isFinished() {
      return this.getItemsToStore(this.controller).length == 0;
   }

   private ItemTarget[] getItemsToStore(AltoClefController controller) {
      return Arrays.stream(this.toStore).filter(target -> controller.getItemStorage().hasItem(target.getMatches())).toArray(ItemTarget[]::new);
   }

   @Override
   protected void onStop(Task interruptTask) {
   }

   @Override
   protected boolean isEqual(Task other) {
      return !(other instanceof StoreInStashTask task)
         ? false
         : task.stashRange.equals(this.stashRange)
            && task.getIfNotPresent == this.getIfNotPresent
            && Arrays.equals((Object[])task.toStore, (Object[])this.toStore);
   }

   @Override
   protected String toDebugString() {
      return "Storing in stash " + this.stashRange + ": " + Arrays.toString((Object[])this.toStore);
   }
}
