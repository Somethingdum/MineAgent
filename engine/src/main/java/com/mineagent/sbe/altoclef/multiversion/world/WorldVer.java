// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/multiversion/world/WorldVer.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.multiversion.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

public class WorldVer {
   public static boolean isBiomeAtPos(Level world, ResourceKey<Biome> biome, BlockPos pos) {
      Holder<Biome> b = world.getBiome(pos);
      return b.is(biome);
   }

   public static boolean isBiome(Holder<Biome> biome1, ResourceKey<Biome> biome2) {
      return biome1.is(biome2);
   }

   public static int getBottomY(Level world) {
      return world.getMinBuildHeight();
   }

   public static int getTopY(Level world) {
      return HeightLimitViewVer.getTopY(world);
   }

   private static boolean isOutOfHeightLimit(Level world, BlockPos pos) {
      return world.isOutsideBuildHeight(pos);
   }
}
