// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/automatone/command/defaults/FarmCommand.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
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

package com.mineagent.sbe.automatone.command.defaults;

import com.mineagent.sbe.automatone.api.IBaritone;
import com.mineagent.sbe.automatone.api.cache.IWaypoint;
import com.mineagent.sbe.automatone.api.command.Command;
import com.mineagent.sbe.automatone.api.command.argument.IArgConsumer;
import com.mineagent.sbe.automatone.api.command.datatypes.ForWaypoints;
import com.mineagent.sbe.automatone.api.command.exception.CommandException;
import com.mineagent.sbe.automatone.api.command.exception.CommandInvalidStateException;
import com.mineagent.sbe.automatone.api.utils.BetterBlockPos;
import net.minecraft.commands.CommandSourceStack;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class FarmCommand extends Command {

    public FarmCommand() {
        super("farm");
    }

    @Override
    public void execute(CommandSourceStack source, String label, IArgConsumer args, IBaritone baritone) throws CommandException {
        args.requireMax(2);
        int range = 0;
        BetterBlockPos origin = null;
        //range
        if (args.has(1)) {
            range = args.getAs(Integer.class);
        }
        //waypoint
        if (args.has(1)) {
            IWaypoint[] waypoints = args.getDatatypeFor(ForWaypoints.INSTANCE);
            IWaypoint waypoint;
            switch (waypoints.length) {
                case 0:
                    throw new CommandInvalidStateException("No waypoints found");
                case 1:
                    waypoint = waypoints[0];
                    break;
                default:
                    throw new CommandInvalidStateException("Multiple waypoints were found");
            }
            origin = waypoint.getLocation();
        }

        baritone.getFarmProcess().farm(range, origin);
        logDirect(source, "Farming");
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Farm nearby crops";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "The farm command starts farming nearby plants. It harvests mature crops and plants new ones.",
                "",
                "Usage:",
                "> farm - farms every crop it can find.",
                "> farm <range> - farm crops within range from the starting position.",
                "> farm <range> <waypoint> - farm crops within range from waypoint."
        );
    }
}
