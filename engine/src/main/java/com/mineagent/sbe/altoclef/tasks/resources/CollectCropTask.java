// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/resources/CollectCropTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.resources;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.multiversion.EntityVer;
import com.mineagent.sbe.altoclef.multiversion.blockpos.BlockPosVer;
import com.mineagent.sbe.altoclef.tasks.DoToClosestBlockTask;
import com.mineagent.sbe.altoclef.tasks.InteractWithBlockTask;
import com.mineagent.sbe.altoclef.tasks.ResourceTask;
import com.mineagent.sbe.altoclef.tasks.construction.DestroyBlockTask;
import com.mineagent.sbe.altoclef.tasks.movement.PickupDroppedItemTask;
import com.mineagent.sbe.altoclef.tasksystem.Task;
import com.mineagent.sbe.altoclef.util.ItemTarget;
import com.mineagent.sbe.altoclef.util.helpers.StlHelper;
import com.mineagent.sbe.altoclef.util.helpers.WorldHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

public class CollectCropTask extends ResourceTask {
   private final ItemTarget cropToCollect;
   private final Item[] cropSeed;
   private final Predicate<BlockPos> canBreak;
   private final Block[] cropBlock;
   private final Set<BlockPos> emptyCropland = new HashSet<>();
   private final Task collectSeedTask;
   private final HashSet<BlockPos> wasFullyGrown = new HashSet<>();

   public CollectCropTask(ItemTarget cropToCollect, Block[] cropBlock, Item[] cropSeed, Predicate<BlockPos> canBreak) {
      super(cropToCollect);
      this.cropToCollect = cropToCollect;
      this.cropSeed = cropSeed;
      this.canBreak = canBreak;
      this.cropBlock = cropBlock;
      this.collectSeedTask = new PickupDroppedItemTask(new ItemTarget(cropSeed, 1), true);
   }

   public CollectCropTask(ItemTarget cropToCollect, Block[] cropBlock, Item... cropSeed) {
      this(cropToCollect, cropBlock, cropSeed, canBreak -> true);
   }

   public CollectCropTask(ItemTarget cropToCollect, Block cropBlock, Item... cropSeed) {
      this(cropToCollect, new Block[]{cropBlock}, cropSeed);
   }

   public CollectCropTask(Item cropItem, int count, Block cropBlock, Item... cropSeed) {
      this(new ItemTarget(cropItem, count), cropBlock, cropSeed);
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
      if (this.hasEmptyCrops(mod) && mod.getModSettings().shouldReplantCrops() && !mod.getItemStorage().hasItem(this.cropSeed)) {
         if (this.collectSeedTask.isActive() && !this.collectSeedTask.isFinished()) {
            this.setDebugState("Picking up dropped seeds");
            return this.collectSeedTask;
         }

         if (mod.getEntityTracker().itemDropped(this.cropSeed)) {
            Optional<ItemEntity> closest = mod.getEntityTracker().getClosestItemDrop(EntityVer.getPos(mod.getPlayer()), this.cropSeed);
            if (closest.isPresent() && closest.get().closerThan(mod.getPlayer(), 7.0)) {
               return this.collectSeedTask;
            }
         }
      }

      if (this.shouldReplantNow(mod)) {
         this.setDebugState("Replanting...");
         this.emptyCropland.removeIf(blockPos -> !this.isEmptyCrop(mod, blockPos));

         assert !this.emptyCropland.isEmpty();

         return new DoToClosestBlockTask(
            blockPos -> new InteractWithBlockTask(new ItemTarget(this.cropSeed, 1), Direction.UP, blockPos.below(), true),
            pos -> this.emptyCropland.stream().min(StlHelper.compareValues(block -> BlockPosVer.getSquaredDistance(block, pos))),
            this.emptyCropland::contains,
            Blocks.FARMLAND
         );
      } else {
         Predicate<BlockPos> validCrop = blockPos -> !this.canBreak.test(blockPos)
            ? false
            : (
               mod.getModSettings().shouldReplantCrops() && !this.isMature(mod, blockPos)
                  ? false
                  : (mod.getWorld().getBlockState(blockPos).getBlock() == Blocks.WHEAT ? this.isMature(mod, blockPos) : true)
            );
         if (this.isInWrongDimension(mod) && !mod.getBlockScanner().anyFound(validCrop, this.cropBlock)) {
            return this.getToCorrectDimensionTask(mod);
         } else {
            this.setDebugState("Breaking crops.");
            return new DoToClosestBlockTask(blockPos -> {
               this.emptyCropland.add(blockPos);
               return new DestroyBlockTask(blockPos);
            }, validCrop, this.cropBlock);
         }
      }
   }

   @Override
   protected void onResourceStop(AltoClefController mod, Task interruptTask) {
   }

   @Override
   public boolean isFinished() {
      return this.shouldReplantNow(this.controller) ? false : super.isFinished();
   }

   private boolean shouldReplantNow(AltoClefController mod) {
      return mod.getModSettings().shouldReplantCrops() && this.hasEmptyCrops(mod) && mod.getItemStorage().hasItem(this.cropSeed);
   }

   private boolean hasEmptyCrops(AltoClefController mod) {
      for (BlockPos pos : this.emptyCropland) {
         if (this.isEmptyCrop(mod, pos)) {
            return true;
         }
      }

      return false;
   }

   private boolean isEmptyCrop(AltoClefController mod, BlockPos pos) {
      return WorldHelper.isAir(mod.getWorld().getBlockState(pos).getBlock());
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return !(other instanceof CollectCropTask task)
         ? false
         : Arrays.equals((Object[])task.cropSeed, (Object[])this.cropSeed)
            && Arrays.equals((Object[])task.cropBlock, (Object[])this.cropBlock)
            && task.cropToCollect.equals(this.cropToCollect);
   }

   @Override
   protected String toDebugStringName() {
      return "Collecting crops: " + this.cropToCollect;
   }

   private boolean isMature(AltoClefController mod, BlockPos blockPos) {
      if (mod.getChunkTracker().isChunkLoaded(blockPos) && WorldHelper.canReach(this.controller, blockPos)) {
         BlockState s = mod.getWorld().getBlockState(blockPos);
         if (s.getBlock() instanceof CropBlock crop) {
            boolean mature = crop.isMaxAge(s);
            if (this.wasFullyGrown.contains(blockPos)) {
               if (!mature) {
                  this.wasFullyGrown.remove(blockPos);
               }
            } else if (mature) {
               this.wasFullyGrown.add(blockPos);
            }

            return mature;
         } else {
            return false;
         }
      } else {
         return this.wasFullyGrown.contains(blockPos);
      }
   }
}
