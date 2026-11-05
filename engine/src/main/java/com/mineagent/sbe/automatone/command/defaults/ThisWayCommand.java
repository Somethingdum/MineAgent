// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/automatone/command/defaults/ThisWayCommand.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
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
import com.mineagent.sbe.automatone.api.command.Command;
import com.mineagent.sbe.automatone.api.command.argument.IArgConsumer;
import com.mineagent.sbe.automatone.api.command.exception.CommandException;
import com.mineagent.sbe.automatone.api.pathing.goals.GoalXZ;
import com.mineagent.sbe.automatone.api.utils.IEntityContext;
import net.minecraft.commands.CommandSourceStack;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class ThisWayCommand extends Command {

    public ThisWayCommand() {
        super("thisway", "forward");
    }

    @Override
    public void execute(CommandSourceStack source, String label, IArgConsumer args, IBaritone baritone) throws CommandException {
        args.requireExactly(1);
        IEntityContext ctx = baritone.getPlayerContext();
        GoalXZ goal = GoalXZ.fromDirection(
                ctx.feetPosAsVec(),
                ctx.entity().yHeadRot,
                args.getAs(Double.class)
        );
        baritone.getCustomGoalProcess().setGoal(goal);
        logDirect(source, String.format("Goal: %s", goal));
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Travel in your current direction";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Creates a GoalXZ some amount of blocks in the direction you're currently looking",
                "",
                "Usage:",
                "> thisway <distance> - makes a GoalXZ distance blocks in front of you"
        );
    }
}
