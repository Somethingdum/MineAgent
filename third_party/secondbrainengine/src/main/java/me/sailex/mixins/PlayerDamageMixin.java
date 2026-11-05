package me.sailex.mixins;

import me.sailex.altoclef.eventbus.EventBus;
import me.sailex.altoclef.eventbus.events.PlayerDamageEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class PlayerDamageMixin extends Entity {

    protected PlayerDamageMixin(EntityType<? extends LivingEntity> entityType, Level world) {
        super(entityType, world);
    }

    @Inject(
      method = {"damage"},
      at = {@At("HEAD")}
    )
    
    public void applyDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> ci) {
      EventBus.publish(new PlayerDamageEvent(this, source, amount));
    }
    
}
