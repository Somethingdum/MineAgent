// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/util/serialization/Vec3dDeserializer.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.util.serialization;

import com.fasterxml.jackson.core.JsonToken;
import java.util.List;
import net.minecraft.world.phys.Vec3;

public class Vec3dDeserializer extends AbstractVectorDeserializer<Vec3, Double> {
   @Override
   protected String getTypeName() {
      return "Vec3d";
   }

   @Override
   protected String[] getComponents() {
      return new String[]{"x", "y"};
   }

   protected Double parseUnit(String unit) throws Exception {
      return Double.parseDouble(unit);
   }

   protected Vec3 deserializeFromUnits(List<Double> units) {
      return new Vec3(units.get(0), units.get(1), units.get(2));
   }

   @Override
   protected boolean isUnitTokenValid(JsonToken token) {
      return token == JsonToken.VALUE_NUMBER_INT || token == JsonToken.VALUE_NUMBER_FLOAT;
   }
}
