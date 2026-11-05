// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/AbstractDoToClosestObjectTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.multiversion.EntityVer;
import com.mineagent.sbe.altoclef.tasks.movement.TimeoutWanderTask;
import com.mineagent.sbe.altoclef.tasksystem.Task;
import net.minecraft.world.phys.Vec3;
import java.util.HashMap;
import java.util.Optional;
import java.util.OptionalDouble;

public abstract class AbstractDoToClosestObjectTask<T> extends Task {
   private final HashMap<T, CachedHeuristic> heuristicMap = new HashMap<>();
   private T currentlyPursuing = (T)null;
   private boolean wasWandering;
   private Task goalTask = null;

   protected abstract Vec3 getPos(AltoClefController var1, T var2);

   protected abstract Optional<T> getClosestTo(AltoClefController var1, Vec3 var2);

   protected abstract Vec3 getOriginPos(AltoClefController var1);

   protected abstract Task getGoalTask(T var1);

   protected abstract boolean isValid(AltoClefController var1, T var2);

   protected Task getWanderTask(AltoClefController mod) {
      return new TimeoutWanderTask(true);
   }

   public void resetSearch() {
      this.currentlyPursuing = null;
      this.heuristicMap.clear();
      this.goalTask = null;
   }

   public boolean wasWandering() {
      return this.wasWandering;
   }

   private double getCurrentCalculatedHeuristic(AltoClefController mod) {
      OptionalDouble ticksRemainingOp = mod.getBaritone().getPathingBehavior().ticksRemainingInSegment();
      return Double.valueOf(ticksRemainingOp.orElse(Double.valueOf(Double.POSITIVE_INFINITY)));
   }

   @Override
   protected Task onTick() {
      this.wasWandering = false;
      AltoClefController mod = this.controller;
      if (this.currentlyPursuing != null && !this.isValid(mod, this.currentlyPursuing)) {
         this.heuristicMap.remove(this.currentlyPursuing);
         this.currentlyPursuing = null;
      }

      Optional<T> checkNewClosest = this.getClosestTo(mod, this.getOriginPos(mod));
      if (checkNewClosest.isPresent() && !checkNewClosest.get().equals(this.currentlyPursuing)) {
         T newClosest = checkNewClosest.get();
         if (this.currentlyPursuing == null) {
            this.currentlyPursuing = newClosest;
         } else if (this.goalTask != null) {
            this.setDebugState("Moving towards closest...");
            double currentHeuristic = this.getCurrentCalculatedHeuristic(mod);
            double closestDistanceSqr = this.getPos(mod, this.currentlyPursuing).distanceToSqr(EntityVer.getPos(mod.getPlayer()));
            int lastTick = this.controller.getWorld().getServer().getTickCount();
            if (!this.heuristicMap.containsKey(this.currentlyPursuing)) {
               this.heuristicMap.put(this.currentlyPursuing, new CachedHeuristic());
            }

            CachedHeuristic h = this.heuristicMap.get(this.currentlyPursuing);
            h.updateHeuristic(currentHeuristic);
            h.updateDistance(closestDistanceSqr);
            h.setTickAttempted(lastTick);
            if (this.heuristicMap.containsKey(newClosest)) {
               CachedHeuristic maybeReAttempt = this.heuristicMap.get(newClosest);
               double maybeClosestDistance = this.getPos(mod, newClosest).distanceToSqr(EntityVer.getPos(mod.getPlayer()));
               if (maybeReAttempt.getHeuristicValue() < h.getHeuristicValue() || maybeClosestDistance < maybeReAttempt.getClosestDistanceSqr() / 4.0) {
                  this.setDebugState("Retrying old heuristic!");
                  this.currentlyPursuing = newClosest;
                  maybeReAttempt.updateDistance(maybeClosestDistance);
               }
            } else {
               this.setDebugState("Trying out NEW pursuit");
               this.currentlyPursuing = newClosest;
            }
         } else {
            this.setDebugState("Waiting for move task to kick in...");
         }
      }

      if (this.currentlyPursuing != null) {
         this.goalTask = this.getGoalTask(this.currentlyPursuing);
         return this.goalTask;
      } else {
         this.goalTask = null;
         if (checkNewClosest.isEmpty()) {
            this.setDebugState("Waiting for calculations I think (wandering)");
            this.wasWandering = true;
            return this.getWanderTask(mod);
         } else {
            this.setDebugState("Waiting for calculations I think (NOT wandering)");
            return null;
         }
      }
   }

   private static class CachedHeuristic {
      private double closestDistanceSqr;
      private int tickAttempted;
      private double heuristicValue;

      public CachedHeuristic() {
         this.closestDistanceSqr = Double.POSITIVE_INFINITY;
         this.heuristicValue = Double.POSITIVE_INFINITY;
      }

      public CachedHeuristic(double closestDistanceSqr, int tickAttempted, double heuristicValue) {
      }

      public double getHeuristicValue() {
         return this.heuristicValue;
      }

      public void updateHeuristic(double heuristicValue) {
         heuristicValue = Math.min(heuristicValue, heuristicValue);
      }

      public double getClosestDistanceSqr() {
         return this.closestDistanceSqr;
      }

      public void updateDistance(double closestDistanceSqr) {
         closestDistanceSqr = Math.min(closestDistanceSqr, closestDistanceSqr);
      }

      public int getTickAttempted() {
         return this.tickAttempted;
      }

      public void setTickAttempted(int tickAttempted) {
      }
   }
}
