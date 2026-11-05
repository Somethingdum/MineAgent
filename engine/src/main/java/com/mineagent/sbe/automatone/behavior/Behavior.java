// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/automatone/behavior/Behavior.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
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

package com.mineagent.sbe.automatone.behavior;

import com.mineagent.sbe.automatone.Baritone;
import com.mineagent.sbe.automatone.api.behavior.IBehavior;
import com.mineagent.sbe.automatone.api.utils.IEntityContext;

/**
 * A type of game event listener that is given {@link Baritone} instance context.
 *
 * @author Brady
 * @since 8/1/2018
 */
public class Behavior implements IBehavior {

    public final Baritone baritone;
    public final IEntityContext ctx;

    protected Behavior(Baritone baritone) {
        this.baritone = baritone;
        this.ctx = baritone.getPlayerContext();
        baritone.registerBehavior(this);
    }
}
