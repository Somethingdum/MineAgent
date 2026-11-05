package me.sailex.mixins;

import net.minecraft.world.entity.projectile.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;

import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({AbstractArrow.class})
public interface PersistentProjectileEntityAccessor {

    
    
    @Accessor("inGround")
    boolean getIsInGround();

    
}
