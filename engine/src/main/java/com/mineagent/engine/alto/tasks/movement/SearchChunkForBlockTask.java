// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/tasks/movement/SearchChunkForBlockTask.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.tasks.movement;

import com.mineagent.engine.alto.AltoClef;
import com.mineagent.engine.alto.tasksystem.Task;
import org.apache.commons.lang3.ArrayUtils;

import java.util.Arrays;
import java.util.HashSet;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;

public class SearchChunkForBlockTask extends SearchChunksExploreTask {

    private final HashSet<Block> _toSearchFor = new HashSet<>();

    public SearchChunkForBlockTask(Block... blocks) {
        _toSearchFor.addAll(Arrays.asList(blocks));
    }

    @Override
    protected boolean isChunkWithinSearchSpace(AltoClef mod, ChunkPos pos) {
        return mod.getChunkTracker().scanChunk(pos, block -> {
                    return _toSearchFor.contains(mod.getWorld().getBlockState(block).getBlock());
                }
        );
    }

    @Override
    protected boolean isEqual(Task other) {
        if (other instanceof SearchChunkForBlockTask blockTask) {
            return Arrays.equals(blockTask._toSearchFor.toArray(Block[]::new), _toSearchFor.toArray(Block[]::new));
        }
        return false;
    }

    @Override
    protected String toDebugString() {
        return "Searching chunk for blocks " + ArrayUtils.toString(_toSearchFor.toArray(Block[]::new));
    }
}
