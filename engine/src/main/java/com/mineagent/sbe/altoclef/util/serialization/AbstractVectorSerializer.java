// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/util/serialization/AbstractVectorSerializer.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.util.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;
import java.util.Collection;

public abstract class AbstractVectorSerializer<T> extends StdSerializer<T> {
   protected AbstractVectorSerializer() {
      this(null);
   }

   protected AbstractVectorSerializer(Class<T> vc) {
      super(vc);
   }

   protected abstract Collection<String> getParts(T var1);

   @Override
   public void serialize(T value, JsonGenerator gen, SerializerProvider provider) throws IOException {
      Collection<String> parts = this.getParts(value);
      gen.writeString(String.join(",", parts));
   }
}
