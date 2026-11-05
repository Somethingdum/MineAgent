// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/entity/DoToClosestEntityTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.entity;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.multiversion.EntityVer;
import com.mineagent.sbe.altoclef.tasks.AbstractDoToClosestObjectTask;
import com.mineagent.sbe.altoclef.tasksystem.Task;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class DoToClosestEntityTask extends AbstractDoToClosestObjectTask<Entity> {
   private final Class[] targetEntities;
   private final Supplier<Vec3> getOriginPos;
   private final Function<Entity, Task> getTargetTask;
   private final Predicate<Entity> shouldInteractWith;

   public DoToClosestEntityTask(Supplier<Vec3> getOriginSupplier, Function<Entity, Task> getTargetTask, Predicate<Entity> shouldInteractWith, Class... entities) {
      this.getOriginPos = getOriginSupplier;
      this.getTargetTask = getTargetTask;
      this.shouldInteractWith = shouldInteractWith;
      this.targetEntities = entities;
   }

   public DoToClosestEntityTask(Supplier<Vec3> getOriginSupplier, Function<Entity, Task> getTargetTask, Class... entities) {
      this(getOriginSupplier, getTargetTask, entity -> true, entities);
   }

   public DoToClosestEntityTask(Function<Entity, Task> getTargetTask, Predicate<Entity> shouldInteractWith, Class... entities) {
      this((Supplier<Vec3>)null, getTargetTask, shouldInteractWith, entities);
   }

   public DoToClosestEntityTask(Function<Entity, Task> getTargetTask, Class... entities) {
      this((Supplier<Vec3>)null, getTargetTask, entity -> true, entities);
   }

   protected Vec3 getPos(AltoClefController mod, Entity obj) {
      return EntityVer.getPos(obj);
   }

   @Override
   protected Optional<Entity> getClosestTo(AltoClefController mod, Vec3 pos) {
      return !mod.getEntityTracker().entityFound(this.targetEntities)
         ? Optional.empty()
         : mod.getEntityTracker().getClosestEntity(pos, this.shouldInteractWith, this.targetEntities);
   }

   @Override
   protected Vec3 getOriginPos(AltoClefController mod) {
      return this.getOriginPos != null ? this.getOriginPos.get() : EntityVer.getPos(mod.getPlayer());
   }

   protected Task getGoalTask(Entity obj) {
      return this.getTargetTask.apply(obj);
   }

   protected boolean isValid(AltoClefController mod, Entity obj) {
      return obj.isAlive() && mod.getEntityTracker().isEntityReachable(obj) && obj != mod.getEntity();
   }

   @Override
   protected void onStart() {
   }

   @Override
   protected void onStop(Task interruptTask) {
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof DoToClosestEntityTask task ? Arrays.equals((Object[])task.targetEntities, (Object[])this.targetEntities) : false;
   }

   @Override
   protected String toDebugString() {
      return "Doing something to closest entity...";
   }
}
