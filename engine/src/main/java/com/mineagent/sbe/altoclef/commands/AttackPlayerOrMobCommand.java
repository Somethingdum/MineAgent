// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/commands/AttackPlayerOrMobCommand.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.commands;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.commandsystem.Arg;
import com.mineagent.sbe.altoclef.commandsystem.ArgParser;
import com.mineagent.sbe.altoclef.commandsystem.Command;
import com.mineagent.sbe.altoclef.commandsystem.CommandException;
import com.mineagent.sbe.altoclef.eventbus.EventBus;
import com.mineagent.sbe.altoclef.eventbus.Subscription;
import com.mineagent.sbe.altoclef.eventbus.events.EntityDeathEvent;
import com.mineagent.sbe.altoclef.tasks.ResourceTask;
import com.mineagent.sbe.altoclef.tasks.entity.KillEntitiesTask;
import com.mineagent.sbe.altoclef.tasksystem.Task;
import com.mineagent.sbe.altoclef.util.ItemTarget;
import com.mineagent.sbe.altoclef.util.time.TimerGame;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

public class AttackPlayerOrMobCommand extends Command {
   public AttackPlayerOrMobCommand() throws CommandException {
      super(
         "attack",
         "Attacks a specified player or mob. Example usages: attack zombie 5 to attack and kill 5 zombies, attack Player to attack a player with username=Player",
         new Arg<>(String.class, "name"),
         new Arg<>(Integer.class, "count", 1, 1)
      );
   }

   @Override
   protected void call(AltoClefController mod, ArgParser parser) throws CommandException {
      String nameToAttack = parser.get(String.class);
      int countToAttack = parser.get(Integer.class);
      mod.runUserTask(new AttackAndGetDropsTask(nameToAttack, countToAttack), () -> this.finish());
   }

   private static class AttackAndGetDropsTask extends ResourceTask {
      private final String toKill;
      private final Task killTask;
      private int mobsKilledCount;
      private int mobKillTargetCount;
      private TimerGame forceCollectTimer = new TimerGame(2.0);
      private Subscription<EntityDeathEvent> onMobDied;
      private Predicate<Entity> shouldAttackPredicate;
      private Set<Entity> trackedDeadEntities = new HashSet<>();
      private static ItemTarget[] drops = new ItemTarget[]{
         new ItemTarget("rotten_flesh", 9999),
         new ItemTarget("bone", 9999),
         new ItemTarget("string", 9999),
         new ItemTarget("spider_eye", 9999),
         new ItemTarget("gunpowder", 9999),
         new ItemTarget("slime_ball", 9999),
         new ItemTarget("ender_pearl", 9999),
         new ItemTarget("blaze_powder", 9999),
         new ItemTarget("ghast_tear", 9999),
         new ItemTarget("magma_cream", 9999),
         new ItemTarget("ender_eye", 9999),
         new ItemTarget("speckled_melon", 9999),
         new ItemTarget("gold_nugget", 9999),
         new ItemTarget("iron_nugget", 9999),
         new ItemTarget("porkchop", 9999),
         new ItemTarget("beef", 9999),
         new ItemTarget("chicken", 9999),
         new ItemTarget("mutton", 9999),
         new ItemTarget("rabbit", 9999)
      };

      public AttackAndGetDropsTask(String toKill, int killCount) {
         super(drops);
         this.toKill = toKill;
         this.mobKillTargetCount = killCount;
         this.shouldAttackPredicate = entity -> {
            if (this.mobsKilledCount >= this.mobKillTargetCount) {
               return false;
            } else {
               if (entity instanceof Player) {
                  String playerName = entity.getName().getString();
                  if (playerName != null && playerName.equalsIgnoreCase(toKill)) {
                     return true;
                  }
               }

               String name = entity.getType().toShortString();
               return name != null && name.equals(toKill);
            }
         };
         this.killTask = new KillEntitiesTask(this.shouldAttackPredicate);
      }

      @Override
      protected boolean shouldAvoidPickingUp(AltoClefController mod) {
         return false;
      }

      @Override
      protected void onResourceStart(AltoClefController mod) {
         this.forceCollectTimer.reset();
         this.onMobDied = EventBus.subscribe(EntityDeathEvent.class, evt -> {
            Entity diedEntity = evt.entity;
            if (!this.trackedDeadEntities.contains(diedEntity)) {
               if (this.shouldAttackPredicate.test(diedEntity)) {
                  this.markEntityDead(diedEntity);
               }
            }
         });
      }

      private void markEntityDead(Entity entity) {
         this.trackedDeadEntities.add(entity);
         this.mobsKilledCount++;
      }

      @Override
      public boolean isFinished() {
         return this.mobsKilledCount >= this.mobKillTargetCount && this.forceCollectTimer.elapsed();
      }

      @Override
      protected Task onResourceTick(AltoClefController mod) {
         for (Entity entity : mod.getWorld().getAllEntities()) {
            if (!this.trackedDeadEntities.contains(entity) && this.shouldAttackPredicate.test(entity) && !entity.isAlive()) {
               this.markEntityDead(entity);
            }
         }

         this.trackedDeadEntities.removeIf(entityx -> entityx.isAlive());
         if (this.mobsKilledCount < this.mobKillTargetCount) {
            this.forceCollectTimer.reset();
         }

         return this.killTask;
      }

      @Override
      protected void onResourceStop(AltoClefController mod, Task interruptTask) {
         EventBus.unsubscribe(this.onMobDied);
         this.trackedDeadEntities.clear();
      }

      @Override
      protected boolean isEqualResource(ResourceTask other) {
         return !(other instanceof AttackAndGetDropsTask task)
            ? false
            : task.toKill.equals(this.toKill) && task.mobKillTargetCount == this.mobKillTargetCount;
      }

      @Override
      protected String toDebugStringName() {
         return "Attacking and collect items from " + this.toKill + " x " + this.mobKillTargetCount;
      }
   }
}
