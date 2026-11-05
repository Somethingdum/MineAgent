// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/mixins/PlayerDamageMixin.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.mixins;

import com.mineagent.sbe.altoclef.eventbus.EventBus;
import com.mineagent.sbe.altoclef.eventbus.events.PlayerDamageEvent;
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
      method = {"hurt"}, // SMH: Mojang's name
      at = {@At("HEAD")}
    )
    
    public void applyDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> ci) {
      EventBus.publish(new PlayerDamageEvent(this, source, amount));
    }
    
}
