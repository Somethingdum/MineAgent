package me.sailex.altoclef.trackers.blacklisting;

import me.sailex.altoclef.AltoClefController;
import me.sailex.altoclef.Debug;
import me.sailex.altoclef.multiversion.EntityVer;
import me.sailex.altoclef.util.MiningRequirement;
import me.sailex.altoclef.util.helpers.StorageHelper;
import net.minecraft.world.phys.Vec3;
import java.util.HashMap;

public abstract class AbstractObjectBlacklist<T> {
   private final HashMap<T, BlacklistEntry> entries = new HashMap<>();

   public void blackListItem(AltoClefController mod, T item, int numberOfFailuresAllowed) {
      if (!this.entries.containsKey(item)) {
         BlacklistEntry blacklistEntry = new BlacklistEntry();
         blacklistEntry.numberOfFailuresAllowed = numberOfFailuresAllowed;
         blacklistEntry.numberOfFailures = 0;
         blacklistEntry.bestDistanceSq = Double.POSITIVE_INFINITY;
         blacklistEntry.bestTool = MiningRequirement.HAND;
         this.entries.put(item, blacklistEntry);
      }

      BlacklistEntry entry = this.entries.get(item);
      double newDistance = this.getPos(item).distanceToSqr(EntityVer.getPos(mod.getPlayer()));
      MiningRequirement newTool = StorageHelper.getCurrentMiningRequirement(mod);
      if (newTool.ordinal() > entry.bestTool.ordinal() || newDistance < entry.bestDistanceSq - 1.0) {
         if (newTool.ordinal() > entry.bestTool.ordinal()) {
            entry.bestTool = newTool;
         }

         if (newDistance < entry.bestDistanceSq) {
            entry.bestDistanceSq = newDistance;
         }

         entry.numberOfFailures = 0;
         Debug.logMessage("Blacklist RESET: " + item.toString());
      }

      entry.numberOfFailures++;
      entry.numberOfFailuresAllowed = numberOfFailuresAllowed;
      Debug.logMessage("Blacklist: " + item.toString() + ": Try " + entry.numberOfFailures + " / " + entry.numberOfFailuresAllowed);
   }

   protected abstract Vec3 getPos(T var1);

   public boolean unreachable(T item) {
      if (this.entries.containsKey(item)) {
         BlacklistEntry entry = this.entries.get(item);
         return entry.numberOfFailures > entry.numberOfFailuresAllowed;
      } else {
         return false;
      }
   }

   public void clear() {
      this.entries.clear();
   }

   private static class BlacklistEntry {
      public int numberOfFailuresAllowed;
      public int numberOfFailures;
      public double bestDistanceSq;
      public MiningRequirement bestTool;
   }
}
