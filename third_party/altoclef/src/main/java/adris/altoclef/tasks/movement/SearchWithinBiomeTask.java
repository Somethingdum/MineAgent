package adris.altoclef.tasks.movement;

import adris.altoclef.AltoClef;
import adris.altoclef.multiversion.world.WorldVer;
import adris.altoclef.tasksystem.Task;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;

/**
 * Explores/Loads all chunks of a biome.
 */
public class SearchWithinBiomeTask extends SearchChunksExploreTask {

    private final ResourceKey<Biome> _toSearch;

    public SearchWithinBiomeTask(ResourceKey<Biome> toSearch) {
        _toSearch = toSearch;
    }

    @Override
    protected boolean isChunkWithinSearchSpace(AltoClef mod, ChunkPos pos) {
        return WorldVer.isBiomeAtPos(mod.getWorld(),_toSearch,pos.getWorldPosition().offset(1,1,1));
    }

    @Override
    protected boolean isEqual(Task other) {
        if (other instanceof SearchWithinBiomeTask task) {
            return task._toSearch == _toSearch;
        }
        return false;
    }

    @Override
    protected String toDebugString() {
        return "Searching for+within biome: " + _toSearch;
    }
}
