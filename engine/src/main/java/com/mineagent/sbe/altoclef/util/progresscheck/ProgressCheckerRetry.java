// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/util/progresscheck/ProgressCheckerRetry.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.util.progresscheck;

public class ProgressCheckerRetry<T> implements IProgressChecker<T> {
   private final IProgressChecker<T> subChecker;
   private final int allowedAttempts;
   private int failCount;

   public ProgressCheckerRetry(IProgressChecker<T> subChecker, int allowedAttempts) {
      this.subChecker = subChecker;
      this.allowedAttempts = allowedAttempts;
   }

   @Override
   public void setProgress(T progress) {
      this.subChecker.setProgress(progress);
      if (this.subChecker.failed()) {
         this.failCount++;
         this.subChecker.reset();
      }
   }

   @Override
   public boolean failed() {
      return this.failCount >= this.allowedAttempts;
   }

   @Override
   public void reset() {
      this.subChecker.reset();
      this.failCount = 0;
   }
}
