// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/automatone/api/command/manager/ICommandManager.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
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

package com.mineagent.sbe.automatone.api.command.manager;

import com.mineagent.sbe.automatone.api.IBaritone;
import com.mineagent.sbe.automatone.api.command.ICommand;
import com.mineagent.sbe.automatone.api.command.argument.ICommandArgument;
import com.mineagent.sbe.automatone.api.command.exception.CommandException;
import com.mineagent.sbe.automatone.api.command.registry.Registry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.util.Tuple;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * @author Brady
 * @since 9/21/2019
 */
public interface ICommandManager {

    Registry<ICommand> registry = new Registry<>();

    /**
     * @param name The command name to search for.
     * @return The command, if found.
     */
    static ICommand getCommand(String name) {
        for (ICommand command : registry.entries) {
            if (command.getNames().contains(name.toLowerCase(Locale.ROOT))) {
                return command;
            }
        }
        return null;
    }

    IBaritone getBaritone();

    Registry<ICommand> getRegistry();

    boolean execute(CommandSourceStack source, String string) throws CommandException;

    boolean execute(CommandSourceStack source, Tuple<String, List<ICommandArgument>> expanded) throws CommandException;

    Stream<String> tabComplete(Tuple<String, List<ICommandArgument>> expanded);

    Stream<String> tabComplete(String prefix);
}
