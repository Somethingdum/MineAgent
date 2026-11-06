// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/trackers/SimpleChunkTracker.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.trackers;

import com.mineagent.engine.alto.AltoClef;
import com.mineagent.engine.alto.Debug;
import com.mineagent.engine.alto.eventbus.EventBus;
import com.mineagent.engine.alto.eventbus.events.ChunkLoadEvent;
import com.mineagent.engine.alto.eventbus.events.ChunkUnloadEvent;
import com.mineagent.engine.alto.util.helpers.WorldHelper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

/**
 * Keeps track of currently loaded chunks. That's it.
 */
public class SimpleChunkTracker {

    private final AltoClef mod;
    private final Set<ChunkPos> loaded = new HashSet<>();

    public SimpleChunkTracker(AltoClef mod) {
        this.mod = mod;

        // When chunks load...
        EventBus.subscribe(ChunkLoadEvent.class, evt -> onLoad(evt.chunk.getPos()));
        EventBus.subscribe(ChunkUnloadEvent.class, evt -> onUnload(evt.chunkPos));
    }

    private void onLoad(ChunkPos pos) {
        //Debug.logInternal("LOADED: " + pos);
        loaded.add(pos);
    }

    private void onUnload(ChunkPos pos) {
        //Debug.logInternal("unloaded: " + pos);
        loaded.remove(pos);
    }

    public boolean isChunkLoaded(ChunkPos pos) {
        // SMH: a client's getChunk gives an empty chunk for one it has not loaded; a server's loads or generates it.
        // Asked the way WorldHelper.blockAt asks, on any thread and never loading it (plan 71, §6.3).
        return WorldHelper.chunkAt(mod.getWorld(), pos.x, pos.z) != null;
    }

    public boolean isChunkLoaded(BlockPos pos) {
        return isChunkLoaded(new ChunkPos(pos));
    }

    public List<ChunkPos> getLoadedChunks() {
        List<ChunkPos> result = new ArrayList<>(loaded);
        // Only show LOADED chunks.
        result = result.stream()
                .filter(this::isChunkLoaded)
                .distinct()
                .collect(Collectors.toList());
        return result;
    }

    /**
     * Loops through every block in a chunk if it is loaded.
     * If the chunk isn't loaded, it doesn't scan anything.
     *
     * @param chunk       The chunk pos to scan
     * @param onBlockStop Run for every block until it returns true, where it stops scanning.
     * @return whether `onBlockStop` returned true at any point.
     */
    public boolean scanChunk(ChunkPos chunk, Predicate<BlockPos> onBlockStop) {
        if (!isChunkLoaded(chunk)) return false;
        int bottomY = mod.getWorld().getMinBuildHeight();
        int topY = mod.getWorld().getMaxBuildHeight();

        //Debug.logInternal("SCANNED CHUNK " + chunk.toString());
        for (int xx = chunk.getMinBlockX(); xx <= chunk.getMaxBlockX(); ++xx) {
            for (int yy = bottomY; yy <= topY; ++yy) {
                for (int zz = chunk.getMinBlockZ(); zz <= chunk.getMaxBlockZ(); ++zz) {
                    if (onBlockStop.test(new BlockPos(xx, yy, zz))) return true;
                }
            }
        }
        return false;
    }

    public void scanChunk(ChunkPos chunk, Consumer<BlockPos> onBlock) {
        scanChunk(chunk, (block) -> {
            onBlock.accept(block);
            return false;
        });
    }

    public void reset(AltoClef mod) {
        Debug.logInternal("CHUNKS RESET");
        loaded.clear();
    }
}
