// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/mixins/EntityAnimationSwungMixin.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.mixins;

import com.mineagent.sbe.altoclef.eventbus.EventBus;
import com.mineagent.sbe.altoclef.eventbus.events.EntitySwungEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({LivingEntity.class})
public abstract class EntityAnimationSwungMixin {
   @Inject(
      method = {"swing(Lnet/minecraft/world/InteractionHand;)V" /* SMH: Mojang's name */},
      at = {@At("HEAD")}
   )
   private void onEntityAnimation(InteractionHand hand, CallbackInfo ci) {
      EventBus.publish(new EntitySwungEvent((LivingEntity)(Object)this));
   }
}
