// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/automatone/cache/WorldData.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
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

package com.mineagent.sbe.automatone.cache;

import com.mineagent.sbe.automatone.api.cache.ICachedWorld;
import com.mineagent.sbe.automatone.api.cache.IContainerMemory;
import com.mineagent.sbe.automatone.api.cache.IWaypointCollection;
import com.mineagent.sbe.automatone.api.cache.IWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import java.util.ArrayList;


/**
 * Data about a world, from baritone's point of view. Includes cached chunks, waypoints, and map data.
 *
 * @author leijurv
 */
public class WorldData implements IWorldData {

    private final WaypointCollection waypoints;
    private final ContainerMemory containerMemory;
    //public final MapData map;
    public final ResourceKey<Level> dimension;

    WorldData(ResourceKey<Level> dimension) {
        this.waypoints = new WaypointCollection();
        this.containerMemory = new ContainerMemory();
        this.dimension = dimension;
    }

//    public void readFromNbt(NbtCompound tag) {
//        this.containerMemory.read(tag.getCompound("containers"));
//        this.waypoints.readFromNbt(tag.getCompound("waypoints"));
//    }
//
//    public void writeToNbt(NbtCompound tag) {
//        tag.put("containers", containerMemory.toNbt());
//        tag.put("waypoints", waypoints.toNbt());
//    }

    @Override
    public ICachedWorld getCachedWorld() {
        return new ICachedWorld(){

            @Override
            public boolean isCached(int blockX, int blockZ) {
                return false;
            }

            @Override
            public ArrayList<BlockPos> getLocationsOf(String block, int maximum, int centerX, int centerZ, int maxRegionDistanceSq) {
                return new ArrayList<>();
            }
        };
    }

    @Override
    public IWaypointCollection getWaypoints() {
        return this.waypoints;
    }

    @Override
    public IContainerMemory getContainerMemory() {
        return this.containerMemory;
    }
}
