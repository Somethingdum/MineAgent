package me.sailex.altoclef.multiversion;

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
