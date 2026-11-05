// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/tasks/movement/SearchChunksExploreTask.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.tasks.movement;

import com.mineagent.engine.alto.AltoClef;
import com.mineagent.engine.alto.Debug;
import com.mineagent.engine.alto.eventbus.EventBus;
import com.mineagent.engine.alto.eventbus.Subscription;
import com.mineagent.engine.alto.eventbus.events.ChunkLoadEvent;
import com.mineagent.engine.alto.tasksystem.Task;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.world.level.ChunkPos;

/**
 * Searches/explores a continuous "blob" of chunks, attempting to load in ALL nearby chunks that are part of this "blob"
 * <p>
 * You must define a function that determines whether a chunk is to be included within this "blob".
 * <p>
 * For instance, if you wish to explore an entire desert, this function will return whether a chunk is a desert chunk.
 */
public abstract class SearchChunksExploreTask extends Task {

    private final Object searcherMutex = new Object();
    private final Set<ChunkPos> alreadyExplored = new HashSet<>();
    private ChunkSearchTask searcher;
    private Subscription<ChunkLoadEvent> _chunkLoadedSubscription;

    // Virtual
    protected ChunkPos getBestChunkOverride(AltoClef mod, List<ChunkPos> chunks) {
        return null;
    }

    @Override
    protected void onStart() {
        // Listen for chunk loading
        _chunkLoadedSubscription = EventBus.subscribe(ChunkLoadEvent.class, evt -> onChunkLoad(evt.chunk.getPos()));

        resetSearch();
    }

    @Override
    protected Task onTick() {
        synchronized (searcherMutex) {
            if (searcher == null) {
                setDebugState("Exploring/Searching for valid chunk");
                // Explore
                return getWanderTask();
            }

            if (searcher.isActive() && searcher.isFinished()) {
                Debug.logWarning("Target object search failed.");
                alreadyExplored.addAll(searcher.getSearchedChunks());
                searcher = null;
            } else if (searcher.finished()) {
                setDebugState("Searching for target object...");
                Debug.logMessage("Search finished.");
                alreadyExplored.addAll(searcher.getSearchedChunks());
                searcher = null;
            }
            //Debug.logMessage("wtf: " + (_searcher == null? "(null)" :_searcher.finished()));
            setDebugState("Searching within chunks...");
            return searcher;
        }
    }

    @Override
    protected void onStop(Task interruptTask) {
        EventBus.unsubscribe(_chunkLoadedSubscription);
    }

    // When we find a valid chunk, start our search there.
    private void onChunkLoad(ChunkPos pos) {
        if (searcher != null) return;
        if (!this.isActive()) return;

        if (isChunkWithinSearchSpace(AltoClef.getInstance(), pos)) {
            synchronized (searcherMutex) {
                if (!alreadyExplored.contains(pos)) {
                    Debug.logMessage("New searcher: " + pos);
                    searcher = new SearchSubTask(pos);
                }
            }
        }

    }

    protected Task getWanderTask() {
        return new TimeoutWanderTask(true);
    }

    protected abstract boolean isChunkWithinSearchSpace(AltoClef mod, ChunkPos pos);

    public boolean failedSearch() {
        return searcher == null;
    }

    public void resetSearch() {
        //Debug.logMessage("Search reset");
        searcher = null;
        alreadyExplored.clear();
        // We want to search the currently loaded chunks too!!!
        for (ChunkPos start : AltoClef.getInstance().getChunkTracker().getLoadedChunks()) {
            onChunkLoad(start);
        }
    }

    class SearchSubTask extends ChunkSearchTask {

        public SearchSubTask(ChunkPos start) {
            super(start);
        }

        @Override
        protected boolean isChunkPartOfSearchSpace(AltoClef mod, ChunkPos pos) {
            return isChunkWithinSearchSpace(mod, pos);
        }

        @Override
        public ChunkPos getBestChunk(AltoClef mod, List<ChunkPos> chunks) {
            ChunkPos override = getBestChunkOverride(mod, chunks);
            if (override != null) return override;
            return super.getBestChunk(mod, chunks);
        }

        @Override
        protected boolean isChunkSearchEqual(ChunkSearchTask other) {
            // Since we're keeping track of "_searcher", we expect the subchild routine to ALWAYS be consistent!
            return other == this;//return other instanceof SearchSubTask;
        }

        @Override
        protected String toDebugString() {
            return "Searching chunks...";
        }
    }

}
