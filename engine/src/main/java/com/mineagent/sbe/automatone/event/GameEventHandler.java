// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/automatone/event/GameEventHandler.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
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

package com.mineagent.sbe.automatone.event;

import com.mineagent.sbe.automatone.Automatone;
import com.mineagent.sbe.automatone.Baritone;
import com.mineagent.sbe.automatone.api.event.events.BlockInteractEvent;
import com.mineagent.sbe.automatone.api.event.events.PathEvent;
import com.mineagent.sbe.automatone.api.event.listener.IEventBus;
import com.mineagent.sbe.automatone.api.event.listener.IGameEventListener;
import com.mineagent.sbe.automatone.utils.BlockStateInterface;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author Brady
 * @since 7/31/2018
 */
public final class GameEventHandler implements IEventBus {

    private final Baritone baritone;

    private final List<IGameEventListener> listeners = new CopyOnWriteArrayList<>();

    public GameEventHandler(Baritone baritone) {
        this.baritone = baritone;
    }

    @Override
    public void onTickServer() {
        try {
            baritone.bsi = new BlockStateInterface(baritone.getPlayerContext());
        } catch (Exception ex) {
            Automatone.LOGGER.error(ex);
            baritone.bsi = null;
        }
        listeners.forEach(IGameEventListener::onTickServer);
    }

    @Override
    public void onBlockInteract(BlockInteractEvent event) {
        listeners.forEach(l -> l.onBlockInteract(event));
    }

    @Override
    public void onPathEvent(PathEvent event) {
        listeners.forEach(l -> l.onPathEvent(event));
    }

    @Override
    public final void registerEventListener(IGameEventListener listener) {
        this.listeners.add(listener);
    }
}
