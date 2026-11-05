package me.sailex.altoclef.multiversion;

import net.minecraft.world.InteractionResult;

public class ActionResultVer {

    public static boolean shouldSwingHand(InteractionResult actionResult) {
        return  actionResult.shouldSwing(); 
    }

}
