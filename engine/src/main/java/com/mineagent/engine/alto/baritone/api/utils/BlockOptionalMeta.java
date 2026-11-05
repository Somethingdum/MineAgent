// Ported from Baritone (LGPL-3.0, the licence of this mod; see THIRD_PARTY_NOTICES.md): src/api/java/baritone/api/utils/BlockOptionalMeta.java
// at commit 11ea66c (branch 1.21.1). SMH: only the block and its states (AltoClef asks for any state of one block).
package com.mineagent.engine.alto.baritone.api.utils;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class BlockOptionalMeta {

    private final Block block;

    public BlockOptionalMeta(Block block) {
        this.block = block;
    }

    public Block getBlock() {
        return block;
    }

    public boolean matches(BlockState state) {
        return state.getBlock() == block;
    }

    public BlockState getAnyBlockState() {
        return block.defaultBlockState();
    }

    @Override
    public String toString() {
        return "BlockOptionalMeta{block=" + block + "}";
    }
}
