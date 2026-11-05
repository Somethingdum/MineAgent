// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/util/MiningRequirement.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.util;

import com.mineagent.sbe.altoclef.Debug;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

public enum MiningRequirement implements Comparable<MiningRequirement> {
   HAND(Items.AIR),
   WOOD(Items.WOODEN_PICKAXE),
   STONE(Items.STONE_PICKAXE),
   IRON(Items.IRON_PICKAXE),
   DIAMOND(Items.DIAMOND_PICKAXE);

   private final Item minPickaxe;

   private MiningRequirement(Item minPickaxe) {
      this.minPickaxe = minPickaxe;
   }

   public static MiningRequirement getMinimumRequirementForBlock(Block block) {
      if (block.defaultBlockState().requiresCorrectToolForDrops()) {
         for (MiningRequirement req : values()) {
            if (req != HAND) {
                
                ItemStack pick = new ItemStack(req.getMinimumPickaxe());
                
               if (pick.isCorrectToolForDrops(block.defaultBlockState())) {
                  return req;
               }
            }
         }

         Debug.logWarning(
            "Failed to find ANY effective tool against: " + block + ". I assume netherite is not required anywhere, so something else probably went wrong."
         );
         return DIAMOND;
      } else {
         return HAND;
      }
   }

   public Item getMinimumPickaxe() {
      return this.minPickaxe;
   }
}
