/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.sailex.mixins;

import me.sailex.automatone.utils.accessor.ServerChunkManagerAccessor;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ServerChunkCache.class)
public abstract class MixinServerChunkManager implements ServerChunkManagerAccessor {

    @Shadow @Nullable protected abstract ChunkHolder getChunkHolder(long pos);

    @Override
    public @Nullable LevelChunk automatone$getChunkNow(int chunkX, int chunkZ) {
        ChunkHolder chunkHolder = this.getChunkHolder(ChunkPos.asLong(chunkX, chunkZ));
        return chunkHolder == null ? null : chunkHolder.getTickingChunk();
    }
}
