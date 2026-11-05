// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/util/serialization/ChunkPosDeserializer.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.util.serialization;

import com.fasterxml.jackson.core.JsonToken;
import java.util.List;
import net.minecraft.world.level.ChunkPos;

public class ChunkPosDeserializer extends AbstractVectorDeserializer<ChunkPos, Integer> {
   @Override
   protected String getTypeName() {
      return "ChunkPos";
   }

   @Override
   protected String[] getComponents() {
      return new String[]{"x", "z"};
   }

   protected Integer parseUnit(String unit) throws Exception {
      return Integer.parseInt(unit);
   }

   protected ChunkPos deserializeFromUnits(List<Integer> units) {
      return new ChunkPos(units.get(0), units.get(1));
   }

   @Override
   protected boolean isUnitTokenValid(JsonToken unitToken) {
      return false;
   }
}
