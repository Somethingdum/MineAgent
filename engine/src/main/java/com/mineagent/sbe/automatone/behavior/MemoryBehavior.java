// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/automatone/behavior/MemoryBehavior.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
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

package com.mineagent.sbe.automatone.behavior;

import com.mineagent.sbe.automatone.Baritone;
import com.mineagent.sbe.automatone.api.cache.Waypoint;
import com.mineagent.sbe.automatone.api.event.events.BlockInteractEvent;
import com.mineagent.sbe.automatone.api.utils.BetterBlockPos;
import com.mineagent.sbe.automatone.utils.BlockStateInterface;
import net.minecraft.world.level.block.BedBlock;


/**
 * doesn't work for horse inventories :^)
 *
 * @author Brady
 * @since 8/6/2018
 */
public final class MemoryBehavior extends Behavior {

    public MemoryBehavior(Baritone baritone) {
        super(baritone);
    }

    @Override
    public void onBlockInteract(BlockInteractEvent event) {
        if (event.getType() == BlockInteractEvent.Type.USE && BlockStateInterface.getBlock(ctx, event.getPos()) instanceof BedBlock) {
            baritone.getWorldProvider().getCurrentWorld().getWaypoints().addWaypoint(new Waypoint("bed", Waypoint.Tag.BED, BetterBlockPos.from(event.getPos())));
        }
    }
}
