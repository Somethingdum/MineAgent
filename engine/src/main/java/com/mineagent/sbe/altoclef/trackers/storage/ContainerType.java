// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/trackers/storage/ContainerType.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.trackers.storage;

import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;

public enum ContainerType {
   CHEST,
   ENDER_CHEST,
   SHULKER,
   FURNACE,
   BREWING,
   MISC,
   EMPTY;

   public static ContainerType getFromBlock(Block block) {
      if (block instanceof ChestBlock) {
         return CHEST;
      } else if (block instanceof AbstractFurnaceBlock) {
         return FURNACE;
      } else if (block.equals(Blocks.ENDER_CHEST)) {
         return ENDER_CHEST;
      } else if (block instanceof ShulkerBoxBlock) {
         return SHULKER;
      } else if (block instanceof BrewingStandBlock) {
         return BREWING;
      } else {
         return !(block instanceof BarrelBlock) && !(block instanceof DispenserBlock) && !(block instanceof HopperBlock) ? EMPTY : MISC;
      }
   }
}
