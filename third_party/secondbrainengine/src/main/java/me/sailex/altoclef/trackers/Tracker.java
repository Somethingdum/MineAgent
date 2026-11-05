package me.sailex.altoclef.trackers;

import me.sailex.altoclef.AltoClefController;

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
