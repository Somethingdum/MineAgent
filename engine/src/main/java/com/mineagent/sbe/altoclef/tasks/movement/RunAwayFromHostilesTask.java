// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/movement/RunAwayFromHostilesTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.movement;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.tasksystem.Task;
import com.mineagent.sbe.altoclef.util.baritone.GoalRunAwayFromEntities;
import com.mineagent.sbe.altoclef.util.helpers.BaritoneHelper;
import com.mineagent.sbe.automatone.api.pathing.goals.Goal;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Skeleton;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RunAwayFromHostilesTask extends CustomBaritoneGoalTask {
   private final double distanceToRun;
   private final boolean includeSkeletons;

   public RunAwayFromHostilesTask(double distance, boolean includeSkeletons) {
      this.distanceToRun = distance;
      this.includeSkeletons = includeSkeletons;
   }

   public RunAwayFromHostilesTask(double distance) {
      this(distance, false);
   }

   @Override
   protected Goal newGoal(AltoClefController mod) {
      mod.getBaritone().getPathingBehavior().forceCancel();
      return new GoalRunAwayFromHostiles(mod, this.distanceToRun);
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof RunAwayFromHostilesTask task ? Math.abs(task.distanceToRun - this.distanceToRun) < 1.0 : false;
   }

   @Override
   protected String toDebugString() {
      return "NIGERUNDAYOO, SUMOOKEYY! distance=" + this.distanceToRun + ", skeletons=" + this.includeSkeletons;
   }

   private class GoalRunAwayFromHostiles extends GoalRunAwayFromEntities {
      public GoalRunAwayFromHostiles(AltoClefController mod, double distance) {
         super(mod, distance, false, 0.8);
      }

      @Override
      protected List<Entity> getEntities(AltoClefController mod) {
         Stream<LivingEntity> stream = mod.getEntityTracker().getHostiles().stream();
         synchronized (BaritoneHelper.MINECRAFT_LOCK) {
            if (!RunAwayFromHostilesTask.this.includeSkeletons) {
               stream = stream.filter(hostile -> !(hostile instanceof Skeleton));
            }

            return stream.collect(Collectors.toList());
         }
      }
   }
}
