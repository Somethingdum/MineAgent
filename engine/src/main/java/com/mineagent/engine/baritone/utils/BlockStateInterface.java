// Ported from Baritone (LGPL-3.0, the licence of this mod; see THIRD_PARTY_NOTICES.md): src/main/java/baritone/utils/BlockStateInterface.java
// at commit 11ea66c (branch 1.21.1). SMH: the server's own chunks. On the server thread a loaded chunk is read as it is
// (getChunkNow: never loads one); for the path search on Baritone's own threads, the chunks loaded round the companion
// are taken on the server thread when the search is made (Baritone's "copy loaded chunks", which a client does with its
// chunk array). There is no Baritone world cache: ground not loaded is not known, as in Baritone with no cache.
package com.mineagent.engine.baritone.utils;

import com.mineagent.engine.baritone.api.utils.IPlayerContext;
import com.mineagent.engine.baritone.utils.pathing.BetterWorldBorder;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Wraps get for chuck caching capability
 *
 * @author leijurv
 */
public class BlockStateInterface {

    protected final Level world;
    public final BlockPos.MutableBlockPos isPassableBlockPos;
    public final BlockGetter access;
    public final BetterWorldBorder worldBorder;
    /** SMH: the chunks taken for a search off the server thread; null when read live on it. */
    private final Long2ObjectOpenHashMap<LevelChunk> copied;

    private LevelChunk prev = null;

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    public BlockStateInterface(IPlayerContext ctx) {
        this(ctx, false);
    }

    public BlockStateInterface(IPlayerContext ctx, boolean copyLoadedChunks) {
        this.world = ctx.world();
        this.worldBorder = new BetterWorldBorder(world.getWorldBorder());
        ServerLevel level = ctx.world();
        if (!level.getServer().isSameThread()) {
            throw new IllegalStateException("BlockStateInterface must be constructed on the main thread");
        }
        if (copyLoadedChunks) {
            this.copied = new Long2ObjectOpenHashMap<>();
            ChunkPos at = ctx.player().chunkPosition();
            int view = Math.max(2, level.getServer().getPlayerList().getViewDistance()) + 2;
            for (int cx = at.x - view; cx <= at.x + view; cx++) {
                for (int cz = at.z - view; cz <= at.z + view; cz++) {
                    LevelChunk c = level.getChunkSource().getChunkNow(cx, cz);
                    if (c != null) copied.put(ChunkPos.asLong(cx, cz), c);
                }
            }
        } else {
            this.copied = null;
        }
        this.isPassableBlockPos = new BlockPos.MutableBlockPos();
        this.access = new BlockStateInterfaceAccessWrapper(this);
    }

    private LevelChunk chunk(int cx, int cz) {
        if (copied != null) return copied.get(ChunkPos.asLong(cx, cz));
        return ((ServerLevel) world).getChunkSource().getChunkNow(cx, cz);
    }

    public boolean worldContainsLoadedChunk(int blockX, int blockZ) {
        return chunk(blockX >> 4, blockZ >> 4) != null;
    }

    public static Block getBlock(IPlayerContext ctx, BlockPos pos) { // won't be called from the pathing thread because the pathing thread doesn't make a single blockpos pog
        return get(ctx, pos).getBlock();
    }

    public static BlockState get(IPlayerContext ctx, BlockPos pos) {
        return new BlockStateInterface(ctx).get0(pos.getX(), pos.getY(), pos.getZ()); // immense iq
        // can't just do world().get because that doesn't work for out of bounds
        // and toBreak and stuff fails when the movement is instantiated out of load range but it's not able to BlockStateInterface.get what it's going to walk on
    }

    public BlockState get0(BlockPos pos) {
        return get0(pos.getX(), pos.getY(), pos.getZ());
    }

    public BlockState get0(int x, int y, int z) { // Mickey resigned
        y -= world.dimensionType().minY();
        // Invalid vertical position
        if (y < 0 || y >= world.dimensionType().height()) {
            return AIR;
        }

        LevelChunk cached = prev;
        // there's great cache locality in block state lookups
        // generally it's within each movement
        // if it's the same chunk as last time
        // we can just skip the mc.world.getChunk lookup
        // which is a Long2ObjectOpenHashMap.get
        // see issue #113
        if (cached != null && cached.getPos().x == x >> 4 && cached.getPos().z == z >> 4) {
            return getFromChunk(cached, x, y, z);
        }
        LevelChunk chunk = chunk(x >> 4, z >> 4);
        if (chunk != null && !chunk.isEmpty()) {
            prev = chunk;
            return getFromChunk(chunk, x, y, z);
        }
        return AIR;
    }

    public boolean isLoaded(int x, int z) {
        LevelChunk prevChunk = prev;
        if (prevChunk != null && prevChunk.getPos().x == x >> 4 && prevChunk.getPos().z == z >> 4) {
            return true;
        }
        prevChunk = chunk(x >> 4, z >> 4);
        if (prevChunk != null && !prevChunk.isEmpty()) {
            prev = prevChunk;
            return true;
        }
        return false;
    }

    // get the block at x,y,z from this chunk WITHOUT creating a single blockpos object
    public static BlockState getFromChunk(LevelChunk chunk, int x, int y, int z) {
        LevelChunkSection section = chunk.getSections()[y >> 4];
        if (section.hasOnlyAir()) {
            return AIR;
        }
        return section.getBlockState(x & 15, y & 15, z & 15);
    }
}
