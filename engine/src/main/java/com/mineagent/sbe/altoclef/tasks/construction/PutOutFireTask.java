// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/construction/PutOutFireTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.construction;

import com.mineagent.sbe.altoclef.tasks.InteractWithBlockTask;
import com.mineagent.sbe.altoclef.tasksystem.Task;
import com.mineagent.sbe.altoclef.util.ItemTarget;
import com.mineagent.sbe.automatone.api.utils.input.Input;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class PutOutFireTask extends Task {
   private final BlockPos firePosition;

   public PutOutFireTask(BlockPos firePosition) {
      this.firePosition = firePosition;
   }

   @Override
   protected void onStart() {
   }

   @Override
   protected Task onTick() {
      return new InteractWithBlockTask(ItemTarget.EMPTY, null, this.firePosition, Input.CLICK_LEFT, false, false);
   }

   @Override
   protected void onStop(Task interruptTask) {
   }

   @Override
   public boolean isFinished() {
      BlockState s = this.controller.getWorld().getBlockState(this.firePosition);
      return s.getBlock() != Blocks.FIRE && s.getBlock() != Blocks.SOUL_FIRE;
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof PutOutFireTask task ? task.firePosition.equals(this.firePosition) : false;
   }

   @Override
   protected String toDebugString() {
      return "Putting out fire at " + this.firePosition;
   }
}
