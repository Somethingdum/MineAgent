// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/multiversion/world/WorldVer.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.multiversion.world;

import com.mineagent.engine.alto.multiversion.Pattern;
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


    @Pattern
    public static int getBottomY(Level world) {
        return world.getMinBuildHeight();
    }

    @Pattern
    public static int getTopY(Level world) {
        return world.getMaxBuildHeight();
    }

    @Pattern
    private static boolean isOutOfHeightLimit(Level world,BlockPos pos) {
        return world.isOutsideBuildHeight(pos);
    }

}
