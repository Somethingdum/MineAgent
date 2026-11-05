// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/automatone/command/defaults/InvertCommand.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
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
import com.mineagent.sbe.automatone.api.command.exception.CommandInvalidStateException;
import com.mineagent.sbe.automatone.api.pathing.goals.Goal;
import com.mineagent.sbe.automatone.api.pathing.goals.GoalInverted;
import com.mineagent.sbe.automatone.api.process.ICustomGoalProcess;
import net.minecraft.commands.CommandSourceStack;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class InvertCommand extends Command {

    public InvertCommand() {
        super("invert");
    }

    @Override
    public void execute(CommandSourceStack source, String label, IArgConsumer args, IBaritone baritone) throws CommandException {
        args.requireMax(0);
        ICustomGoalProcess customGoalProcess = baritone.getCustomGoalProcess();
        Goal goal;
        if ((goal = customGoalProcess.getGoal()) == null) {
            throw new CommandInvalidStateException("No goal");
        }
        if (goal instanceof GoalInverted) {
            goal = ((GoalInverted) goal).origin;
        } else {
            goal = new GoalInverted(goal);
        }
        customGoalProcess.setGoalAndPath(goal);
        logDirect(source, String.format("Goal: %s", goal.toString()));
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Run away from the current goal";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "The invert command tells Automatone to head away from the current goal rather than towards it.",
                "",
                "Usage:",
                "> invert - Invert the current goal."
        );
    }
}
