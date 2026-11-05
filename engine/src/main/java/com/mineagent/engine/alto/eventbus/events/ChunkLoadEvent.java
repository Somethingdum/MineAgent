// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/eventbus/events/ChunkLoadEvent.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.eventbus.events;

import net.minecraft.world.level.chunk.LevelChunk;

public class ChunkLoadEvent {
    public LevelChunk chunk;

    public ChunkLoadEvent(LevelChunk chunk) {
        this.chunk = chunk;
    }
}
