// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/multiversion/BlockTagVer.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.multiversion;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;

public class BlockTagVer {


    public static boolean isWool(Block block) {
        return BuiltInRegistries.BLOCK.getResourceKey(block).map(e -> BuiltInRegistries.BLOCK.getHolderOrThrow(e).tags().anyMatch(t -> t == BlockTags.WOOL)).orElse(false);
    }

}
