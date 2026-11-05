// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/util/serialization/ChunkPosDeserializer.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.util.serialization;

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

    @Override
    protected Integer parseUnit(String unit) throws Exception {
        return Integer.parseInt(unit);
    }

    @Override
    protected ChunkPos deserializeFromUnits(List<Integer> units) {
        return new ChunkPos(units.get(0), units.get(1));
    }

    @Override
    protected boolean isUnitTokenValid(JsonToken unitToken) {
        return false;
    }
}
