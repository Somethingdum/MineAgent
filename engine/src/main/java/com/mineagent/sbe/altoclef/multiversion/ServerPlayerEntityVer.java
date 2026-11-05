// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/multiversion/ServerPlayerEntityVer.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.multiversion;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

public class ServerPlayerEntityVer {

    public static ItemEntity dropStack(ServerPlayer player, ItemStack stack, int amount) {
        
        return player.spawnAtLocation(stack, amount);
         
    }

    public static ItemEntity dropStack(ServerPlayer player, ItemStack stack) {
        
        return player.spawnAtLocation(stack);
         
    }

    public static ServerLevel getWorld(ServerPlayer player) {
        
        return player.serverLevel();
        
    }

}
