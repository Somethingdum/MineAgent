// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/automatone/api/event/events/BlockInteractEvent.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
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

package com.mineagent.sbe.automatone.api.event.events;

import net.minecraft.core.BlockPos;

/**
 * Called when the local player interacts with a block, can be either {@link Type#START_BREAK} or {@link Type#USE}.
 *
 * @author Brady
 * @since 8/22/2018
 */
public final class BlockInteractEvent {

    /**
     * The position of the block interacted with
     */
    private final BlockPos pos;

    /**
     * The type of interaction that occurred
     */
    private final Type type;

    public BlockInteractEvent(BlockPos pos, Type type) {
        this.pos = pos;
        this.type = type;
    }

    /**
     * @return The position of the block interacted with
     */
    public final BlockPos getPos() {
        return this.pos;
    }

    /**
     * @return The type of interaction with the target block
     */
    public final Type getType() {
        return this.type;
    }

    public enum Type {

        /**
         * We're starting to break the target block.
         */
        START_BREAK,

        /**
         * We're right clicking on the target block. Either placing or interacting with.
         */
        USE
    }
}
