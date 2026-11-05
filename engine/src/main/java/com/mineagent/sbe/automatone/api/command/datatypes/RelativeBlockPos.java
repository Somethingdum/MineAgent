// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/automatone/api/command/datatypes/RelativeBlockPos.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
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

package com.mineagent.sbe.automatone.api.command.datatypes;

import com.mineagent.sbe.automatone.api.command.argument.IArgConsumer;
import com.mineagent.sbe.automatone.api.command.exception.CommandException;
import com.mineagent.sbe.automatone.api.utils.BetterBlockPos;

import java.util.stream.Stream;

public enum RelativeBlockPos implements IDatatypePost<BetterBlockPos, BetterBlockPos> {
    INSTANCE;

    @Override
    public BetterBlockPos apply(IDatatypeContext ctx, BetterBlockPos origin) throws CommandException {
        if (origin == null) {
            origin = BetterBlockPos.ORIGIN;
        }

        final IArgConsumer consumer = ctx.getConsumer();
        return new BetterBlockPos(
                consumer.getDatatypePost(RelativeCoordinate.INSTANCE, (double) origin.x),
                consumer.getDatatypePost(RelativeCoordinate.INSTANCE, (double) origin.y),
                consumer.getDatatypePost(RelativeCoordinate.INSTANCE, (double) origin.z)
        );
    }

    @Override
    public Stream<String> tabComplete(IDatatypeContext ctx) throws CommandException {
        final IArgConsumer consumer = ctx.getConsumer();
        if (consumer.hasAny() && !consumer.has(4)) {
            while (consumer.has(2)) {
                if (consumer.peekDatatypeOrNull(RelativeCoordinate.INSTANCE) == null) {
                    break;
                }
                consumer.get();
            }
            return consumer.tabCompleteDatatype(RelativeCoordinate.INSTANCE);
        }
        return Stream.empty();
    }
}
