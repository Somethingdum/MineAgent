// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/misc/RavageRuinedPortalsTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.misc;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.altoclef.tasks.container.LootContainerTask;
import com.mineagent.sbe.altoclef.tasks.movement.TimeoutWanderTask;
import com.mineagent.sbe.altoclef.tasksystem.Task;
import com.mineagent.sbe.altoclef.util.Dimension;
import com.mineagent.sbe.altoclef.util.helpers.WorldHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public class RavageRuinedPortalsTask extends Task {
   public final Item[] LOOT = new Item[]{
      Items.IRON_NUGGET,
      Items.FLINT,
      Items.OBSIDIAN,
      Items.FIRE_CHARGE,
      Items.FLINT_AND_STEEL,
      Items.GOLD_NUGGET,
      Items.GOLDEN_APPLE,
      Items.GOLDEN_AXE,
      Items.GOLDEN_HOE,
      Items.GOLDEN_PICKAXE,
      Items.GOLDEN_SHOVEL,
      Items.GOLDEN_SWORD,
      Items.GOLDEN_HELMET,
      Items.GOLDEN_CHESTPLATE,
      Items.GOLDEN_LEGGINGS,
      Items.GOLDEN_BOOTS,
      Items.GLISTERING_MELON_SLICE,
      Items.GOLDEN_CARROT,
      Items.GOLD_INGOT,
      Items.CLOCK,
      Items.LIGHT_WEIGHTED_PRESSURE_PLATE,
      Items.GOLDEN_HORSE_ARMOR,
      Items.GOLD_BLOCK,
      Items.BELL,
      Items.ENCHANTED_GOLDEN_APPLE
   };
   private List<BlockPos> notRuinedPortalChests = new ArrayList<>();
   private Task lootTask;

   @Override
   protected void onStart() {
      this.controller.getBehaviour().push();
   }

   @Override
   protected Task onTick() {
      if (this.lootTask != null && this.lootTask.isActive() && !this.lootTask.isFinished()) {
         return this.lootTask;
      } else {
         Optional<BlockPos> closest = this.locateClosestUnopenedRuinedPortalChest(this.controller);
         if (closest.isPresent()) {
            this.lootTask = new LootContainerTask(closest.get(), List.of(this.LOOT));
            return this.lootTask;
         } else {
            return new TimeoutWanderTask();
         }
      }
   }

   @Override
   protected void onStop(Task task) {
      this.controller.getBehaviour().pop();
   }

   @Override
   protected boolean isEqual(Task task) {
      return task instanceof RavageRuinedPortalsTask;
   }

   @Override
   public boolean isFinished() {
      return false;
   }

   @Override
   protected String toDebugString() {
      return "Ravaging Ruined Portals";
   }

   private boolean canBeLootablePortalChest(AltoClefController mod, BlockPos blockPos) {
      if (mod.getWorld().getBlockState(blockPos.above(1)).getBlock() != Blocks.WATER && blockPos.getY() >= 50) {
         for (BlockPos check : WorldHelper.scanRegion(blockPos.offset(-4, -2, -4), blockPos.offset(4, 2, 4))) {
            if (mod.getWorld().getBlockState(check).getBlock() == Blocks.NETHERRACK) {
               return true;
            }
         }

         this.notRuinedPortalChests.add(blockPos);
         return false;
      } else {
         return false;
      }
   }

   private Optional<BlockPos> locateClosestUnopenedRuinedPortalChest(AltoClefController mod) {
      return WorldHelper.getCurrentDimension(this.controller) != Dimension.OVERWORLD
         ? Optional.empty()
         : mod.getBlockScanner()
            .getNearestBlock(
               (Predicate<BlockPos>)(blockPos -> !this.notRuinedPortalChests.contains(blockPos)
                  && WorldHelper.isUnopenedChest(this.controller, blockPos)
                  && this.canBeLootablePortalChest(mod, blockPos)),
               Blocks.CHEST
            );
   }
}
