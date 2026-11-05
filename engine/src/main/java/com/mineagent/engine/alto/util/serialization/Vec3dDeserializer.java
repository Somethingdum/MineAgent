// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/util/serialization/Vec3dDeserializer.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.util.serialization;

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

    @Override
    protected Double parseUnit(String unit) throws Exception {
        return Double.parseDouble(unit);
    }

    @Override
    protected Vec3 deserializeFromUnits(List<Double> units) {
        return new Vec3(units.get(0), units.get(1), units.get(2));
    }

    @Override
    protected boolean isUnitTokenValid(JsonToken token) {
        return token == JsonToken.VALUE_NUMBER_INT || token == JsonToken.VALUE_NUMBER_FLOAT;
    }

}
