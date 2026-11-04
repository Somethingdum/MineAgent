package com.mineagent.engine.pathing.execute;

import com.mineagent.api.entity.AgentPlayer;
import com.mineagent.engine.pathing.astar.*;
import com.mineagent.engine.pathing.cache.PathCaches;
import com.mineagent.engine.pathing.goals.Goal;
import com.mineagent.engine.pathing.moves.CalculationContext;
import com.mineagent.engine.pathing.moves.ChunkLoadedTest;
import com.mineagent.engine.task.TaskContext;
import com.mineagent.api.config.MineAgentConfig;
import com.mineagent.engine.planning.IntentContract;
import com.mineagent.engine.pathing.util.BlockHelper;

import java.util.HashSet;
import java.util.Set;

/**
 * Core state machine for the pathing system. Manages the lifecycle:
 * SEARCH → EXECUTE → (SEGMENT) → SEARCH → ...
 *
 * <p>States:
 * <ul>
 *   <li>IDLE — no pathing task active</li>
 *   <li>SEARCH — calculating a path to the goal</li>
 *   <li>EXECUTE — following the computed path</li>
 *   <li>SEGMENT — path was cut short, re-pathing from current position</li>
 * </ul>
 *
 * <p>When a path fails during execution, the core automatically
 * re-paths from the current position.
 */
public class PathingCore {

    /** The current state of the pathing state machine. */
    public enum State {
        IDLE,
        SEARCH,
        EXECUTE,
        SEGMENT
    }

    /**
     * Maximum number of A* nodes to expand per server tick while
     * searching. Bounding this keeps the server tick responsive —
     * a single tick never spends more than ~1ms on pathfinding, so
     * the world keeps ticking at 20 TPS and the companion never
     * appears to "freeze" while thinking about a path.
     *
     * <p>At ~1µs/node (rough benchmark for this implementation),
     * 2000 nodes ≈ 2ms, leaving plenty of headroom in a 50ms tick.
     */
    private static final int NODES_PER_TICK = 2000;

    /**
     * Hard wall-clock budget (in nanoseconds) for a single search
     * slice. If we hit this before the node budget, we yield early.
     * This protects against pathological cost calculations that would
     * otherwise blow up a single tick.
     */
    private static final long SEARCH_TIME_BUDGET_NS = 3_000_000L; // 3ms

    private final AgentPlayer player;
    private final PathCaches caches;
    private final AStarPathFinder finder;
    private final MineAgentConfig.PathfindingConfig pathConfig;
    private final IntentContract.TerrainPolicy terrainPolicy;

    private State state = State.IDLE;
    private Goal currentGoal;
    private PathExecutor executor;
    private PathCalcResult lastResult;

    /**
     * Whether a sliced search has been initialized for the current
     * goal. Reset whenever a new goal is set or the search completes
     * / is cancelled. When false, {@link #tickSearch()} will call
     * {@link AStarPathFinder#initializeSearch} first; when true it
     * will call {@link AStarPathFinder#continueSearch}.
     */
    private boolean searchInitialized;

    /** One alternate route distinguishes a failed edge from a failed goal. */
    private static final int MAX_EXECUTION_FAILURES = 2;

    /** Cutoff segments are successful progress and use a separate guard. */
    private static final int MAX_PATH_SEGMENTS = 128;

    private int executionFailures;
    private int pathSegments;
    private boolean bridgeDisabledForCurrentGoal;
    private boolean digDisabledForCurrentGoal;
    /**
     * The way out of a hole, for this goal: its task's terrain rules found no path and it stands below the ground at
     * its own spot (the third benchmark run mined down to coal five blocks under, and every walk after that, its
     * hunts, its trees, its hut, was under rules that neither dig nor pillar, and found no way up for two days). Then
     * once more as a player would: digging (never what someone built) and pillaring, as far up as the surface is.
     */
    private IntentContract.TerrainPolicy wayOut;
    /** Off for a task that digs its own way (mining's tunnels) or walks round what it builds (a villager's cell). */
    private boolean wayOutAllowed = true;

    /** Whether a walk that finds no way out of a hole may look again digging and pillaring (on unless a task says). */
    public void allowWayOut(boolean allowed) {
        wayOutAllowed = allowed;
    }
    private String lastFailureDetail;
    private final Set<CalculationContext.ExcludedEdge> excludedEdges = new HashSet<>();

    public PathingCore(AgentPlayer player, PathCaches caches) {
        this(player, caches, MineAgentConfig.PathfindingConfig.DEFAULTS,
                IntentContract.TerrainPolicy.CONSERVATIVE);
    }

    public PathingCore(AgentPlayer player, PathCaches caches,
                       MineAgentConfig.PathfindingConfig pathConfig) {
        this(player, caches, pathConfig, IntentContract.TerrainPolicy.CONSERVATIVE);
    }

    public PathingCore(AgentPlayer player, PathCaches caches,
                       MineAgentConfig.PathfindingConfig pathConfig,
                       IntentContract.TerrainPolicy terrainPolicy) {
        this.player = player;
        this.caches = caches;
        this.pathConfig = pathConfig != null
                ? pathConfig : MineAgentConfig.PathfindingConfig.DEFAULTS;
        this.terrainPolicy = terrainPolicy == null
                ? IntentContract.TerrainPolicy.CONSERVATIVE : terrainPolicy;
        // Configuration must reach the actual search implementation; keeping
        // it only in PlayerNav made max nodes and parkour toggles cosmetic.
        this.finder = new AStarPathFinder(this.pathConfig.maxSearchNodes(),
                this.pathConfig.allowParkour());
    }

    /**
     * Set a new navigation goal and start searching.
     *
     * @param goal the goal to navigate to
     */
    public void setGoal(Goal goal) {
        if (executor != null) executor.stop();
        if (state == State.SEARCH) finder.cancel();
        this.currentGoal = goal;
        this.executionFailures = 0;
        this.pathSegments = 0;
        this.state = State.SEARCH;
        this.executor = null;
        this.lastResult = null;
        this.searchInitialized = false;
        this.bridgeDisabledForCurrentGoal = false;
        this.digDisabledForCurrentGoal = false;
        this.wayOut = null;
        this.lastFailureDetail = null;
        this.excludedEdges.clear();
    }

    /** Hostile mobs within 32 blocks, to keep paths clear of (MA5.10); none when the switch is off. */
    static java.util.List<com.mineagent.engine.pathing.util.MobAvoidance.Danger> dangersAround(
            net.minecraft.server.level.ServerPlayer body) {
        var out = new java.util.ArrayList<com.mineagent.engine.pathing.util.MobAvoidance.Danger>();
        if (com.mineagent.engine.settings.Switches.avoidMobs()) for (var mob : body.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                body.getBoundingBox().inflate(32.0), m -> m.isAlive()
                        && m instanceof net.minecraft.world.entity.monster.Enemy
                        && !(m instanceof net.minecraft.world.entity.NeutralMob neutral && !neutral.isAngry()))) {
            out.add(com.mineagent.engine.pathing.util.MobAvoidance.mob(mob.getBlockX(), mob.getBlockY(), mob.getBlockZ(),
                    mob instanceof net.minecraft.world.entity.monster.Creeper));
            if (out.size() >= 64) break;
        }
        // The deep dark (MA8.7): a sculk sensor hears a step within 8 blocks, and a shrieker calls the warden; kept out of
        // their hearing. Only looked for where the deep dark is, below Y 0.
        if (body.getBlockY() < 0) {
            var at = body.blockPosition();
            int found = 0;
            for (var p : net.minecraft.core.BlockPos.betweenClosed(at.offset(-16, -8, -16), at.offset(16, 8, 16))) {
                var st = body.level().getBlockState(p);
                if (!com.mineagent.engine.survival.HardRules.listening(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(st.getBlock()).toString())) continue;
                out.add(new com.mineagent.engine.pathing.util.MobAvoidance.Danger(p.getX(), p.getY(), p.getZ(),
                        com.mineagent.engine.survival.HardRules.HEARS, 40.0));
                if (++found >= 32) break;
            }
        }
        // Places it keeps dying (MA8.6): kept off like a mob, harder.
        String dim = body.level().dimension().location().toString();
        for (var z : com.mineagent.engine.learning.DangerZones.of(body.getUUID()).zones(dim, body.level().getGameTime())) {
            out.add(new com.mineagent.engine.pathing.util.MobAvoidance.Danger(z.x(), z.y(), z.z(),
                    com.mineagent.engine.learning.DangerZones.RADIUS, com.mineagent.engine.learning.DangerZones.WEIGHT));
        }
        return out;
    }

    /**
     * Cancel the current pathing task.
     */
    public void cancel() {
        if (state == State.SEARCH) {
            finder.cancel();
        }
        if (executor != null) {
            executor.stop();
        }
        state = State.IDLE;
        currentGoal = null;
        executor = null;
        searchInitialized = false;
        bridgeDisabledForCurrentGoal = false;
        digDisabledForCurrentGoal = false;
        wayOut = null;
        lastFailureDetail = null;
        excludedEdges.clear();
    }

    /**
     * Tick the state machine. Called every server tick.
     */
    public void tick() {
        switch (state) {
            case IDLE -> {}
            case SEARCH -> tickSearch();
            case EXECUTE -> tickExecute();
            case SEGMENT -> tickSegment();
        }
    }

    private void tickSearch() {
        if (currentGoal == null) {
            state = State.IDLE;
            return;
        }

        // On the first tick of a SEARCH, initialize the sliced search.
        if (!searchInitialized) {
            var pos = com.mineagent.engine.pathing.util.BlockHelper.bodyCell(TaskContext.serverPlayer(player));
            ChunkLoadedTest chunkTest = caches.loadedChunks()::isChunkLoaded;
            CalculationContext ctx = new CalculationContext(
                    caches.level(), caches.worldView(), chunkTest,
                    pathConfig.allowDigThrough()
                            && policy().allowBreakingObstacles()
                            && !digDisabledForCurrentGoal,
                    pathConfig.allowBridge() && policy().allowBridge()
                            && !bridgeDisabledForCurrentGoal
                            && com.mineagent.engine.act.Placement.hasSupportBlock(
                                    TaskContext.serverPlayer(player)),
                    pathConfig.allowBridge() && policy().allowPillar()
                            && !bridgeDisabledForCurrentGoal
                            && com.mineagent.engine.act.Placement.hasSupportBlock(
                                    TaskContext.serverPlayer(player)),
                    pathConfig.allowParkour() && policy().allowParkour(),
                    pos.getY(), policy().maxUpwardDeviation(), excludedEdges);
            // Break costs from the tools it carries (SMH plan 70, MA5.1).
            ctx.withBreakTimer(com.mineagent.engine.pathing.moves.ToolTimer.of(
                    TaskContext.serverPlayer(player)));
            // Its health and fall attributes, its water bucket, and the mobs about (SMH plan 70, MA5.10).
            var body = TaskContext.serverPlayer(player);
            ctx.withBody(body.getHealth(),
                    body.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.SAFE_FALL_DISTANCE),
                    body.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FALL_DAMAGE_MULTIPLIER),
                    body.getInventory().contains(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WATER_BUCKET)),
                    !body.level().dimensionType().ultraWarm());
            ctx.withDangers(dangersAround(body));

            boolean needsContinue = finder.initializeSearch(
                    pos.getX(), pos.getY(), pos.getZ(),
                    currentGoal, Favoring.NONE, ctx);
            searchInitialized = true;

            // initializeSearch can complete immediately (e.g. already
            // at goal) — in that case grab the result now.
            if (!needsContinue) {
                finishSearch();
                return;
            }
        }

        // Resume the sliced search with this tick's node + time budget.
        long startTime = System.nanoTime();
        int remaining = NODES_PER_TICK;
        boolean stillActive = true;
        while (stillActive && remaining > 0) {
            // Sub-batch so we can also check the time budget.
            int batch = Math.min(remaining, 512);
            long elapsed = System.nanoTime() - startTime;
            if (elapsed >= SEARCH_TIME_BUDGET_NS) {
                // Out of time budget — yield to the tick.
                break;
            }
            stillActive = finder.continueSearch(batch);
            remaining -= batch;
        }

        if (!stillActive) {
            // Search finished this tick — collect the result.
            finishSearch();
        }
        // else: search still in progress; we'll resume next tick. The
        // companion stays in SEARCH state but does NOT freeze — the
        // tick returns quickly and the world keeps simulating.
    }

    /**
     * Collect the result of a completed sliced search and transition
     * to the appropriate next state.
     */
    private void finishSearch() {
        lastResult = finder.searchResult();
        searchInitialized = false;

        if (lastResult != null && lastResult.foundPath()) {
            PathBase path = lastResult.path();
            int requiredSupports = requiredSupportBlocks(path);
            int availableSupports = Math.min(policy().maxPlacedBlocks(),
                    com.mineagent.engine.act.Placement.supportBlockCount(
                            TaskContext.serverPlayer(player)));
            if (!bridgeDisabledForCurrentGoal
                    && requiredSupports > availableSupports) {
                // The A* node key does not include remaining inventory. Reject
                // a bridge that cannot be completed before stepping over void
                // or that exceeds the task's terrain-change budget.
                bridgeDisabledForCurrentGoal = true;
                lastFailureDetail = requiredSupports > policy().maxPlacedBlocks()
                        ? "PATH_EXCEEDS_PLACEMENT_BUDGET" : "INSUFFICIENT_SUPPORT_BLOCKS";
                lastResult = null;
                state = State.SEARCH;
                searchInitialized = false;
                return;
            }
            int requiredBreaks = requiredObstacleBreaks(path);
            if (!digDisabledForCurrentGoal
                    && requiredBreaks > policy().maxBrokenObstacles()) {
                digDisabledForCurrentGoal = true;
                lastFailureDetail = "PATH_EXCEEDS_BREAK_BUDGET";
                lastResult = null;
                state = State.SEARCH;
                searchInitialized = false;
                return;
            }
            // Path movements are created independently of task semantics.
            // Attach the cleanup contract before any movement can place a
            // support, keeping BuildTask output distinct from navigation aid.
            for (var movement : path.movements()) {
                movement.configureSupportCleanup(policy().cleanupMode());
            }
            executor = new PathExecutor(player, path);
            state = State.EXECUTE;
        } else if (wayOut == null && wayOutAllowed && !terrainPolicy.allowPillar() && !terrainPolicy.allowBreakingObstacles()
                && wayOutHere()) {
            var sp = TaskContext.serverPlayer(player);
            wayOut = new IntentContract.TerrainPolicy(true, true, true, false, 16, 24,
                    Math.max(4, surfaceAround(sp) - sp.getBlockY() + 4), terrainPolicy.cleanupMode());
            bridgeDisabledForCurrentGoal = false;
            digDisabledForCurrentGoal = false;
            lastFailureDetail = "NO_PATH_TRYING_THE_WAY_OUT";
            lastResult = null;
            state = State.SEARCH;
            searchInitialized = false;
        } else {
            // No path found — give up
            state = State.IDLE;
        }
    }

    /**
     * Its walks that found no way or stalled for good lately, by companion: when, and where it stood (SMH plan 70: the
     * one way out of anywhere, as AltoClef's progress checkers and Mindcraft's unstuck mode have it, instead of a rule
     * for each place it was caught: a pit, a tree top, walls of its own, a cliff's edge).
     */
    private static final java.util.Map<java.util.UUID, java.util.ArrayDeque<long[]>> FAILED =
            new java.util.concurrent.ConcurrentHashMap<>();
    /** How long a failed walk is remembered, and how near the next must be to count as the same place. */
    static final long FAILED_WITHIN = 600L;
    static final int SAME_PLACE = 4;

    /**
     * This walk found no way (or stalled for good): noted, and true when another did here lately, so this one may dig
     * and pillar ({@code behaviour.wayOutOfHoles}). In the Overworld; never through what someone built.
     */
    private boolean failedHere() {
        var sp = TaskContext.serverPlayer(player);
        if (sp == null || !com.mineagent.engine.settings.Switches.wayOutOfHoles()) return false;
        if (sp.serverLevel().dimension() != net.minecraft.world.level.Level.OVERWORLD) return false;
        long now = sp.serverLevel().getGameTime();
        var fails = FAILED.computeIfAbsent(sp.getUUID(), k -> new java.util.ArrayDeque<>());
        synchronized (fails) {
            while (!fails.isEmpty() && now - fails.peekFirst()[3] > FAILED_WITHIN) fails.pollFirst();
            int before = 0;
            for (long[] f : fails) {
                long dx = f[0] - sp.getBlockX(), dy = f[1] - sp.getBlockY(), dz = f[2] - sp.getBlockZ();
                if (dx * dx + dy * dy + dz * dz <= (long) SAME_PLACE * SAME_PLACE) before++;
            }
            fails.addLast(new long[] {sp.getBlockX(), sp.getBlockY(), sp.getBlockZ(), now});
            while (fails.size() > 16) fails.pollFirst();
            return before >= 1;
        }
    }

    /** No way found under its task's rules: in a hole, up a tree, or the second walk lately to fail here (noted). */
    private boolean wayOutHere() {
        boolean again = failedHere(); // noted whatever the rest says
        return again || inAHole() || upATree();
    }

    /** Its terrain rules for this goal: its task's, or the way out of a hole once those found none. */
    private IntentContract.TerrainPolicy policy() {
        return wayOut != null ? wayOut : terrainPolicy;
    }

    /**
     * In a hole in the ground: two blocks or more below the natural ground round it (a pit, a shaft, a dig beside a
     * hill, a cave), open over its head or with the ground over it, not a roof. Blocks it put down near it (what is
     * left of a night's shelter) don't make it less of a hole: the twelfth benchmark run stood four under its hut's
     * site beside its shelter's leftovers, and never climbed up. The way out never breaks what someone built, and its
     * villager cell and mining's tunnels opt out.
     */
    private boolean inAHole() {
        var sp = TaskContext.serverPlayer(player);
        if (sp == null || !com.mineagent.engine.settings.Switches.wayOutOfHoles()) return false;
        // The Overworld's: under the Nether's roof everywhere is "under the ground", and its walks (to a portal, home
        // through it) are their own business.
        if (sp.serverLevel().dimension() != net.minecraft.world.level.Level.OVERWORLD) return false;
        if (surfaceAround(sp) - sp.getBlockY() < 2) return false;
        // Open over its head, or anything over it: the ground, or what it put there itself (the run on 7ab68c7 roofed
        // its night's shelter in a shaft ten down and sat sealed under that roof half a day; the way out digs round
        // what someone built, never through it).
        return true;
    }

    /**
     * Up in the trees: standing on leaves (the three-world run's second world came back from a death on a spruce's top,
     * ten over the ground, and every log there was "no more spruce logs near here" for two days). The same way out: the
     * leaves broken through, never what someone built.
     */
    private boolean upATree() {
        var sp = TaskContext.serverPlayer(player);
        if (sp == null || !com.mineagent.engine.settings.Switches.wayOutOfHoles()) return false;
        if (sp.serverLevel().dimension() != net.minecraft.world.level.Level.OVERWORLD) return false;
        var under = sp.serverLevel().getBlockState(sp.blockPosition().below());
        return under.is(net.minecraft.tags.BlockTags.LEAVES) && sp.getBlockY() - surfaceAround(sp) >= 2;
    }

    /** The highest ground (its top's air) at its spot and the blocks two round it; a column topped by something built is no ground. */
    private static int surfaceAround(net.minecraft.server.level.ServerPlayer sp) {
        var level = sp.serverLevel();
        var placed = com.mineagent.engine.world.PlacedBlocks.of(level);
        int top = Integer.MIN_VALUE;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int x = sp.getBlockX() + dx, z = sp.getBlockZ() + dz;
                if (!level.hasChunk(x >> 4, z >> 4)) continue;
                int h = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (placed.isPlaced(x, h - 1, z)) continue;
                top = Math.max(top, h);
            }
        }
        return top == Integer.MIN_VALUE ? sp.getBlockY() : top;
    }

    private void tickExecute() {
        if (executor == null) {
            state = State.IDLE;
            return;
        }

        executor.tick();

        if (executor.isFinished()) {
            // Path completed — check if we reached the goal
            if (currentGoal != null) {
                var pos = com.mineagent.engine.pathing.util.BlockHelper.bodyCell(TaskContext.serverPlayer(player));
                if (currentGoal.isInGoal(pos.getX(), pos.getY(), pos.getZ())) {
                    state = State.IDLE;
                    return;
                }
                // If path ended but goal not reached, try re-pathing
                if (lastResult.path() instanceof CutoffPath) {
                    state = State.SEGMENT;
                    return;
                }
            }
            lastFailureDetail = "PATH_ENDED_OUTSIDE_GOAL player="
                    + TaskContext.serverPlayer(player).blockPosition().toShortString();
            state = State.IDLE;
        } else if (executor.isFailed()) {
            // Movement failed — try re-pathing
            PathExecutor.Failure failure = executor.failure();
            executionFailures++;
            if (failure != null) {
                // An unchanged A* search otherwise returns this same edge and
                // repeats the same failed physical action several times.
                lastFailureDetail = failure.describe();
                excludedEdges.add(new CalculationContext.ExcludedEdge(
                        failure.source().getX(), failure.source().getY(), failure.source().getZ(),
                        failure.destination().getX(), failure.destination().getY(),
                        failure.destination().getZ()));
            } else {
                lastFailureDetail = "EXECUTION_FAILED_WITHOUT_DETAIL";
            }
            if (executionFailures < MAX_EXECUTION_FAILURES) {
                state = State.SEARCH;
                executor = null;
                searchInitialized = false;
            } else if (failedHere() && wayOut == null && wayOutAllowed && !terrainPolicy.allowPillar() && !terrainPolicy.allowBreakingObstacles()) {
                // Stalled for good, and not the first time here: the way out, digging and pillaring, from here.
                var sp = TaskContext.serverPlayer(player);
                wayOut = new IntentContract.TerrainPolicy(true, true, true, false, 16, 24,
                        Math.max(4, surfaceAround(sp) - sp.getBlockY() + 4), terrainPolicy.cleanupMode());
                lastFailureDetail = "STALLED_TRYING_THE_WAY_OUT";
                excludedEdges.clear();
                executionFailures = 0;
                state = State.SEARCH;
                executor = null;
                searchInitialized = false;
            } else {
                state = State.IDLE;
            }
        }
    }

    private void tickSegment() {
        // Re-path from current position to the same goal
        pathSegments++;
        if (pathSegments < MAX_PATH_SEGMENTS) {
            state = State.SEARCH;
            executor = null;
            searchInitialized = false;
        } else {
            lastFailureDetail = "PATH_SEGMENT_LIMIT_REACHED segments=" + pathSegments;
            state = State.IDLE;
        }
    }

    private int requiredSupportBlocks(PathBase path) {
        java.util.Set<net.minecraft.core.BlockPos> missing = new java.util.HashSet<>();
        var level = TaskContext.serverPlayer(player).level();
        for (var movement : path.movements()) {
            var support = movement.requiredSupportPosition();
            if (support != null
                    && !com.mineagent.engine.pathing.util.BlockHelper.isClimbable(
                    level.getBlockState(new net.minecraft.core.BlockPos(
                            movement.dstX(), movement.dstY(), movement.dstZ())))
                    && !com.mineagent.engine.pathing.util.BlockHelper.canStandOn(
                    level.getBlockState(support))) {
                missing.add(support.immutable());
            }
        }
        return missing.size();
    }

    private int requiredObstacleBreaks(PathBase path) {
        java.util.Set<net.minecraft.core.BlockPos> obstacles = new java.util.HashSet<>();
        var level = TaskContext.serverPlayer(player).level();
        for (var movement : path.movements()) {
            addBreakObstacle(level, obstacles, new net.minecraft.core.BlockPos(
                    movement.dstX(), movement.dstY(), movement.dstZ()));
            addBreakObstacle(level, obstacles, new net.minecraft.core.BlockPos(
                    movement.dstX(), movement.dstY() + 1, movement.dstZ()));
            if (movement.dstY() > movement.srcY()) {
                addBreakObstacle(level, obstacles, new net.minecraft.core.BlockPos(
                        movement.srcX(), movement.srcY() + 2, movement.srcZ()));
            }
        }
        return obstacles.size();
    }

    private static void addBreakObstacle(net.minecraft.world.level.Level level,
                                         java.util.Set<net.minecraft.core.BlockPos> result,
                                         net.minecraft.core.BlockPos pos) {
        var state = level.getBlockState(pos);
        if (!BlockHelper.isPassable(state) && !BlockHelper.canOpenByHand(state)) {
            result.add(pos.immutable());
        }
    }

    /** Get the current state. */
    public State state() { return state; }

    /** Whether the pathing system is currently active. */
    public boolean isActive() { return state != State.IDLE; }

    /** Get the current goal. */
    public Goal currentGoal() { return currentGoal; }

    /** Get the current path executor (if any). */
    public PathExecutor executor() { return executor; }

    /** Get the last path calculation result. */
    public PathCalcResult lastResult() { return lastResult; }

    /** More specific policy/resource failure when available. */
    public String lastFailureDetail() { return lastFailureDetail; }

    /** Get the caches. */
    public PathCaches caches() { return caches; }
}
