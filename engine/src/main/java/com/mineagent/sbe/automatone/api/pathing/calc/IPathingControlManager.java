// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/automatone/api/pathing/calc/IPathingControlManager.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
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

package com.mineagent.sbe.automatone.api.pathing.calc;

import com.mineagent.sbe.automatone.api.process.IBaritoneProcess;
import com.mineagent.sbe.automatone.api.process.PathingCommand;

import java.util.Optional;

/**
 * @author leijurv
 */
public interface IPathingControlManager {

    /**
     * Registers a process with this pathing control manager. See {@link IBaritoneProcess} for more details.
     *
     * @param process The process
     * @see IBaritoneProcess
     */
    void registerProcess(IBaritoneProcess process);

    /**
     * @return The most recent {@link IBaritoneProcess} that had control
     */
    Optional<IBaritoneProcess> mostRecentInControl();

    /**
     * @return The most recent pathing command executed
     */
    Optional<PathingCommand> mostRecentCommand();
}
