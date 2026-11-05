// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/multiversion/CommandVer.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.multiversion;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

public class CommandVer {

    public static ClickEvent getRunCommand(String value) {
        
        
        return new ClickEvent(ClickEvent.Action.RUN_COMMAND, value);
        
    }

    public static ClickEvent getSuggestCommand(String value) {
        
        
        return new ClickEvent(ClickEvent.Action.RUN_COMMAND, value);
        
    }

    public static HoverEvent getShowText(Component value) {
        
        
        return new HoverEvent(HoverEvent.Action.SHOW_TEXT, value);
        
    }

}
