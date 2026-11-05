// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/speedrun/beatgame/prioritytask/tasks/MineBlockPriorityTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.speedrun.beatgame.prioritytask.tasks;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.multiversion.EntityVer;
import com.mineagent.sbe.altoclef.tasks.resources.MineAndCollectTask;
import com.mineagent.sbe.altoclef.tasks.speedrun.beatgame.prioritytask.prioritycalculators.DistancePriorityCalculator;
import com.mineagent.sbe.altoclef.tasksystem.Task;
import com.mineagent.sbe.altoclef.util.ItemTarget;
import com.mineagent.sbe.altoclef.util.MiningRequirement;
import com.mineagent.sbe.altoclef.util.helpers.StorageHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;

public class MineBlockPriorityTask extends PriorityTask {
   public final Block[] toMine;
   public final Item[] droppedItem;
   public final ItemTarget[] droppedItemTargets;
   private final MiningRequirement miningRequirement;
   private final DistancePriorityCalculator prioritySupplier;

   public MineBlockPriorityTask(Block[] toMine, Item[] droppedItem, MiningRequirement miningRequirement, DistancePriorityCalculator prioritySupplier) {
      this(toMine, droppedItem, miningRequirement, prioritySupplier, false, true, false);
   }

   public MineBlockPriorityTask(
      Block[] toMine,
      Item[] droppedItem,
      MiningRequirement miningRequirement,
      DistancePriorityCalculator prioritySupplier,
      Function<AltoClefController, Boolean> canCall
   ) {
      this(toMine, droppedItem, miningRequirement, prioritySupplier, canCall, false, true, false);
   }

   public MineBlockPriorityTask(
      Block[] toMine,
      Item[] droppedItem,
      MiningRequirement miningRequirement,
      DistancePriorityCalculator prioritySupplier,
      boolean shouldForce,
      boolean canCache,
      boolean bypassForceCooldown
   ) {
      this(toMine, droppedItem, miningRequirement, prioritySupplier, mod -> true, shouldForce, canCache, bypassForceCooldown);
   }

   public MineBlockPriorityTask(
      Block[] toMine,
      Item[] droppedItem,
      MiningRequirement miningRequirement,
      DistancePriorityCalculator prioritySupplier,
      Function<AltoClefController, Boolean> canCall,
      boolean shouldForce,
      boolean canCache,
      boolean bypassForceCooldown
   ) {
      super(canCall, shouldForce, canCache, bypassForceCooldown);
      this.toMine = toMine;
      this.droppedItem = droppedItem;
      this.droppedItemTargets = ItemTarget.of(droppedItem);
      this.miningRequirement = miningRequirement;
      this.prioritySupplier = prioritySupplier;
   }

   @Override
   public Task getTask(AltoClefController mod) {
      return new MineAndCollectTask(this.droppedItemTargets, this.toMine, this.miningRequirement);
   }

   @Override
   public String getDebugString() {
      return "Gathering resource: " + Arrays.toString((Object[])this.droppedItem);
   }

   @Override
   protected double getPriority(AltoClefController mod) {
      if (!StorageHelper.miningRequirementMet(mod, this.miningRequirement)) {
         return Double.NEGATIVE_INFINITY;
      } else {
         double closestDist = this.getClosestDist(mod);
         int itemCount = mod.getItemStorage().getItemCount(this.droppedItem);
         this.prioritySupplier.update(itemCount);
         return this.prioritySupplier.getPriority(closestDist);
      }
   }

   private double getClosestDist(AltoClefController mod) {
      Vec3 pos = EntityVer.getPos(mod.getPlayer());
      Tuple<Double, Optional<BlockPos>> closestBlock = MineAndCollectTask.MineOrCollectTask.getClosestBlock(mod, pos,
            this.toMine);
      Tuple<Double, Optional<ItemEntity>> closestDrop = MineAndCollectTask.MineOrCollectTask.getClosestItemDrop(mod, pos,
            this.droppedItemTargets);
      return Math.min((Double) closestBlock.getA(), (Double) closestDrop.getA());
   }
}
