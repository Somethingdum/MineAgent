// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/automatone/api/schematic/ReplaceSchematic.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
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

package com.mineagent.sbe.automatone.api.schematic;

import com.mineagent.sbe.automatone.api.utils.BlockOptionalMetaLookup;
import net.minecraft.world.level.block.state.BlockState;

public class ReplaceSchematic extends MaskSchematic {

    private final BlockOptionalMetaLookup filter;
    private final Boolean[][][] cache;

    public ReplaceSchematic(ISchematic schematic, BlockOptionalMetaLookup filter) {
        super(schematic);
        this.filter = filter;
        this.cache = new Boolean[widthX()][heightY()][lengthZ()];
    }

    @Override
    public void reset() {
        // it's final, can't use this.cache = new Boolean[widthX()][heightY()][lengthZ()]
        for (int x = 0; x < cache.length; x++) {
            for (int y = 0; y < cache[0].length; y++) {
                for (int z = 0; z < cache[0][0].length; z++) {
                    cache[x][y][z] = null;
                }
            }
        }
    }

    @Override
    protected boolean partOfMask(int x, int y, int z, BlockState currentState) {
        if (cache[x][y][z] == null) {
            cache[x][y][z] = filter.has(currentState);
        }
        return cache[x][y][z];
    }
}
