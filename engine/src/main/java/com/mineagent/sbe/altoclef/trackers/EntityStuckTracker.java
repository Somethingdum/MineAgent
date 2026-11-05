// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/trackers/EntityStuckTracker.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.trackers;

import com.mineagent.sbe.altoclef.util.helpers.BaritoneHelper;
import com.mineagent.sbe.altoclef.util.helpers.WorldHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import java.util.HashSet;
import java.util.Set;

public class EntityStuckTracker extends Tracker {
   final float MOB_RANGE = 25.0F;
   private final Set<BlockPos> blockedSpots = new HashSet<>();

   public EntityStuckTracker(TrackerManager manager) {
      super(manager);
   }

   public boolean isBlockedByEntity(BlockPos pos) {
      this.ensureUpdated();
      synchronized (BaritoneHelper.MINECRAFT_LOCK) {
         return this.blockedSpots.contains(pos);
      }
   }

   @Override
   protected synchronized void updateState() {
      synchronized (BaritoneHelper.MINECRAFT_LOCK) {
         this.blockedSpots.clear();
         LivingEntity clientPlayerEntity = this.mod.getEntity();

         for (Entity entity : this.mod.getWorld().getAllEntities()) {
            if (entity != null && entity.isAlive() && !entity.equals(clientPlayerEntity) && clientPlayerEntity.closerThan(entity, 25.0)) {
               AABB b = entity.getBoundingBox();

               for (BlockPos p : WorldHelper.getBlocksTouchingBox(b)) {
                  this.blockedSpots.add(p);
               }
            }
         }
      }
   }

   @Override
   protected void reset() {
      this.blockedSpots.clear();
   }
}
