// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/util/serialization/BlockPosDeserializer.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.util.serialization;

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

    @Override
    protected Integer parseUnit(String unit) throws Exception {
        return Integer.parseInt(unit);
    }

    @Override
    protected BlockPos deserializeFromUnits(List<Integer> units) {
        return new BlockPos(units.get(0), units.get(1), units.get(2));
    }

    @Override
    protected boolean isUnitTokenValid(JsonToken token) {
        return token == JsonToken.VALUE_NUMBER_INT;
    }
}
