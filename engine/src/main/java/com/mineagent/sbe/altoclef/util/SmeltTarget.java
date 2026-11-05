// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/util/SmeltTarget.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.util;

import java.util.Objects;
import net.minecraft.world.item.Item;

public class SmeltTarget {
   private final ItemTarget item;
   private final Item[] optionalMaterials;
   private ItemTarget material;

   public SmeltTarget(ItemTarget item, ItemTarget material, Item... optionalMaterials) {
      this.item = item;
      this.material = material;
      this.material = new ItemTarget(material, this.item.getTargetCount());
      this.optionalMaterials = optionalMaterials;
   }

   public ItemTarget getItem() {
      return this.item;
   }

   public ItemTarget getMaterial() {
      return this.material;
   }

   public Item[] getOptionalMaterials() {
      return this.optionalMaterials;
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) {
         return true;
      } else if (o != null && this.getClass() == o.getClass()) {
         SmeltTarget that = (SmeltTarget)o;
         return Objects.equals(this.material, that.material) && Objects.equals(this.item, that.item);
      } else {
         return false;
      }
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.material, this.item);
   }
}
