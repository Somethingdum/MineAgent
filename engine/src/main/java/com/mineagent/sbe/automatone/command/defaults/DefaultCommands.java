// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/automatone/command/defaults/DefaultCommands.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
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

import com.mineagent.sbe.altoclef.multiversion.CommandVer;
import com.mineagent.sbe.automatone.Automatone;
import com.mineagent.sbe.automatone.api.BaritoneAPI;
import com.mineagent.sbe.automatone.api.IBaritone;
import com.mineagent.sbe.automatone.api.Settings;
import com.mineagent.sbe.automatone.api.command.ICommand;
import com.mineagent.sbe.automatone.api.command.argument.ICommandArgument;
import com.mineagent.sbe.automatone.api.command.exception.CommandException;
import com.mineagent.sbe.automatone.api.command.exception.CommandNotEnoughArgumentsException;
import com.mineagent.sbe.automatone.api.command.manager.ICommandManager;
import com.mineagent.sbe.automatone.command.argument.ArgConsumer;
import com.mineagent.sbe.automatone.command.manager.BaritoneCommandManager;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static com.mineagent.sbe.automatone.api.command.IBaritoneChatControl.FORCE_COMMAND_PREFIX;

public final class DefaultCommands {

    public static final ExecutionControlCommands controlCommands = new ExecutionControlCommands();
    public static final SelCommand selCommand = new SelCommand();
    public static final DynamicCommandExceptionType BARITONE_COMMAND_FAILED_EXCEPTION = new DynamicCommandExceptionType(Message.class::cast);

    public static void registerAll() {
        List<ICommand> commands = new ArrayList<>(Arrays.asList(
                new HelpCommand(),
                new SetCommand(),
                new CommandAlias(Arrays.asList("modified", "mod", "me/sailex/automatone", "modifiedsettings"), "List modified settings", "set modified"),
                new CommandAlias("reset", "Reset all settings or just one", "set reset"),
                new GoalCommand(),
                new GotoCommand(),
                new PathCommand(),
                new ProcCommand(),
                new ETACommand(),
                new VersionCommand(),
                new RepackCommand(),
                new BuildCommand(),
                new SchematicaCommand(),
                new ComeCommand(),
                new AxisCommand(),
                new ForceCancelCommand(),
                new GcCommand(),
                new InvertCommand(),
                new TunnelCommand(),
                new RenderCommand(),
                new FarmCommand(),
                new ChestsCommand(),
                new FollowCommand(),
                new ExploreFilterCommand(),
                new ReloadAllCommand(),
                new SaveAllCommand(),
                new ExploreCommand(),
                new BlacklistCommand(),
                new FindCommand(),
                new MineCommand(),
                new SurfaceCommand(),
                new ThisWayCommand(),
                new WaypointsCommand(),
                new FishCommand(),
                new CommandAlias("sethome", "Sets your home waypoint", "waypoints save home"),
                new CommandAlias("home", "Path to your home waypoint", "waypoints goto home"),
                selCommand
        ));
        for (ICommand command : commands) {
            ICommandManager.registry.register(command);
        }
        // SMH: no chat commands registered with the server (the companion's Baritone is driven in code).
    }

    private static void logRanCommand(CommandSourceStack source, String command, String rest) {
        if (BaritoneAPI.getGlobalSettings().echoCommands.get()) {
            String msg = command + rest;
            String toDisplay = BaritoneAPI.getGlobalSettings().censorRanCommands.get() ? command + " ..." : msg;
            source.sendSuccess(() -> {
                MutableComponent component = Component.literal(String.format("> %s", toDisplay));
                component.setStyle(component.getStyle()
                        .applyFormat(ChatFormatting.WHITE)
                        .withHoverEvent(CommandVer.getShowText(
                                Component.literal("Click to rerun command")
                        ))
                        .withClickEvent(CommandVer.getRunCommand(
                                FORCE_COMMAND_PREFIX + msg
                        )));
                return component;
            }, false);
        }
    }

    public static boolean runCommand(CommandSourceStack source, String msg, IBaritone baritone) throws CommandException {
        if (msg.trim().equalsIgnoreCase("damn")) {
            source.sendSuccess(() -> Component.literal("daniel"), false);
            return false;
        } else if (msg.trim().equalsIgnoreCase("orderpizza")) {
            Automatone.LOGGER.fatal("No pizza :(");
            return false;
        }
        if (msg.isEmpty()) {
            return runCommand(source, "help", baritone);
        }
        Tuple<String, List<ICommandArgument>> pair = BaritoneCommandManager.expand(msg);
        String command = pair.getA();
        String rest = msg.substring(pair.getA().length());
        ArgConsumer argc = new ArgConsumer(baritone.getCommandManager(), pair.getB(), baritone);
        if (!argc.hasAny()) {
            Settings.Setting<?> setting = BaritoneAPI.getGlobalSettings().byLowerName.get(command.toLowerCase(Locale.ROOT));
            if (setting != null) {
                logRanCommand(source, command, rest);
                if (setting.getValueClass() == Boolean.class) {
                    baritone.getCommandManager().execute(source, String.format("set toggle %s", setting.getName()));
                } else {
                    baritone.getCommandManager().execute(source, String.format("set %s", setting.getName()));
                }
                return true;
            }
        } else if (argc.hasExactlyOne()) {
            for (Settings.Setting<?> setting : BaritoneAPI.getGlobalSettings().allSettings) {
                if (setting.getName().equals("logger")) {
                    continue;
                }
                if (setting.getName().equalsIgnoreCase(pair.getA())) {
                    logRanCommand(source, command, rest);
                    try {
                        baritone.getCommandManager().execute(source, String.format("set %s %s", setting.getName(), argc.getString()));
                    } catch (CommandNotEnoughArgumentsException ignored) {
                    } // The operation is safe
                    return true;
                }
            }
        }

        // If the command exists, then handle echoing the input
        if (ICommandManager.getCommand(pair.getA()) != null) {
            logRanCommand(source, command, rest);
        }

        return baritone.getCommandManager().execute(source, pair);
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("automatone")
                
                .requires(s -> s.hasPermission(2))
                
                .then(Commands.argument("command", StringArgumentType.greedyString()).executes(command ->
                        runCommand(command.getSource(), command.getSource().getEntityOrException(), StringArgumentType.getString(command,"command"))))
        );
    }

    private static int runCommand(CommandSourceStack source, Entity target, String command) throws CommandSyntaxException {
        if (!(target instanceof LivingEntity)) throw EntityArgument.NO_ENTITIES_FOUND.create();
        try {
            return runCommand(source, command, BaritoneAPI.getProvider().getBaritone((ServerPlayer) target)) ? Command.SINGLE_SUCCESS : 0;
        } catch (CommandException e) {
            throw BARITONE_COMMAND_FAILED_EXCEPTION.create(e.handle());
        }
    }
}
