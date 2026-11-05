// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/automatone/utils/schematic/SchematicSystem.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
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

package com.mineagent.sbe.automatone.utils.schematic;

import com.mineagent.sbe.automatone.api.schematic.ISchematicSystem;
import com.mineagent.sbe.automatone.api.schematic.format.ISchematicFormat;
import com.mineagent.sbe.automatone.utils.schematic.format.DefaultSchematicFormats;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import java.io.File;
import java.util.Locale;
import java.util.Optional;

/**
 * @author Brady
 * @since 12/24/2019
 */
public enum SchematicSystem implements ISchematicSystem {
    INSTANCE;

    private final Registry<ISchematicFormat> registry = new net.minecraft.core.MappedRegistry<>(net.minecraft.resources.ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath("automatone", "schematics")), com.mojang.serialization.Lifecycle.stable()); // SMH: not in the game's root registry

    SchematicSystem() {
        for (DefaultSchematicFormats s : DefaultSchematicFormats.values()) {
            Registry.register(this.registry, ResourceLocation.fromNamespaceAndPath("automatone", s.name().toLowerCase(Locale.ROOT)), s);
        }
    }

    @Override
    public Registry<ISchematicFormat> getRegistry() {
        return this.registry;
    }

    @Override
    public Optional<ISchematicFormat> getByFile(File file) {
        return this.registry.stream().filter(format -> format.isFileType(file)).findFirst();
    }
}
