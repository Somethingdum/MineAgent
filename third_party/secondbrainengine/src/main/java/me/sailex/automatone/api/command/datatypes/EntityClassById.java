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

package me.sailex.automatone.api.command.datatypes;

import me.sailex.altoclef.multiversion.RegistriesVer;
import me.sailex.automatone.api.command.exception.CommandException;
import me.sailex.automatone.api.command.helpers.TabCompleteHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import java.util.stream.Stream;

public enum EntityClassById implements IDatatypeFor<EntityType<?>> {
    INSTANCE;

    @Override
    public EntityType<?> get(IDatatypeContext ctx) throws CommandException {
        
        ResourceLocation id = ResourceLocation.parse(ctx.getConsumer().getString());
        

        EntityType<?> entity = RegistriesVer.get(BuiltInRegistries.ENTITY_TYPE,  id);
        if (entity == null) {
            throw new IllegalArgumentException("no entity found by that id");
        }
        return entity;
    }

    @Override
    public Stream<String> tabComplete(IDatatypeContext ctx) throws CommandException {
        return new TabCompleteHelper()
                .append(BuiltInRegistries.ENTITY_TYPE.stream().map(Object::toString))
                .filterPrefixNamespaced(ctx.getConsumer().getString())
                .sortAlphabetically()
                .stream();
    }
}
