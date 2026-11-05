package me.sailex.altoclef.tasks.entity;

import me.sailex.altoclef.AltoClefController;
import me.sailex.altoclef.tasks.movement.GetToEntityTask;
import me.sailex.altoclef.tasks.movement.PickupDroppedItemTask;
import me.sailex.altoclef.tasks.movement.TimeoutWanderTask;
import me.sailex.altoclef.tasks.resources.KillAndLootTask;
import me.sailex.altoclef.tasksystem.Task;
import me.sailex.altoclef.util.ItemTarget;
import me.sailex.altoclef.util.helpers.ItemHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Slime;
import java.util.Optional;

public class HeroTask extends Task {
   @Override
   protected void onStart() {
   }

   @Override
   protected Task onTick() {
      AltoClefController mod = this.controller;
      if (mod.getFoodChain().needsToEat()) {
         this.setDebugState("Eat first.");
         return null;
      } else {
         Optional<Entity> experienceOrb = mod.getEntityTracker().getClosestEntity(ExperienceOrb.class);
         if (experienceOrb.isPresent()) {
            this.setDebugState("Getting experience.");
            return new GetToEntityTask(experienceOrb.get());
         } else {
            assert this.controller.getWorld() != null;

            Iterable<Entity> hostiles = this.controller.getWorld().getAllEntities();
            if (hostiles != null) {
               for (Entity hostile : hostiles) {
                  if (hostile instanceof Monster || hostile instanceof Slime) {
                     Optional<Entity> closestHostile = mod.getEntityTracker().getClosestEntity(hostile.getClass());
                     if (closestHostile.isPresent()) {
                        this.setDebugState("Killing hostiles or picking hostile drops.");
                        return new KillAndLootTask(hostile.getClass(), new ItemTarget(ItemHelper.HOSTILE_MOB_DROPS));
                     }
                  }
               }
            }

            if (mod.getEntityTracker().itemDropped(ItemHelper.HOSTILE_MOB_DROPS)) {
               this.setDebugState("Picking hostile drops.");
               return new PickupDroppedItemTask(new ItemTarget(ItemHelper.HOSTILE_MOB_DROPS), true);
            } else {
               this.setDebugState("Searching for hostile mobs.");
               return new TimeoutWanderTask();
            }
         }
      }
   }

   @Override
   protected void onStop(Task interruptTask) {
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof HeroTask;
   }

   @Override
   protected String toDebugString() {
      return "Killing all hostile mobs.";
   }
}
