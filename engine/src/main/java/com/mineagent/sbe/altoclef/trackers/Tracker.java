// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/trackers/Tracker.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.trackers;

import com.mineagent.sbe.altoclef.AltoClefController;

public abstract class Tracker {
   protected AltoClefController mod;
   private boolean dirty = true;

   public Tracker(TrackerManager manager) {
      manager.addTracker(this);
   }

   public void setDirty() {
      this.dirty = true;
   }

   protected boolean isDirty() {
      return this.dirty;
   }

   public void ensureUpdated() {
      if (this.isDirty()) {
         this.updateState();
         this.dirty = false;
      }
   }

   protected abstract void updateState();

   protected abstract void reset();
}
