// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/util/progresscheck/LinearProgressChecker.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.util.progresscheck;

import com.mineagent.sbe.altoclef.util.time.TimerGame;

public class LinearProgressChecker implements IProgressChecker<Double> {
   private final double minProgress;
   private final TimerGame timer;
   private double lastProgress;
   private double currentProgress;
   private boolean first;
   private boolean failed;

   public LinearProgressChecker(double timeout, double minProgress) {
      this.minProgress = minProgress;
      this.timer = new TimerGame(timeout);
      this.reset();
   }

   public void setProgress(Double progress) {
      this.currentProgress = progress;
      if (this.first) {
         this.lastProgress = progress;
         this.first = false;
      }

      if (this.timer.elapsed()) {
         double improvement = progress - this.lastProgress;
         if (improvement < this.minProgress) {
            this.failed = true;
         }

         this.first = false;
         this.timer.reset();
         this.lastProgress = progress;
      }
   }

   @Override
   public boolean failed() {
      return this.failed;
   }

   @Override
   public void reset() {
      this.failed = false;
      this.timer.reset();
      this.first = true;
   }
}
