// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/misc/LootDesertTempleTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.misc;

import com.mineagent.sbe.altoclef.tasks.construction.DestroyBlockTask;
import com.mineagent.sbe.altoclef.tasks.container.LootContainerTask;
import com.mineagent.sbe.altoclef.tasksystem.Task;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import java.util.List;

public class LootDesertTempleTask extends Task {
   public final Vec3i[] CHEST_POSITIONS_RELATIVE = new Vec3i[]{new Vec3i(2, 0, 0), new Vec3i(-2, 0, 0), new Vec3i(0, 0, 2), new Vec3i(0, 0, -2)};
   private final BlockPos temple;
   private final List<Item> wanted;
   private Task lootTask;
   private short looted = 0;

   public LootDesertTempleTask(BlockPos temple, List<Item> wanted) {
      this.temple = temple;
      this.wanted = wanted;
   }

   @Override
   protected void onStart() {
      this.controller.getBaritoneSettings().blocksToAvoid.get().add(Blocks.STONE_PRESSURE_PLATE);
   }

   @Override
   protected Task onTick() {
      if (this.lootTask != null) {
         if (!this.lootTask.isFinished()) {
            this.setDebugState("Looting a desert temple chest");
            return this.lootTask;
         }

         this.looted++;
      }

      if (this.controller.getWorld().getBlockState(this.temple).getBlock() == Blocks.STONE_PRESSURE_PLATE) {
         this.setDebugState("Breaking pressure plate");
         return new DestroyBlockTask(this.temple);
      } else if (this.looted < 4) {
         this.setDebugState("Looting a desert temple chest");
         this.lootTask = new LootContainerTask(this.temple.offset(this.CHEST_POSITIONS_RELATIVE[this.looted]), this.wanted);
         return this.lootTask;
      } else {
         this.setDebugState("Why is this still running? Report this");
         return null;
      }
   }

   @Override
   protected void onStop(Task task) {
      this.controller.getBaritoneSettings().blocksToAvoid.get().remove(Blocks.STONE_PRESSURE_PLATE);
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof LootDesertTempleTask && ((LootDesertTempleTask)other).getTemplePos() == this.temple;
   }

   @Override
   public boolean isFinished() {
      return this.looted == 4;
   }

   @Override
   protected String toDebugString() {
      return "Looting Desert Temple";
   }

   public BlockPos getTemplePos() {
      return this.temple;
   }
}
