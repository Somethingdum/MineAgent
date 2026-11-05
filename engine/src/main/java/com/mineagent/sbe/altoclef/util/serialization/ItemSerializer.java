// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/util/serialization/ItemSerializer.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.util.serialization;

import com.mineagent.sbe.altoclef.util.helpers.ItemHelper;
import net.minecraft.world.item.Item;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;
import java.util.List;

public class ItemSerializer extends StdSerializer<Object> {
   public ItemSerializer() {
      this(null);
   }

   public ItemSerializer(Class<Object> vc) {
      super(vc);
   }

   @Override
   public void serialize(Object value, JsonGenerator gen, SerializerProvider provider) throws IOException {
      List<Item> items = (List<Item>)value;
      gen.writeStartArray();

      for (Item item : items) {
         String key = ItemHelper.trimItemName(item.getDescriptionId());
         gen.writeString(key);
      }

      gen.writeEndArray();
   }
}
