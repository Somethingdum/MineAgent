// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/trackers/TrackerManager.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.trackers;

import com.mineagent.sbe.altoclef.AltoClefController;

import java.util.ArrayList;

public class TrackerManager {
   private final ArrayList<Tracker> trackers = new ArrayList<>();
   private final AltoClefController mod;
   private boolean wasInGame = false;

   public TrackerManager(AltoClefController mod) {
      this.mod = mod;
   }

   public void tick() {
      boolean inGame = AltoClefController.inGame();
      if (!inGame && this.wasInGame) {
         for (Tracker tracker : this.trackers) {
            tracker.reset();
         }

         this.mod.getChunkTracker().reset(this.mod);
         this.mod.getMiscBlockTracker().reset();
      }

      this.wasInGame = inGame;

      for (Tracker tracker : this.trackers) {
         tracker.setDirty();
      }
   }

   public void addTracker(Tracker tracker) {
      tracker.mod = this.mod;
      this.trackers.add(tracker);
   }

   public AltoClefController getController() {
      return this.mod;
   }
}
