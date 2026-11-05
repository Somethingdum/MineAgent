// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/multiversion/FoodComponentWrapper.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.multiversion;


import net.minecraft.world.food.FoodProperties;



public class FoodComponentWrapper {
   private final FoodProperties component;

   public static FoodComponentWrapper of(FoodProperties component) {
      return component == null ? null : new FoodComponentWrapper(component);
   }

   private FoodComponentWrapper(FoodProperties component) {
      this.component = component;
   }

   public int getHunger() {
       
       return this.component.nutrition();
        
   }

   public float getSaturationModifier() {
       
       return component.nutrition() == 0 ? 0.0f : component.saturation() / (component.nutrition() * 2.0f);
       
   }
}
