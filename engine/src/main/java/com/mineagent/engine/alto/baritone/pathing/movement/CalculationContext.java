// Ported from Baritone (LGPL-3.0, the licence of this mod; see THIRD_PARTY_NOTICES.md): src/main/java/baritone/pathing/movement/CalculationContext.java
// at commit 11ea66c (branch 1.21.1). SMH: only what AltoClef's checks read (the world, the tools it carries, the extra cost
// and the blocks it must not break); the companion's own walks have their own context (pathing/moves/CalculationContext).
package com.mineagent.engine.alto.baritone.pathing.movement;

import com.mineagent.engine.alto.baritone.Baritone;
import com.mineagent.engine.alto.baritone.api.pathing.movement.ActionCosts;
import com.mineagent.engine.alto.baritone.utils.BlockStateInterface;
import com.mineagent.engine.alto.baritone.utils.ToolSet;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class CalculationContext {

    public final Baritone baritone;
    public final BlockStateInterface bsi;
    public final ToolSet toolSet;
    public final double breakBlockAdditionalCost;

    public CalculationContext(Baritone baritone) {
        this.baritone = baritone;
        this.bsi = new BlockStateInterface(baritone.getPlayerContext());
        this.toolSet = new ToolSet(baritone.getPlayerContext().player());
        this.breakBlockAdditionalCost = baritone.settings().blockBreakAdditionalPenalty.value;
    }

    public BlockState get(int x, int y, int z) {
        return bsi.get0(x, y, z);
    }

    public BlockState get(BlockPos pos) {
        return get(pos.getX(), pos.getY(), pos.getZ());
    }

    public double breakCostMultiplierAt(int x, int y, int z, BlockState current) {
        if (!baritone.settings().allowBreak.value) {
            return ActionCosts.COST_INF;
        }
        if (baritone.getExtraSettings().shouldAvoidBreaking(x, y, z)) {
            return ActionCosts.COST_INF;
        }
        return 1;
    }
}
