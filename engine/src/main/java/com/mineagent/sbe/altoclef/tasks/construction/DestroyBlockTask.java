// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/construction/DestroyBlockTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.construction;

import com.mineagent.sbe.altoclef.tasksystem.ITaskRequiresGrounded;
import com.mineagent.sbe.altoclef.tasksystem.Task;
import com.mineagent.sbe.automatone.api.process.IBuilderProcess;
import net.minecraft.core.BlockPos;
import java.util.Objects;

public class DestroyBlockTask extends Task implements ITaskRequiresGrounded {
   private final BlockPos pos;
   private boolean isClear;

   public DestroyBlockTask(BlockPos pos) {
      this.pos = pos;
   }

   @Override
   protected void onStart() {
      this.isClear = false;
      IBuilderProcess builder = this.controller.getBaritone().getBuilderProcess();
      builder.clearArea(this.pos, this.pos);
   }

   @Override
   protected Task onTick() {
      IBuilderProcess builder = this.controller.getBaritone().getBuilderProcess();
      if (!builder.isActive()) {
         this.isClear = true;
         return null;
      } else {
         this.setDebugState("Automatone is breaking the block.");
         return null;
      }
   }

   @Override
   protected void onStop(Task interruptTask) {
      IBuilderProcess builder = this.controller.getBaritone().getBuilderProcess();
      if (builder.isActive()) {
         builder.onLostControl();
      }
   }

   @Override
   public boolean isFinished() {
      return this.isClear || this.controller.getWorld().isEmptyBlock(this.pos);
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof DestroyBlockTask task ? Objects.equals(task.pos, this.pos) : false;
   }

   @Override
   protected String toDebugString() {
      return "Destroying block at " + this.pos.toShortString();
   }
}
