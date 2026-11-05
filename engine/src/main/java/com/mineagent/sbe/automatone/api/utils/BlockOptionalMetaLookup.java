// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/automatone/api/utils/BlockOptionalMetaLookup.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.mineagent.sbe.automatone.api.utils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class BlockOptionalMetaLookup {

    private final BlockOptionalMeta[] boms;

    public BlockOptionalMetaLookup(BlockOptionalMeta... boms) {
        this.boms = boms;
    }

    public BlockOptionalMetaLookup(ServerLevel world, Block... blocks) {
        this.boms = Stream.of(blocks)
                .map(block -> new BlockOptionalMeta(world, block))
                .toArray(BlockOptionalMeta[]::new);
    }

    public BlockOptionalMetaLookup(ServerLevel world, List<Block> blocks) {
        this.boms = blocks.stream()
                .map(block -> new BlockOptionalMeta(world, block))
                .toArray(BlockOptionalMeta[]::new);
    }

    public BlockOptionalMetaLookup(ServerLevel world, String... blocks) {
        this.boms = Stream.of(blocks)
                .map(block -> new BlockOptionalMeta(world, block))
                .toArray(BlockOptionalMeta[]::new);
    }

    public boolean has(Block block) {
        for (BlockOptionalMeta bom : boms) {
            if (bom.getBlock() == block) {
                return true;
            }
        }

        return false;
    }

    public boolean has(BlockState state) {
        for (BlockOptionalMeta bom : boms) {
            if (bom.matches(state)) {
                return true;
            }
        }

        return false;
    }

    public boolean has(ItemStack stack) {
        for (BlockOptionalMeta bom : boms) {
            if (bom.matches(stack)) {
                return true;
            }
        }

        return false;
    }

    public List<BlockOptionalMeta> blocks() {
        return Arrays.asList(boms);
    }

    @Override
    public String toString() {
        return String.format(
                "BlockOptionalMetaLookup{%s}",
                Arrays.toString(boms)
        );
    }
}
