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

package me.sailex.automatone.utils.schematic;

import me.sailex.automatone.api.schematic.ISchematicSystem;
import me.sailex.automatone.api.schematic.format.ISchematicFormat;
import me.sailex.automatone.utils.schematic.format.DefaultSchematicFormats;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
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

    private final Registry<ISchematicFormat> registry = FabricRegistryBuilder.createSimple(ISchematicFormat.class, ResourceLocation.fromNamespaceAndPath("automatone", "schematics")).buildAndRegister();

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
