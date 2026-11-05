package me.sailex.altoclef.multiversion.world;

import net.minecraft.world.level.Level;

public class HeightLimitViewVer {

    public static int getTopY(Level world) {
        
        
        return world.getMaxBuildHeight();
         
    }

}
