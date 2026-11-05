// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/automatone/api/schematic/ISchematicSystem.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
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

package com.mineagent.sbe.automatone.api.schematic;

import com.mineagent.sbe.automatone.api.schematic.format.ISchematicFormat;
import net.minecraft.core.Registry;
import java.io.File;
import java.util.Optional;

/**
 * @author Brady
 * @since 12/23/2019
 */
public interface ISchematicSystem {

    /**
     * @return The registry of supported schematic formats
     */
    Registry<ISchematicFormat> getRegistry();

    /**
     * Attempts to find an {@link ISchematicFormat} that supports the specified schematic file.
     *
     * @param file A schematic file
     * @return The corresponding format for the file, {@link Optional#empty()} if no candidates were found.
     */
    Optional<ISchematicFormat> getByFile(File file);
}
