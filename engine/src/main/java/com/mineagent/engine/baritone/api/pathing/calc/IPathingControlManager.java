// Ported from Baritone (LGPL-3.0, the licence of this mod; see THIRD_PARTY_NOTICES.md): src/api/java/baritone/api/pathing/calc/IPathingControlManager.java at commit 11ea66c
// (branch 1.21.1). Changes for the companion's server-side body are marked SMH.
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

package com.mineagent.engine.baritone.api.pathing.calc;

import com.mineagent.engine.baritone.api.process.IBaritoneProcess;
import com.mineagent.engine.baritone.api.process.PathingCommand;

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
