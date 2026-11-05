// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/multiversion/entity/PlayerVer.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.multiversion.entity;

import com.mineagent.engine.alto.multiversion.Pattern;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class PlayerVer {


    // SMH: what AltoClef would say in chat is its log here (the companion speaks through its own loop); a command it
    // would type is run as the server runs a player's command.
    public static void sendChatMessage(ServerPlayer player,String content) {
        com.mineagent.engine.alto.Debug.logMessage(content);
    }

    public static void sendChatCommand(ServerPlayer player,String content) {
        player.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), content);
    }

    @Pattern
    private static ItemStack getCursorStack(Player player) {
        return player.containerMenu.getCarried();
    }

    @Pattern
    private static Container getInventory(Player player) {
        return player.getInventory();
    }

    public static boolean inPowderedSnow(Player player) {
        return player.isInPowderSnow;
    }



}
