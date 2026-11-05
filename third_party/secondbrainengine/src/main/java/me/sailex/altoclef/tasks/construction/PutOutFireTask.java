package me.sailex.altoclef.tasks.construction;

import me.sailex.altoclef.tasks.InteractWithBlockTask;
import me.sailex.altoclef.tasksystem.Task;
import me.sailex.altoclef.util.ItemTarget;
import me.sailex.automatone.api.utils.input.Input;
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
