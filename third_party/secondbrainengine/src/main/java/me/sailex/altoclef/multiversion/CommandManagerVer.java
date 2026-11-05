package me.sailex.altoclef.multiversion;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public class CommandManagerVer {

    public static void executeWithPrefix(Commands commandManager, CommandSourceStack source, String command) {
        
        commandManager.performPrefixedCommand(source, command);
        
    }
}
