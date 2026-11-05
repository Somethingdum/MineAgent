// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/automatone/api/command/IBaritoneChatControl.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
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

package com.mineagent.sbe.automatone.api.command;

/**
 * @author Brady
 * @since 9/26/2019
 */
public interface IBaritoneChatControl {

    /**
     * In certain cases chat components need to execute commands for you. For example, the paginator automatically runs
     * commands when you click the forward and back arrows to show you the previous/next page.
     * <p>
     * If the prefix is changed in the meantime, then the command will go to chat. That's no good. So here's a permanent
     * prefix that forces a command to run, regardless of the current prefix, chat/prefix control being enabled, etc.
     * <p>
     * If used right (by both developers and users), it should be impossible to expose a command accidentally to the
     * server. As a rule of thumb, if you have a clickable chat component, always use this prefix.
     */
    String FORCE_COMMAND_PREFIX = "/automatone ";
}
