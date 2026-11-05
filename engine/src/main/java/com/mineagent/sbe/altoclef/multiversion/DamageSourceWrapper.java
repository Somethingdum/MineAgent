// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/multiversion/DamageSourceWrapper.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.multiversion;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;

public class DamageSourceWrapper {
   private final DamageSource source;

   public static DamageSourceWrapper of(DamageSource source) {
      return source == null ? null : new DamageSourceWrapper(source);
   }

   private DamageSourceWrapper(DamageSource source) {
      this.source = source;
   }

   public DamageSource getSource() {
      return this.source;
   }

   public boolean bypassesArmor() {
      return this.source.is(DamageTypeTags.BYPASSES_ARMOR);
   }

   public boolean bypassesShield() {
      return this.source.is(DamageTypeTags.BYPASSES_SHIELD);
   }

   public boolean isOutOfWorld() {
      return this.source.is(DamageTypes.FELL_OUT_OF_WORLD);
   }
}
