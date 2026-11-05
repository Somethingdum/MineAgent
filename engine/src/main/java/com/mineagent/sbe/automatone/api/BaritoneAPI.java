// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/automatone/api/BaritoneAPI.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
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

package com.mineagent.sbe.automatone.api;

import java.util.Calendar;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Exposes the {@link IBaritoneProvider} instance and the {@link Settings} instance for API usage.
 *
 * @author Brady
 * @since 9/23/2018
 */
public final class BaritoneAPI {

    private static final IBaritoneProvider provider;

    static {
        try {
            provider = (IBaritoneProvider) Class.forName("com.mineagent.sbe.automatone.BaritoneProvider").getField("INSTANCE").get(null);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }

    public static IBaritoneProvider getProvider() {
        return BaritoneAPI.provider;
    }

    public static Settings getGlobalSettings() {
        return getProvider().getGlobalSettings();
    }

    public static Component getPrefix() {
        // Inner text component
        final Calendar now = Calendar.getInstance();
        final boolean xd = now.get(Calendar.MONTH) == Calendar.APRIL && now.get(Calendar.DAY_OF_MONTH) <= 3;
        MutableComponent baritone = Component.literal(xd ? "Automatoe" : getGlobalSettings().shortBaritonePrefix.get() ? "A" : "Automatone");
        baritone.setStyle(baritone.getStyle().applyFormat(ChatFormatting.GREEN));

        // Outer brackets
        MutableComponent prefix = Component.literal("");
        prefix.setStyle(baritone.getStyle().applyFormat(ChatFormatting.DARK_GREEN));
        prefix.append("[");
        prefix.append(baritone);
        prefix.append("]");

        return prefix;
    }
}
