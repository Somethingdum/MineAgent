package me.sailex.altoclef.multiversion;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class EntityVer {

    public static Vec3 getPos(Entity entity) {
        
        return entity.position();
         
    }

    public static Level getWorld(Entity entity) {
        
        return entity.level();
         
    }

}
