// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/util/time/TimerReal.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.util.time;

public class TimerReal extends BaseTimer {
   public TimerReal(double intervalSeconds) {
      super(intervalSeconds);
   }

   @Override
   protected double currentTime() {
      return System.currentTimeMillis() / 1000.0;
   }
}
