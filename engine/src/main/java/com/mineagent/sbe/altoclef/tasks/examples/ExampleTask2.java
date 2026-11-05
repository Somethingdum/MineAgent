// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/examples/ExampleTask2.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.examples;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.tasks.movement.GetToBlockTask;
import com.mineagent.sbe.altoclef.tasks.movement.TimeoutWanderTask;
import com.mineagent.sbe.altoclef.tasksystem.Task;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.util.Optional;
import java.util.function.Predicate;

public class ExampleTask2 extends Task {
   private BlockPos target = null;

   @Override
   protected void onStart() {
      AltoClefController mod = this.controller;
      mod.getBehaviour().push();
      mod.getBehaviour().avoidBlockBreaking((Predicate<BlockPos>)(blockPos -> {
         BlockState s = mod.getWorld().getBlockState(blockPos);
         return s.getBlock() == Blocks.OAK_LEAVES || s.getBlock() == Blocks.OAK_LOG;
      }));
   }

   @Override
   protected Task onTick() {
      AltoClefController mod = this.controller;
      if (this.target != null) {
         return new GetToBlockTask(this.target);
      } else if (!mod.getBlockScanner().anyFound(Blocks.OAK_LOG)) {
         return new TimeoutWanderTask();
      } else {
         Optional<BlockPos> nearest = mod.getBlockScanner().getNearestBlock(Blocks.OAK_LOG);
         if (nearest.isPresent()) {
            BlockPos check = new BlockPos((Vec3i)nearest.get());

            while (mod.getWorld().getBlockState(check).getBlock() == Blocks.OAK_LOG || mod.getWorld().getBlockState(check).getBlock() == Blocks.OAK_LEAVES) {
               check = check.above();
            }

            this.target = check;
         }

         return null;
      }
   }

   @Override
   protected void onStop(Task interruptTask) {
      this.controller.getBehaviour().pop();
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof ExampleTask2;
   }

   @Override
   public boolean isFinished() {
      return this.target != null ? this.controller.getPlayer().blockPosition().equals(this.target) : super.isFinished();
   }

   @Override
   protected String toDebugString() {
      return "Standing on a tree";
   }
}
