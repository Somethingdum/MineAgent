// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/multiversion/MethodWrapper.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.multiversion;

import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.Level;



public class MethodWrapper {
   public static Entity getRenderedEntity(BaseSpawner logic, Level world, BlockPos pos) {
        
       return logic.getOrCreateDisplayEntity(world, pos);
        
   }

   public static float getDamageLeft(LivingEntity armorWearer, double damage, DamageSource source, double armor, double armorToughness) {
      return getDamageLeft(armorWearer, (float)damage, source, (float)armor, (float)armorToughness);
   }

   public static float getDamageLeft(LivingEntity armorWearer, float damage, DamageSource source, float armor, float armorToughness) {
       
       return CombatRules.getDamageAfterAbsorb(armorWearer, damage, source, armor, armorToughness);
       
   }
}
