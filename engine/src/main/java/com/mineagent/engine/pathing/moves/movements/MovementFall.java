package com.mineagent.engine.pathing.moves.movements;

import com.mineagent.api.entity.AgentPlayer;
import com.mineagent.engine.pathing.moves.*;
import com.mineagent.engine.pathing.util.FallRules;
import com.mineagent.engine.task.TaskContext;

/**
 * A deliberate fall to an exact, validated landing cell: unhurt onto ground, into water from high up, or onto ground
 * from higher with a water bucket the MLG chain puts down before it lands (SMH plan 70, MA5.10).
 */
public class MovementFall extends Movement {
    private final Input tickInput = new Input();
    private int fallDistance;

    public MovementFall(int srcX, int srcY, int srcZ, int dstX, int dstY, int dstZ) {
        super(srcX, srcY, srcZ, dstX, dstY, dstZ);
        fallDistance = srcY - dstY;
    }

    /**
     * The landing for a drop from {@code srcY} into column x, z, by the game's fall rules (SMH plan 70, MA5.10): into
     * water from any height up to {@link FallRules#MAX_WATER_DROP}; onto ground no further than it can fall unhurt, or,
     * carrying a water bucket where water stays put, as far as it would survive if the water failed. Null for none.
     */
    public static MovementHelper.Landing landingFor(CalculationContext ctx, int srcY, int x, int z) {
        int minY = Math.max(ctx.level().getMinBuildHeight(), srcY - FallRules.MAX_WATER_DROP);
        MovementHelper.Landing land = MovementHelper.landing(ctx, x, srcY - 1, z, minY);
        if (land == null) return null;
        int drop = srcY - land.y();
        if (land.water()) {
            if (drop <= ctx.safeDrop()) return land;
            // Flowing water breaks a fall too, but a thin layer of it is a closer thing: big drops go into still water.
            var water = ctx.getBlockState(x, land.y(), z);
            return com.mineagent.engine.settings.Switches.waterDrops() && water != null
                    && water.getFluidState().isSource() ? land : null;
        }
        return drop <= ctx.safeDrop() || ctx.bucketDescent(drop) ? land : null;
    }

    @Override
    public double calculateCost(CalculationContext ctx) {
        MovementHelper.Landing land = landingFor(ctx, srcY, dstX, dstZ);
        if (land == null || land.y() != dstY) return cost = Double.POSITIVE_INFINITY;
        fallDistance = srcY - land.y();
        // The column it falls through, from where it steps in at head height down to its landing, must be open.
        for (int y = land.y(); y <= srcY + 1; y++) {
            var state = ctx.getBlockState(dstX, y, dstZ);
            if (state == null || !com.mineagent.engine.pathing.util.BlockHelper.isPassable(state)) {
                return cost = Double.POSITIVE_INFINITY;
            }
        }
        // A drop onto ground past what it falls unhurt is planned only with its water bucket: the MLG chain places
        // the water before it lands and picks it up after. It costs more than walking down, so it is the way only
        // when it saves a long way round.
        double bucket = !land.water() && fallDistance > ctx.safeDrop() ? BUCKET_DESCENT_COST : 0.0;
        return cost = ActionCosts.fallCost(fallDistance) + bucket
                + MovementHelper.getWalkCostModifier(ctx, dstX, land.y(), dstZ);
    }

    /** The extra cost of a planned bucket descent, in blocks of walking. */
    public static final double BUCKET_DESCENT_COST = 12.0;

    @Override
    public Input getTickInput(AgentPlayer player) {
        tickInput.clear();
        var sp = TaskContext.serverPlayer(player);
        for (int y = dstY; y <= srcY + 1; y++) {
            if (!com.mineagent.engine.pathing.util.BlockHelper.isPassable(
                    sp.level().getBlockState(new net.minecraft.core.BlockPos(dstX, y, dstZ)))) {
                // Never mine a fall shaft after stepping off: lower cells may
                // be out of reach and a partial break would leave the body in
                // an uncontrolled fall. The edge times out and replans safely.
                return tickInput;
            }
        }
        if (!ensureClearance(player,
                new net.minecraft.core.BlockPos(dstX, dstY, dstZ),
                new net.minecraft.core.BlockPos(dstX, dstY + 1, dstZ))) return tickInput;
        if (sp.blockPosition().getY() > dstY) {
            moveToward(player, dstX, dstZ, tickInput);
        }
        return tickInput;
    }

    @Override
    public boolean isFinished(AgentPlayer player) {
        var sp = TaskContext.serverPlayer(player);
        return (sp.onGround() || sp.isInWater()) && isAtDestination(player);
    }
}
