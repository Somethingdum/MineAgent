// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/automatone/api/IBaritoneProvider.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
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

import com.mineagent.sbe.automatone.api.cache.IWorldScanner;
import com.mineagent.sbe.automatone.api.command.ICommand;
import com.mineagent.sbe.automatone.api.command.ICommandSystem;
import com.mineagent.sbe.automatone.api.schematic.ISchematicSystem;
import net.minecraft.server.level.ServerPlayer;

/**
 * Provides the present {@link IBaritone} instances, as well as non-baritone instance related APIs.
 *
 * @author leijurv
 */
public interface IBaritoneProvider {

    /**
     * Provides the {@link IBaritone} instance for a given {@link ServerPlayer}.
     *
     * @param player The player
     * @return The {@link IBaritone} instance.
     */
    IBaritone getBaritone(ServerPlayer player);

    /**
     * Returns the {@link IWorldScanner} instance. This is not a type returned by
     * {@link IBaritone} implementation, because it is not linked with {@link IBaritone}.
     *
     * @return The {@link IWorldScanner} instance.
     */
    IWorldScanner getWorldScanner();

    /**
     * Returns the {@link ICommandSystem} instance. This is not bound to a specific {@link IBaritone}
     * instance because {@link ICommandSystem} itself controls global behavior for {@link ICommand}s.
     *
     * @return The {@link ICommandSystem} instance.
     */
    ICommandSystem getCommandSystem();

    /**
     * @return The {@link ISchematicSystem} instance.
     */
    ISchematicSystem getSchematicSystem();

    Settings getGlobalSettings();
}
