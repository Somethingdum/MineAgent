package me.sailex.altoclef.multiversion;

import net.minecraft.world.entity.monster.Creeper;

public class MobEntityVer {

    public static float getClientFuseTime(Creeper creeperEntity, float tickProgress) {
        
        return creeperEntity.getSwelling(tickProgress);
         
    }

}
