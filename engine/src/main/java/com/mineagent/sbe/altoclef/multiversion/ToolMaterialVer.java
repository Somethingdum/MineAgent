// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/multiversion/ToolMaterialVer.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.multiversion;


import net.minecraft.world.item.Item;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.Tiers;

public class ToolMaterialVer {
    
    
   public static int getMiningLevel(Item item) {
      return getMiningLevel(((TieredItem) item).getTier());
   }

   public static int getMiningLevel(Tier material) {
      if (material.equals(Tiers.WOOD) || material.equals(Tiers.GOLD)) {
         return 0;
      } else if (material.equals(Tiers.STONE)) {
         return 1;
      } else if (material.equals(Tiers.IRON)) {
         return 2;
      } else if (material.equals(Tiers.DIAMOND)) {
         return 3;
      } else if (material.equals(Tiers.NETHERITE)) {
         return 4;
      } else {
         throw new IllegalStateException("Unexpected value: " + material);
      }
   }
    
}
