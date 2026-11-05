// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/resources/CollectFlintTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.resources;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.TaskCatalogue;
import com.mineagent.sbe.altoclef.multiversion.EntityVer;
import com.mineagent.sbe.altoclef.tasks.DoToClosestBlockTask;
import com.mineagent.sbe.altoclef.tasks.ResourceTask;
import com.mineagent.sbe.altoclef.tasks.construction.DestroyBlockTask;
import com.mineagent.sbe.altoclef.tasks.construction.PlaceBlockNearbyTask;
import com.mineagent.sbe.altoclef.tasksystem.Task;
import com.mineagent.sbe.altoclef.util.helpers.WorldHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import java.util.Optional;

public class CollectFlintTask extends ResourceTask {
   private static final float CLOSE_ENOUGH_FLINT = 10.0F;
   private final int count;

   public CollectFlintTask(int targetCount) {
      super(Items.FLINT, targetCount);
      this.count = targetCount;
   }

   @Override
   protected boolean shouldAvoidPickingUp(AltoClefController mod) {
      return false;
   }

   @Override
   protected void onResourceStart(AltoClefController mod) {
   }

   @Override
   protected Task onResourceTick(AltoClefController mod) {
      Optional<BlockPos> closest = mod.getBlockScanner()
         .getNearestBlock(
                 EntityVer.getPos(mod.getPlayer()),
            validGravel -> WorldHelper.fallingBlockSafeToBreak(this.controller, validGravel) && WorldHelper.canBreak(this.controller, validGravel),
            Blocks.GRAVEL
         );
      if (closest.isPresent() && closest.get().closerToCenterThan(EntityVer.getPos(mod.getPlayer()), 10.0)) {
         return new DoToClosestBlockTask(DestroyBlockTask::new, Blocks.GRAVEL);
      } else {
         return (Task)(mod.getItemStorage().hasItem(Items.GRAVEL) ? new PlaceBlockNearbyTask(Blocks.GRAVEL) : TaskCatalogue.getItemTask(Items.GRAVEL, 1));
      }
   }

   @Override
   protected void onResourceStop(AltoClefController mod, Task interruptTask) {
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return other instanceof CollectFlintTask task ? task.count == this.count : false;
   }

   @Override
   protected String toDebugStringName() {
      return "Collect " + this.count + " flint";
   }
}
