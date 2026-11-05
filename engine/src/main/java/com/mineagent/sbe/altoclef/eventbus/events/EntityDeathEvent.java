// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/eventbus/events/EntityDeathEvent.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.eventbus.events;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;

public class EntityDeathEvent {
   public Entity entity;
   public DamageSource damageSource;

   public EntityDeathEvent(Entity entity, DamageSource damageSource) {
      this.entity = entity;
      this.damageSource = damageSource;
   }
}
