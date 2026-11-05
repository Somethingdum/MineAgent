package me.sailex.altoclef.multiversion;

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
