// Ported from Baritone (LGPL-3.0, the licence of this mod; see THIRD_PARTY_NOTICES.md): src/main/java/baritone/utils/BlockStateInterface.java
// at commit 11ea66c (branch 1.21.1). SMH: the server's own level, read as it is (no chunk cache copies, no world data).
package com.mineagent.engine.alto.baritone.utils;

import com.mineagent.engine.alto.baritone.api.utils.IPlayerContext;
import com.mineagent.engine.alto.baritone.utils.pathing.BetterWorldBorder;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class BlockStateInterface {

    private final Level world;
    public final BlockPos.MutableBlockPos isPassableBlockPos;
    public final BetterWorldBorder worldBorder;

    public BlockStateInterface(IPlayerContext ctx) {
        this(ctx.world());
    }

    public BlockStateInterface(Level world) {
        this.world = world;
        this.worldBorder = new BetterWorldBorder(world.getWorldBorder());
        this.isPassableBlockPos = new BlockPos.MutableBlockPos();
    }

    public boolean worldContainsLoadedChunk(int blockX, int blockZ) {
        return world.hasChunk(blockX >> 4, blockZ >> 4);
    }

    public static BlockState get(IPlayerContext ctx, BlockPos pos) {
        return new BlockStateInterface(ctx).get0(pos.getX(), pos.getY(), pos.getZ());
    }

    public BlockState get0(BlockPos pos) {
        return get0(pos.getX(), pos.getY(), pos.getZ());
    }

    public BlockState get0(int x, int y, int z) {
        // An unloaded chunk reads as air in Baritone's cache; the server is asked only for loaded ones.
        if (!worldContainsLoadedChunk(x, z)) return Blocks.AIR.defaultBlockState();
        return world.getBlockState(isPassableBlockPos.set(x, y, z));
    }

    public boolean isLoaded(int x, int z) {
        return worldContainsLoadedChunk(x, z);
    }
}
