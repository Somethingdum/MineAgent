// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/util/serialization/BlockPosDeserializer.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.util.serialization;

import com.fasterxml.jackson.core.JsonToken;
import java.util.List;
import net.minecraft.core.BlockPos;

public class BlockPosDeserializer extends AbstractVectorDeserializer<BlockPos, Integer> {
   @Override
   protected String getTypeName() {
      return "BlockPos";
   }

   @Override
   protected String[] getComponents() {
      return new String[]{"x", "y", "z"};
   }

   protected Integer parseUnit(String unit) throws Exception {
      return Integer.parseInt(unit);
   }

   protected BlockPos deserializeFromUnits(List<Integer> units) {
      return new BlockPos(units.get(0), units.get(1), units.get(2));
   }

   @Override
   protected boolean isUnitTokenValid(JsonToken token) {
      return token == JsonToken.VALUE_NUMBER_INT;
   }
}
