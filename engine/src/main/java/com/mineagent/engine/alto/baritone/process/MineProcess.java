// Ported from Baritone (LGPL-3.0, the licence of this mod; see THIRD_PARTY_NOTICES.md): src/main/java/baritone/process/MineProcess.java
// at commit 11ea66c (branch 1.21.1). SMH: only plausibleToBreak, which AltoClef's checks call; the companion mines through
// AltoClef's own tasks.
package com.mineagent.engine.alto.baritone.process;

import com.mineagent.engine.alto.baritone.pathing.movement.CalculationContext;
import com.mineagent.engine.alto.baritone.pathing.movement.MovementHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import static com.mineagent.engine.alto.baritone.api.pathing.movement.ActionCosts.COST_INF;

public final class MineProcess {

    private MineProcess() {
    }

    public static boolean plausibleToBreak(CalculationContext ctx, BlockPos pos) {
        BlockState state = ctx.bsi.get0(pos);
        if (MovementHelper.getMiningDurationTicks(ctx, pos.getX(), pos.getY(), pos.getZ(), state, true) >= COST_INF) {
            return false;
        }
        if (MovementHelper.avoidBreaking(ctx.bsi, pos.getX(), pos.getY(), pos.getZ(), state)) {
            return false;
        }

        // bedrock above and below makes it implausible, otherwise we're good
        return !(ctx.bsi.get0(pos.above()).getBlock() == Blocks.BEDROCK && ctx.bsi.get0(pos.below()).getBlock() == Blocks.BEDROCK);
    }
}
