// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/automatone/api/schematic/format/ISchematicFormat.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
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

package com.mineagent.sbe.automatone.api.schematic.format;

import com.mineagent.sbe.automatone.api.schematic.ISchematic;
import com.mineagent.sbe.automatone.api.schematic.IStaticSchematic;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * The base of a {@link ISchematic} file format
 *
 * @author Brady
 * @since 12/23/2019
 */
public interface ISchematicFormat {

    /**
     * @return The parser for creating schematics of this format
     */
    IStaticSchematic parse(InputStream input) throws IOException;

    /**
     * @param file The file to check against
     * @return Whether or not the specified file matches this schematic format
     */
    boolean isFileType(File file);
}
