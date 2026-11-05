// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/automatone/api/command/datatypes/IDatatypeContext.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
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

import com.mineagent.sbe.automatone.api.IBaritone;
import com.mineagent.sbe.automatone.api.command.argument.IArgConsumer;

/**
 * Provides an {@link IDatatype} with contextual information so
 * that it can perform the desired operation on the target level.
 *
 * @author Brady
 * @see IDatatype
 * @since 9/26/2019
 */
public interface IDatatypeContext {

    /**
     * Provides the {@link IBaritone} instance that is associated with the action relating to datatype handling.
     *
     * @return The context {@link IBaritone} instance.
     */
    IBaritone getBaritone();

    /**
     * Provides the {@link IArgConsumer}} to fetch input information from.
     *
     * @return The context {@link IArgConsumer}}.
     */
    IArgConsumer getConsumer();
}
