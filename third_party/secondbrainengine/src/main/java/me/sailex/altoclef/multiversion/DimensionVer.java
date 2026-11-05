package me.sailex.altoclef.multiversion;

import net.minecraft.world.level.Level;



public class DimensionVer {

    public static boolean isUltrawarm(Level world) {
        
        return world.dimensionType().ultraWarm();
        
    }
}
