// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/automatone/api/command/datatypes/RelativeCoordinate.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
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

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public enum RelativeCoordinate implements IDatatypePost<Double, Double> {
    INSTANCE;
    private static Pattern PATTERN = Pattern.compile("^(~?)([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)([k-k]?)|)$");

    @Override
    public Double apply(IDatatypeContext ctx, Double origin) throws CommandException {
        if (origin == null) {
            origin = 0.0D;
        }

        Matcher matcher = PATTERN.matcher(ctx.getConsumer().getString());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("pattern doesn't match");
        }

        boolean isRelative = !matcher.group(1).isEmpty();

        double offset = matcher.group(2).isEmpty() ? 0 : Double.parseDouble(matcher.group(2).replaceAll("k", ""));

        if (matcher.group(2).contains("k")) {
            offset *= 1000;
        }

        if (isRelative) {
            return origin + offset;
        }
        return offset;
    }

    @Override
    public Stream<String> tabComplete(IDatatypeContext ctx) throws CommandException {
        final IArgConsumer consumer = ctx.getConsumer();
        if (!consumer.has(2) && consumer.getString().matches("^(~|$)")) {
            return Stream.of("~");
        }
        return Stream.empty();
    }
}
