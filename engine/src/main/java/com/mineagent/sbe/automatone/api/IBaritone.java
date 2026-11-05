// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/automatone/api/IBaritone.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
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

package com.mineagent.sbe.automatone.api;

import com.mineagent.sbe.automatone.api.behavior.ILookBehavior;
import com.mineagent.sbe.automatone.api.behavior.IPathingBehavior;
import com.mineagent.sbe.automatone.api.cache.IWorldProvider;
import com.mineagent.sbe.automatone.api.command.manager.ICommandManager;
import com.mineagent.sbe.automatone.api.event.listener.IEventBus;
import com.mineagent.sbe.automatone.api.pathing.calc.IPathingControlManager;
import com.mineagent.sbe.automatone.api.process.IBaritoneProcess;
import com.mineagent.sbe.automatone.api.process.IBuilderProcess;
import com.mineagent.sbe.automatone.api.process.ICustomGoalProcess;
import com.mineagent.sbe.automatone.api.process.IExploreProcess;
import com.mineagent.sbe.automatone.api.process.IFarmProcess;
import com.mineagent.sbe.automatone.api.process.IFollowProcess;
import com.mineagent.sbe.automatone.api.process.IGetToBlockProcess;
import com.mineagent.sbe.automatone.api.process.IMineProcess;
import com.mineagent.sbe.automatone.api.selection.ISelectionManager;
import com.mineagent.sbe.automatone.api.utils.ICommandHelper;
import com.mineagent.sbe.automatone.api.utils.IEntityContext;
import com.mineagent.sbe.automatone.api.utils.IInputOverrideHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import java.util.Arrays;
import java.util.stream.Stream;

/**
 * @author Brady
 * @since 9/29/2018
 */
public interface IBaritone {

    /**
     * @return The {@link IPathingBehavior} instance
     * @see IPathingBehavior
     */
    IPathingBehavior getPathingBehavior();

    /**
     * @return The {@link ILookBehavior} instance
     * @see ILookBehavior
     */
    ILookBehavior getLookBehavior();

    /**
     * @return The {@link IFollowProcess} instance
     * @see IFollowProcess
     */
    IFollowProcess getFollowProcess();

    /**
     * @return The {@link IMineProcess} instance
     * @see IMineProcess
     */
    IMineProcess getMineProcess();

    /**
     * @return The {@link IBuilderProcess} instance
     * @see IBuilderProcess
     */
    IBuilderProcess getBuilderProcess();

    /**
     * @return The {@link IExploreProcess} instance
     * @see IExploreProcess
     */
    IExploreProcess getExploreProcess();

    /**
     * @return The {@link IFarmProcess} instance
     * @see IFarmProcess
     */
    IFarmProcess getFarmProcess();

    /**
     * @return The {@link ICustomGoalProcess} instance
     * @see ICustomGoalProcess
     */
    ICustomGoalProcess getCustomGoalProcess();

    /**
     * @return The {@link IGetToBlockProcess} instance
     * @see IGetToBlockProcess
     */
    IGetToBlockProcess getGetToBlockProcess();

    /**
     * @return The {@link IWorldProvider} instance
     * @see IWorldProvider
     */
    IWorldProvider getWorldProvider();

    ISelectionManager getSelectionManager();

    /**
     * Returns the {@link IPathingControlManager} for this {@link IBaritone} instance, which is responsible
     * for managing the {@link IBaritoneProcess}es which control the {@link IPathingBehavior} state.
     *
     * @return The {@link IPathingControlManager} instance
     * @see IPathingControlManager
     */
    IPathingControlManager getPathingControlManager();

    /**
     * @return The {@link IInputOverrideHandler} instance
     * @see IInputOverrideHandler
     */
    IInputOverrideHandler getInputOverrideHandler();

    /**
     * @return The {@link IEntityContext} instance
     * @see IEntityContext
     */
    IEntityContext getPlayerContext();

    /**
     * @return The {@link IEventBus} instance
     * @see IEventBus
     */
    IEventBus getGameEventHandler();

    /**
     * @return The {@link ICommandManager} instance
     * @see ICommandManager
     */
    ICommandManager getCommandManager();

    /**
     * Send a message to chat only if chatDebug is on
     *
     * @param message The message to display in chat
     */
    void logDebug(String message);

    /**
     * Send components to chat with the [Automatone] prefix
     *
     * @param components The components to send
     */
    default void logDirect(Component... components) {
        IEntityContext playerContext = this.getPlayerContext();
        LivingEntity entity = playerContext.entity();
        if (entity instanceof Player) {
            MutableComponent component = Component.literal("");
            // If we are not logging as a Toast
            // Append the prefix to the base component line
            component.append(BaritoneAPI.getPrefix());
            component.append(Component.literal(" "));
            Arrays.asList(components).forEach(component::append);
            ((Player) entity).displayClientMessage(component, false);
        }
    }

    /**
     * Send a message to chat regardless of chatDebug (should only be used for critically important messages, or as a
     * direct response to a chat command)
     *
     * @param message The message to display in chat
     * @param color   The color to print that message in
     */
    default void logDirect(String message, ChatFormatting color) {
        Stream.of(message.split("\n")).forEach(line -> {
            MutableComponent component = Component.literal(line.replace("\t", "    "));
            component.setStyle(component.getStyle().applyFormat(color));
            logDirect(component);
        });
    }

    /**
     * Send a message to chat regardless of chatDebug (should only be used for critically important messages, or as a
     * direct response to a chat command)
     *
     * @param message The message to display in chat
     */
    default void logDirect(String message) {
        logDirect(message, ChatFormatting.GRAY);
    }

    boolean isActive();

    Settings settings();

    ICommandHelper getCommandHelper();

    void serverTick();
}
