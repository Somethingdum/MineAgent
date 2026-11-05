// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/util/progresscheck/MovementProgressChecker.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.util.progresscheck;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.multiversion.EntityVer;
import com.mineagent.sbe.altoclef.util.helpers.WorldHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public class MovementProgressChecker {
   private final IProgressChecker<Vec3> distanceChecker;
   private final IProgressChecker<Double> mineChecker;
   public BlockPos lastBreakingBlock = null;

   public MovementProgressChecker(double distanceTimeout, double minDistance, double mineTimeout, double minMineProgress, int attempts) {
      this.distanceChecker = new ProgressCheckerRetry<>(new DistanceProgressChecker(distanceTimeout, minDistance), attempts);
      this.mineChecker = new LinearProgressChecker(mineTimeout, minMineProgress);
   }

   public MovementProgressChecker(double distanceTimeout, double minDistance, double mineTimeout, double minMineProgress) {
      this(distanceTimeout, minDistance, mineTimeout, minMineProgress, 1);
   }

   public MovementProgressChecker(int attempts) {
      this(6.0, 0.1, 10.0, 0.001, attempts);
   }

   public MovementProgressChecker() {
      this(1);
   }

   public boolean check(AltoClefController mod) {
      if (mod.getFoodChain().needsToEat()) {
         this.distanceChecker.reset();
         this.mineChecker.reset();
      }

      if (mod.getControllerExtras().isBreakingBlock()) {
         BlockPos breakBlock = mod.getControllerExtras().getBreakingBlockPos();
         if (this.lastBreakingBlock != null && WorldHelper.isAir(mod.getWorld().getBlockState(this.lastBreakingBlock).getBlock())) {
            this.distanceChecker.reset();
            this.mineChecker.reset();
         }

         this.lastBreakingBlock = breakBlock;
         this.mineChecker.setProgress(0.0);
         return !this.mineChecker.failed();
      } else {
         this.mineChecker.reset();
         this.distanceChecker.setProgress(EntityVer.getPos(mod.getPlayer()));
         return !this.distanceChecker.failed();
      }
   }

   public void reset() {
      this.distanceChecker.reset();
      this.mineChecker.reset();
   }
}
