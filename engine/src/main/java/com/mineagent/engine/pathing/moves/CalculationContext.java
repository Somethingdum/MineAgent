package com.mineagent.engine.pathing.moves;

import com.mineagent.engine.planning.IntentContract;
import com.mineagent.engine.pathing.cache.LoadedOnlyView;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

/**
 * Cached world state for path calculation. Provides block state lookups
 * with caching to avoid repeated world access during A* search.
 *
 * <p>Uses a {@link LoadedOnlyView} to ensure we only read from loaded
 * chunks and never trigger chunk generation.
 */
public class CalculationContext {

    private final LoadedOnlyView worldView;
    private final ChunkLoadedTest chunkLoadedTest;
    private final Level level;
    private final boolean allowDigThrough;
    private final boolean allowBridge;
    private final boolean allowPillar;
    private final boolean allowParkour;
    private final int startY;
    private final int maxUpwardDeviation;
    private final Set<ExcludedEdge> excludedEdges;

    /**
     * A directed movement edge that failed during execution for the current
     * goal. Keeping this in the calculation context makes a replan materially
     * different: A* cannot immediately select the same proven-bad action.
     */
    public record ExcludedEdge(int sourceX, int sourceY, int sourceZ,
                               int destinationX, int destinationY, int destinationZ) {}

    /** Ticks to break a block (SMH plan 70, MA5.1); {@link ToolTimer} gives the companion's best tool. */
    @FunctionalInterface
    public interface BreakTimer {
        int ticks(BlockState state, int x, int y, int z);
    }

    /** By hand until the companion's tools are set. */
    private BreakTimer breakTimer = this::ticksByHand;
    /** The blocks real players placed in this level (SMH plan 70, MA5.2), looked up once per search. */
    private com.mineagent.engine.world.PlacedBlocks placed;
    private boolean placedLooked;

    public CalculationContext(Level level, LoadedOnlyView worldView, ChunkLoadedTest chunkLoadedTest) {
        this(level, worldView, chunkLoadedTest, false, false, false, false,
                0, Integer.MAX_VALUE, Set.of());
    }

    public CalculationContext(Level level, LoadedOnlyView worldView,
                              ChunkLoadedTest chunkLoadedTest,
                              boolean allowDigThrough, boolean allowBridge) {
        this(level, worldView, chunkLoadedTest, allowDigThrough, allowBridge,
                false, false, 0, Integer.MAX_VALUE, Set.of());
    }

    public CalculationContext(Level level, LoadedOnlyView worldView,
                              ChunkLoadedTest chunkLoadedTest,
                              boolean allowDigThrough, boolean allowBridge,
                              boolean allowPillar, boolean allowParkour,
                              int startY, int maxUpwardDeviation) {
        this(level, worldView, chunkLoadedTest, allowDigThrough, allowBridge,
                allowPillar, allowParkour, startY, maxUpwardDeviation, Set.of());
    }

    public CalculationContext(Level level, LoadedOnlyView worldView,
                              ChunkLoadedTest chunkLoadedTest,
                              boolean allowDigThrough, boolean allowBridge,
                              boolean allowPillar, boolean allowParkour,
                              int startY, int maxUpwardDeviation,
                              Set<ExcludedEdge> excludedEdges) {
        this.level = level;
        this.worldView = worldView;
        this.chunkLoadedTest = chunkLoadedTest;
        this.allowDigThrough = allowDigThrough;
        this.allowBridge = allowBridge;
        this.allowPillar = allowPillar;
        this.allowParkour = allowParkour;
        this.startY = startY;
        this.maxUpwardDeviation = Math.max(0, maxUpwardDeviation);
        this.excludedEdges = excludedEdges == null || excludedEdges.isEmpty()
                ? Set.of() : Set.copyOf(excludedEdges);
    }

    /**
     * Get the block state at the given position. Returns null if the
     * chunk is not loaded.
     */
    public BlockState getBlockState(int x, int y, int z) {
        if (!chunkLoadedTest.isChunkLoaded(x, z)) {
            return null;
        }
        return worldView.getBlockState(new BlockPos(x, y, z));
    }

    /** Check if a chunk is loaded. */
    public boolean isChunkLoaded(int x, int z) {
        return chunkLoadedTest.isChunkLoaded(x, z);
    }

    /** Get the underlying level (for advanced queries). */
    public Level level() {
        return level;
    }

    /** Get the world view. */
    public LoadedOnlyView worldView() {
        return worldView;
    }

    public boolean allowDigThrough() {
        return allowDigThrough;
    }

    public boolean allowBridge() {
        return allowBridge;
    }

    public boolean allowPillar() {
        return allowPillar;
    }

    public boolean allowParkour() {
        return allowParkour;
    }

    /**
     * A task may permit ordinary terrain changes while still forbidding a
     * route that climbs far above its semantic target (for example, chopping
     * a ground-level tree). This is a hard search bound, not a utility weight.
     */
    public boolean isWithinVerticalPolicy(int y) {
        return y <= (long) startY + maxUpwardDeviation;
    }

    /** Use this timer for break costs (the companion's tools); returns this context. */
    public CalculationContext withBreakTimer(BreakTimer timer) {
        if (timer != null) this.breakTimer = timer;
        return this;
    }

    /** Whether a real player placed the block at x, y, z (SMH plan 70, MA5.2). */
    public boolean placedByPlayer(int x, int y, int z) {
        if (!com.mineagent.engine.settings.Switches.neverBreakBuilt()) return false;
        if (!placedLooked) {
            placedLooked = true;
            placed = level == null ? null : com.mineagent.engine.world.PlacedBlocks.of(level);
        }
        return placed != null && placed.isPlaced(x, y, z);
    }

    private String dimId;

    /** Whether x, y, z is at a farm whose player is AFK, where nothing is placed or broken (SMH plan 70, MA5.4). */
    public boolean afkFarm(int x, int y, int z) {
        if (level == null) return false;
        if (dimId == null) dimId = level.dimension().location().toString();
        return com.mineagent.engine.world.AfkFarms.protects(dimId, x, y, z);
    }

    /** Ticks to break the block at x, y, z with the tools this search knows of. */
    public int breakTicks(BlockState state, int x, int y, int z) {
        return breakTimer.ticks(state, x, y, z);
    }

    private int ticksByHand(BlockState state, int x, int y, int z) {
        float hardness = state.getDestroySpeed(level, new BlockPos(x, y, z));
        return com.mineagent.engine.pathing.util.BreakCost.ticks(hardness, 1.0, !state.requiresCorrectToolForDrops());
    }

    // ── The body, and what's about (SMH plan 70, MA5.10) ───────────

    private double health = 20.0;
    private double safeFall = com.mineagent.engine.pathing.util.FallRules.SAFE_FALL;
    private double fallMultiplier = 1.0;
    private boolean waterBucket;
    private boolean waterWorks = true;
    private java.util.List<com.mineagent.engine.pathing.util.MobAvoidance.Danger> dangers = java.util.List.of();

    /**
     * The companion's health and fall attributes, whether it carries a water bucket, and whether water stays put here
     * (it boils away in the Nether); returns this context.
     */
    public CalculationContext withBody(double health, double safeFall, double fallMultiplier,
                                       boolean waterBucket, boolean waterWorks) {
        this.health = health;
        this.safeFall = safeFall;
        this.fallMultiplier = fallMultiplier;
        this.waterBucket = waterBucket;
        this.waterWorks = waterWorks;
        return this;
    }

    /** Hostile mobs to keep clear of; returns this context. */
    public CalculationContext withDangers(java.util.List<com.mineagent.engine.pathing.util.MobAvoidance.Danger> dangers) {
        this.dangers = dangers == null ? java.util.List.of() : java.util.List.copyOf(dangers);
        return this;
    }

    /** The highest drop onto ground that costs no health. */
    public int safeDrop() {
        return com.mineagent.engine.pathing.util.FallRules.safeDrop(safeFall);
    }

    /** Whether a drop this high onto ground can be planned with its water bucket (the MLG chain places it). */
    public boolean bucketDescent(int drop) {
        if (!com.mineagent.engine.settings.Switches.bucketDescents() || !waterBucket || !waterWorks) return false;
        return drop <= com.mineagent.engine.pathing.util.FallRules.maxBucketDrop(health, safeFall, fallMultiplier);
    }

    /** The extra cost of standing at x, y, z near a hostile mob. */
    public double dangerCost(int x, int y, int z) {
        return com.mineagent.engine.pathing.util.MobAvoidance.cost(dangers, x, y, z);
    }

    /** Return true when execution evidence says this exact edge is unusable. */
    public boolean isEdgeExcluded(int sourceX, int sourceY, int sourceZ,
                                  int destinationX, int destinationY, int destinationZ) {
        return excludedEdges.contains(new ExcludedEdge(sourceX, sourceY, sourceZ,
                destinationX, destinationY, destinationZ));
    }
}
