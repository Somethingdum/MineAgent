// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/automatone/utils/pathing/PathBase.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
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

package com.mineagent.sbe.automatone.utils.pathing;

import com.mineagent.sbe.automatone.api.Settings;
import com.mineagent.sbe.automatone.api.pathing.calc.IPath;
import com.mineagent.sbe.automatone.api.pathing.goals.Goal;
import com.mineagent.sbe.automatone.pathing.path.CutoffPath;
import com.mineagent.sbe.automatone.utils.BlockStateInterface;
import net.minecraft.core.BlockPos;

public abstract class PathBase implements IPath {

    public PathBase cutoffAtLoadedChunks(BlockStateInterface bsi, Settings settings) {
        if (!settings.cutoffAtLoadBoundary.get()) {
            return this;
        }
        for (int i = 0; i < positions().size(); i++) {
            BlockPos pos = positions().get(i);
            if (!bsi.worldContainsLoadedChunk(pos.getX(), pos.getZ())) {
                return new CutoffPath(this, i);
            }
        }
        return this;
    }

    public PathBase staticCutoff(Goal destination, Settings settings) {
        int minLength = settings.pathCutoffMinimumLength.get();
        double cutoffFactor = settings.pathCutoffFactor.get();

        if (length() < minLength) {
            return this;
        }

        if (destination == null || destination.isInGoal(getDest())) {
            return this;
        }

        int newLength = (int) ((length() - minLength) * cutoffFactor) + minLength - 1;
        return new CutoffPath(this, newLength);
    }
}
