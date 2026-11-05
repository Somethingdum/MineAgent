// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/util/serialization/ChunkPosSerializer.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.util.serialization;

import java.util.Arrays;
import java.util.Collection;
import net.minecraft.world.level.ChunkPos;

public class ChunkPosSerializer extends AbstractVectorSerializer<ChunkPos> {
    @Override
    protected Collection<String> getParts(ChunkPos value) {
        return Arrays.asList("" + value.x, "" + value.z);
    }
}
