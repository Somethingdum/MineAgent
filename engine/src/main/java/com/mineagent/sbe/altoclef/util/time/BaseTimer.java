// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/util/time/BaseTimer.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.util.time;

public abstract class BaseTimer {
   private double prevTime = 0.0;
   private double interval;

   protected BaseTimer(double intervalSeconds) {
      this.interval = intervalSeconds;
   }

   public double getDuration() {
      return this.currentTime() - this.prevTime;
   }

   public void setInterval(double interval) {
      this.interval = interval;
   }

   public boolean elapsed() {
      return this.getDuration() > this.interval;
   }

   public void reset() {
      this.prevTime = this.currentTime();
   }

   public void forceElapse() {
      this.prevTime = 0.0;
   }

   protected abstract double currentTime();

   protected void setPrevTimeForce(double toSet) {
      this.prevTime = toSet;
   }

   protected double getPrevTime() {
      return this.prevTime;
   }
}
