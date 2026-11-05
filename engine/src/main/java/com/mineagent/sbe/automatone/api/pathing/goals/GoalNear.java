// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/automatone/api/pathing/goals/GoalNear.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
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

package com.mineagent.sbe.automatone.api.pathing.goals;

import com.mineagent.sbe.automatone.api.utils.SettingsUtil;
import com.mineagent.sbe.automatone.api.utils.interfaces.IGoalRenderPos;
import net.minecraft.core.BlockPos;
import it.unimi.dsi.fastutil.doubles.DoubleIterator;
import it.unimi.dsi.fastutil.doubles.DoubleOpenHashSet;

public class GoalNear implements Goal, IGoalRenderPos {

    protected final int x;
    protected final int y;
    protected final int z;
    protected final int rangeSq;

    public GoalNear(BlockPos pos, int range) {
        this.x = pos.getX();
        this.y = pos.getY();
        this.z = pos.getZ();
        this.rangeSq = range * range;
    }

    @Override
    public boolean isInGoal(int x, int y, int z) {
        int xDiff = x - this.x;
        int yDiff = y - this.y;
        int zDiff = z - this.z;
        return xDiff * xDiff + yDiff * yDiff + zDiff * zDiff <= rangeSq;
    }

    @Override
    public double heuristic(int x, int y, int z) {
        int xDiff = x - this.x;
        int yDiff = y - this.y;
        int zDiff = z - this.z;
        return GoalBlock.calculate(xDiff, yDiff, zDiff);
    }

    @Override
    public double heuristic() {// TODO less hacky solution
        int range = (int) Math.ceil(Math.sqrt(rangeSq));
        DoubleOpenHashSet maybeAlwaysInside = new DoubleOpenHashSet(); // see pull request #1978
        double minOutside = Double.POSITIVE_INFINITY;
        for (int dx = -range; dx <= range; dx++) {
            for (int dy = -range; dy <= range; dy++) {
                for (int dz = -range; dz <= range; dz++) {
                    double h = heuristic(x + dx, y + dy, z + dz);
                    if (h < minOutside && isInGoal(x + dx, y + dy, z + dz)) {
                        maybeAlwaysInside.add(h);
                    } else {
                        minOutside = Math.min(minOutside, h);
                    }
                }
            }
        }
        double maxInside = Double.NEGATIVE_INFINITY;
        DoubleIterator it = maybeAlwaysInside.iterator();
        while (it.hasNext()) {
            double inside = it.nextDouble();
            if (inside < minOutside) {
                maxInside = Math.max(maxInside, inside);
            }
        }
        return maxInside;
    }

    @Override
    public BlockPos getGoalPos() {
        return new BlockPos(x, y, z);
    }

    @Override
    public String toString() {
        return String.format(
                "GoalNear{x=%s, y=%s, z=%s, rangeSq=%d}",
                SettingsUtil.maybeCensor(x),
                SettingsUtil.maybeCensor(y),
                SettingsUtil.maybeCensor(z),
                rangeSq
        );
    }
}
