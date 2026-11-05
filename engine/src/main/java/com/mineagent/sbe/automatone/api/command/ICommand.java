// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/automatone/api/command/ICommand.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
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

package com.mineagent.sbe.automatone.api.command;

import com.mineagent.sbe.automatone.api.BaritoneAPI;
import com.mineagent.sbe.automatone.api.IBaritone;
import com.mineagent.sbe.automatone.api.command.argument.IArgConsumer;
import com.mineagent.sbe.automatone.api.command.exception.CommandException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import java.util.List;
import java.util.stream.Stream;

/**
 * The base for a command.
 *
 * @author Brady
 * @since 10/7/2019
 */
public interface ICommand {

    /**
     * Called when this command is executed.
     */
    void execute(CommandSourceStack source, String label, IArgConsumer args, IBaritone baritone) throws CommandException;

    /**
     * Called when the command needs to tab complete. Return a Stream representing the entries to put in the completions
     * list.
     */
    Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException;

    /**
     * @return A <b>single-line</b> string containing a short description of this command's purpose.
     */
    String getShortDesc();

    /**
     * @return A list of lines that will be printed by the help command when the user wishes to view them.
     */
    List<String> getLongDesc();

    /**
     * @return A list of the names that can be accepted to have arguments passed to this command
     */
    List<String> getNames();

    /**
     * @return {@code true} if this command should be hidden from the help menu
     */
    default boolean hiddenFromHelp() {
        return false;
    }

    /**
     * Send components to chat with the [Automatone] prefix
     *
     * @param source
     * @param components The components to send
     */
    default void logDirect(CommandSourceStack source, Component... components) {
        source.sendSuccess(() -> {
            MutableComponent component = Component.literal("");
            // If we are not logging as a Toast
            // Append the prefix to the base component line
            component.append(BaritoneAPI.getPrefix());
            component.append(Component.literal(" "));
            for (Component t : components) component.append(t);
            return component;
        }, false);
    }

    /**
     * Send a message to chat regardless of chatDebug (should only be used for critically important messages, or as a
     * direct response to a chat command)
     *
     * @param source
     * @param message The message to display in chat
     * @param color   The color to print that message in
     */
    default void logDirect(CommandSourceStack source, String message, ChatFormatting color) {
        Stream.of(message.split("\n")).forEach(line -> {
            MutableComponent component = Component.literal(line.replace("\t", "    "));
            component.setStyle(component.getStyle().applyFormat(color));
            logDirect(source, component);
        });
    }

    /**
     * Send a message to chat regardless of chatDebug (should only be used for critically important messages, or as a
     * direct response to a chat command)
     *
     * @param source
     * @param message The message to display in chat
     */
    default void logDirect(CommandSourceStack source, String message) {
        logDirect(source, message, ChatFormatting.GRAY);
    }
}
