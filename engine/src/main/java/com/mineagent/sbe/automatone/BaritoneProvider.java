// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/automatone/BaritoneProvider.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
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

package com.mineagent.sbe.automatone;

import com.mineagent.sbe.altoclef.multiversion.EntityVer;
import com.mineagent.sbe.automatone.api.IBaritone;
import com.mineagent.sbe.automatone.api.IBaritoneProvider;
import com.mineagent.sbe.automatone.api.Settings;
import com.mineagent.sbe.automatone.api.cache.IWorldScanner;
import com.mineagent.sbe.automatone.api.command.ICommandSystem;
import com.mineagent.sbe.automatone.api.schematic.ISchematicSystem;
import com.mineagent.sbe.automatone.cache.WorldScanner;
import com.mineagent.sbe.automatone.command.CommandSystem;
import com.mineagent.sbe.automatone.utils.SettingsLoader;
import com.mineagent.sbe.automatone.utils.schematic.SchematicSystem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Brady
 * @since 9/29/2018
 */
public final class BaritoneProvider implements IBaritoneProvider {

    public static final BaritoneProvider INSTANCE = new BaritoneProvider();

    public final Map<ServerPlayer, IBaritone> playerToBaritone;

    private final Settings settings;

    public BaritoneProvider() {
        this.settings = new Settings();
        this.playerToBaritone = new HashMap<>();
        SettingsLoader.readAndApply(settings);
    }

    @Override
    public IBaritone getBaritone(ServerPlayer entity) {
        if (EntityVer.getWorld(entity).isClientSide()) throw new IllegalStateException("lol we only support servers now");

        IBaritone baritone = playerToBaritone.get(entity);
        if (baritone == null) {
            return new Baritone(entity);
        }
        return baritone;
    }

    public boolean isPathing(LivingEntity entity) {
        IBaritone baritone = playerToBaritone.get(entity);
        return baritone != null && baritone.isActive();
    }

    @Override
    public IWorldScanner getWorldScanner() {
        return WorldScanner.INSTANCE;
    }

    @Override
    public ICommandSystem getCommandSystem() {
        return CommandSystem.INSTANCE;
    }

    @Override
    public ISchematicSystem getSchematicSystem() {
        return SchematicSystem.INSTANCE;
    }

    @Override
    public Settings getGlobalSettings() {
        return this.settings;
    }
}
